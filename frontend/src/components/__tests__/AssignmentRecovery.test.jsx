import { act, cleanup, fireEvent, render, screen } from '@testing-library/react';
import { Link, MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import AssignmentPlayer from '../../pages/AssignmentPlayer';
import { assignmentApi } from '../../services/assignmentApi';

vi.mock('../../services/assignmentApi', () => ({ assignmentApi: {
    getSession: vi.fn(), saveDraft: vi.fn(), getSessionTiming: vi.fn(), submitAssignment: vi.fn(),
} }));
vi.mock('../../services/api', () => ({ default: { post: vi.fn() } }));
vi.mock('../../context/AuthContext', () => ({ useAuth: () => ({ user: { activeCourseCode: 'en' }, vocabularyMap: {} }) }));
const NOW = Date.parse('2026-10-24T16:00:00Z');
const items = ['one', 'two'].map(exerciseId => ({ exerciseId, type: 'TRANSLATION', content: { question: exerciseId, answerLang: 'en' } }));
let server;
beforeEach(() => {
    vi.resetAllMocks(); vi.useFakeTimers(); vi.setSystemTime(NOW);
    server = { sessionData: { exercises: items, classroomId: 'class', timing: {
        deadline: new Date(NOW + 60000).toISOString(), serverNow: new Date(NOW).toISOString(), allowLateSubmission: false,
    } }, assignmentDetails: { hasFeedback: false, allowRetries: false }, revision: 0, draft: null };
    assignmentApi.getSession.mockImplementation(async () => ({ data: structuredClone(server) }));
    assignmentApi.getSessionTiming.mockImplementation(async () => ({ data: { ...server.sessionData.timing, serverNow: new Date(Date.now()).toISOString() } }));
    assignmentApi.saveDraft.mockImplementation(async (_, draft) => {
        server.draft = structuredClone(draft); server.revision++;
        return { data: { revision: server.revision, timing: server.sessionData.timing } };
    });
    assignmentApi.submitAssignment.mockResolvedValue({});
});
afterEach(() => { cleanup(); vi.useRealTimers(); });
async function open() {
    await act(async () => render(<MemoryRouter initialEntries={['/assignment/session/attempt/play']}><Routes>
        <Route path="/assignment/session/:sessionId/play" element={<AssignmentPlayer />} />
    </Routes></MemoryRouter>));
}
const type = value => fireEvent.change(screen.getByRole('textbox', { name: 'Válaszod' }), { target: { value } });
const advance = async ms => act(async () => vi.advanceTimersByTimeAsync(ms));

it('recovers saved answers and current position from a direct URL without router state', async () => {
    server.revision = 4;
    server.draft = { queue: ['one','two'], answers: [{ exerciseId:'one',answer:'apple' }], currentIndex:1, currentAnswer:'pear', reviewing:false };
    await open();
    expect(screen.getByRole('textbox', { name: 'Válaszod' })).toHaveValue('pear');
    expect(screen.getByText('two')).toBeInTheDocument();
    expect(screen.getByRole('status', { name: 'Válaszok mentési állapota' })).toHaveTextContent('mentve');
    expect(assignmentApi.saveDraft).not.toHaveBeenCalled();
});

it('restores an acknowledged partial answer after unmount/remount like F5 or reopening', async () => {
    await open(); type('apple'); await advance(600);
    expect(server.draft.currentAnswer).toBe('apple');
    cleanup(); await open();
    expect(screen.getByRole('textbox', { name: 'Válaszod' })).toHaveValue('apple');
    expect(assignmentApi.saveDraft).toHaveBeenCalledTimes(1);
});

it('serializes overlapping autosaves and uses the acknowledged revision for the newest text', async () => {
    let finishFirst;
    assignmentApi.saveDraft.mockImplementationOnce(() => new Promise(resolve => { finishFirst = resolve; }));
    await open(); type('a'); await advance(600);
    type('apple'); await advance(600);
    expect(assignmentApi.saveDraft).toHaveBeenCalledTimes(1);
    await act(async () => finishFirst({ data: { revision: 1, timing: server.sessionData.timing } }));
    await advance(1);
    expect(assignmentApi.saveDraft).toHaveBeenCalledTimes(2);
    expect(assignmentApi.saveDraft.mock.calls[1][1]).toMatchObject({ revision: 1, currentAnswer: 'apple' });
});

it('keeps unsaved text, warns on closing, and saves it when the connection returns', async () => {
    assignmentApi.saveDraft.mockRejectedValueOnce(new Error('offline'));
    await open(); type('offline answer'); await advance(600);
    expect(screen.getByRole('alert')).toHaveTextContent('még nincsenek a szerveren');
    expect(screen.getByRole('textbox', { name: 'Válaszod' })).toHaveValue('offline answer');
    const leaving = new Event('beforeunload', { cancelable: true });
    window.dispatchEvent(leaving); expect(leaving.defaultPrevented).toBe(true);
    await act(async () => window.dispatchEvent(new Event('online')));
    expect(server.draft.currentAnswer).toBe('offline answer');
    const savedLeaving = new Event('beforeunload', { cancelable: true });
    window.dispatchEvent(savedLeaving); expect(savedLeaving.defaultPrevented).toBe(false);
});

it('blocks stale-tab overwrites and requires deliberate loading of the server state', async () => {
    assignmentApi.saveDraft.mockRejectedValue({ response: { status: 409 } });
    await open(); type('stale answer'); await advance(600);
    expect(screen.getByRole('alert')).toHaveTextContent('Másik ablak');
    expect(screen.getByRole('textbox', { name: 'Válaszod' })).toBeDisabled();
    await advance(5000); expect(assignmentApi.saveDraft).toHaveBeenCalledTimes(1);
    server.revision = 5; server.draft = { queue:['one','two'], answers:[], currentIndex:0,currentAnswer:'new server answer',reviewing:false };
    await act(async () => fireEvent.click(screen.getByRole('button', { name: 'Szerveren mentett állapot betöltése' })));
    expect(screen.getByRole('textbox', { name: 'Válaszod' })).toHaveValue('new server answer');
});

it('shows completion when a delayed autosave reports server-side deadline finalization', async () => {
    assignmentApi.saveDraft.mockResolvedValue({ data: { revision: 0, timing: { ...server.sessionData.timing, finished:true } } });
    await open(); type('unconfirmed late edit'); await advance(600);
    expect(screen.getByRole('heading', { name: 'Sikeres Beküldés!' })).toHaveFocus();
    expect(assignmentApi.submitAssignment).not.toHaveBeenCalled();
});

it('opens an already finished attempt as a summary and never submits or saves again', async () => {
    server.sessionData.timing.finished = true;
    server.sessionData.exercises = [];
    await open(); await advance(10000);
    expect(screen.getByRole('heading', { name: 'Sikeres Beküldés!' })).toBeInTheDocument();
    expect(assignmentApi.submitAssignment).not.toHaveBeenCalled(); expect(assignmentApi.saveDraft).not.toHaveBeenCalled();
});

it('never carries an old attempt draft into another session while its recovery request is pending', async () => {
    assignmentApi.getSession.mockImplementation(id => id === 'other' ? new Promise(() => {}) : Promise.resolve({data:structuredClone(server)}));
    await act(async () => render(<MemoryRouter initialEntries={['/assignment/session/attempt/play']}>
        <Link to="/assignment/session/other/play">Másik próbálkozás</Link>
        <Routes><Route path="/assignment/session/:sessionId/play" element={<AssignmentPlayer />} /></Routes>
    </MemoryRouter>));
    type('answer belonging to the first attempt');
    await act(async () => fireEvent.click(screen.getByRole('link',{name:'Másik próbálkozás'})));
    await advance(3000);
    expect(screen.queryByRole('textbox',{name:'Válaszod'})).not.toBeInTheDocument();
    expect(assignmentApi.saveDraft).not.toHaveBeenCalled();
});
