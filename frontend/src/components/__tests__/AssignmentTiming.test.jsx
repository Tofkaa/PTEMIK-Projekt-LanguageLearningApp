import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import AssignmentTimingModal from '../AssignmentTimingModal';
import AssignmentPlayer from '../../pages/AssignmentPlayer';
import { assignmentApi } from '../../services/assignmentApi';
import { formatToLocalInput, formatToUtcForServer } from '../../utils/dateUtils';

vi.mock('../../services/assignmentApi', () => ({ assignmentApi: {
    getAccommodations: vi.fn(), updateAccommodation: vi.fn(), getSessionTiming: vi.fn(), submitAssignment: vi.fn(), getSession: vi.fn(), saveDraft: vi.fn(),
} }));
vi.mock('../../services/api', () => ({ default: { post: vi.fn() } }));
vi.mock('../../context/AuthContext', () => ({ useAuth: () => ({ user: { activeCourseCode: 'en' }, vocabularyMap: {} }) }));

const assignment = { assignmentId: 'task', title: 'Angol vizsga', timeLimitMinutes: 10, availableUntil: '2026-10-18T15:00:00Z' };
const student = { userId: 'student', studentName: 'Diák', studentEmail: 'student@example.test', extraMinutes: 5,
    unlimited: false, availableUntilOverride: '2026-10-18T16:30:00.000Z', hasOngoingSession: true };
const NOW = Date.parse('2026-10-18T12:00:00Z');
beforeEach(() => {
    vi.resetAllMocks();
    assignmentApi.getAccommodations.mockResolvedValue({ data: [student] });
    assignmentApi.updateAccommodation.mockResolvedValue({});
    assignmentApi.submitAssignment.mockResolvedValue({});
    assignmentApi.saveDraft.mockImplementation(async (_, draft) => ({ data: { revision: draft.revision + 1, timing: { finished: false } } }));
});
afterEach(() => { cleanup(); vi.useRealTimers(); });

async function selectStudent() {
    render(<AssignmentTimingModal assignment={assignment} onHide={vi.fn()} />);
    fireEvent.change(await screen.findByRole('combobox', { name: 'Tanuló' }), { target: { value: 'student' } });
}
async function openPlayer(deadline, serverNow = new Date(NOW).toISOString()) {
    const timing = { deadline, serverNow, allowLateSubmission: false, finished: false, timeLimitMinutes: 10 };
    assignmentApi.getSessionTiming.mockImplementation(async () => ({ data: { ...timing, serverNow: new Date(Date.now()).toISOString() } }));
    assignmentApi.getSession.mockResolvedValue({ data: { sessionData: { startedAt: new Date(NOW).toISOString(), timing,
        exercises: [{ exerciseId: 'word', type: 'TRANSLATION', content: { question: 'Fordítsd le: alma', answerLang: 'en' } }] },
        assignmentDetails: { timeLimitMinutes: 1, test: true, hasFeedback: false, allowRetries: false, allowLateSubmission: false }, revision: 0 } });
    await act(async () => {
    render(<MemoryRouter initialEntries={[{ pathname: '/assignment/session/session/play', state: {
        sessionData: { sessionId: 'session', startedAt: new Date(NOW).toISOString(), timing, exercises: [
            { exerciseId: 'word', type: 'TRANSLATION', content: { question: 'Fordítsd le: alma', answerLang: 'en' } },
        ] }, assignmentDetails: { timeLimitMinutes: 1, test: true, hasFeedback: false, allowRetries: false, allowLateSubmission: false },
    } }]}><Routes><Route path="/assignment/session/:sessionId/play" element={<AssignmentPlayer />} /></Routes></MemoryRouter>);
    });
}

describe('teacher timing editor', () => {
    it('loads local deadline and sends separate UTC deadline and extra minutes', async () => {
        await selectStudent();
        const deadline = screen.getByLabelText('Egyéni beadási határidő (helyi idő)');
        expect(deadline).toHaveValue(formatToLocalInput(student.availableUntilOverride));
        expect(formatToUtcForServer(deadline.value)).toBe(student.availableUntilOverride);
        fireEvent.change(screen.getByRole('spinbutton', { name: 'Többletidő (perc)' }), { target: { value: '20' } });
        fireEvent.click(screen.getByRole('button', { name: 'Időbeállítások mentése' }));
        await waitFor(() => expect(screen.getByRole('status')).toHaveTextContent('mentése sikerült'));
        expect(assignmentApi.updateAccommodation).toHaveBeenCalledWith('task', 'student', {
            extraMinutes: 20, unlimited: false, availableUntilOverride: student.availableUntilOverride,
        });
    });
    it('saves exemption without silently clearing the independent deadline', async () => {
        await selectStudent();
        fireEvent.click(screen.getByRole('checkbox', { name: 'Időkorlát-mentesség' }));
        expect(screen.getByRole('spinbutton', { name: 'Többletidő (perc)' })).toBeDisabled();
        fireEvent.click(screen.getByRole('button', { name: 'Időbeállítások mentése' }));
        await waitFor(() => expect(assignmentApi.updateAccommodation).toHaveBeenCalledWith('task', 'student', {
            extraMinutes: 0, unlimited: true, availableUntilOverride: student.availableUntilOverride,
        }));
    });
    it('keeps rejected edits visible for correction', async () => {
        assignmentApi.updateAccommodation.mockRejectedValue({ response: { data: { message: 'Elkezdett próbálkozás nem rövidíthető.' } } });
        await selectStudent();
        fireEvent.click(screen.getByRole('button', { name: 'Időbeállítások mentése' }));
        await waitFor(() => expect(screen.getByText('Elkezdett próbálkozás nem rövidíthető.')).toBeInTheDocument());
        expect(screen.getByRole('spinbutton', { name: 'Többletidő (perc)' })).toHaveValue(5);
    });
});

describe('authoritative assignment countdown', () => {
    it('does not announce a teacher change for equivalent deadline timestamp formats', async () => {
        vi.useFakeTimers(); vi.setSystemTime(NOW);
        await openPlayer('2026-10-18T12:10:00Z');
        assignmentApi.getSessionTiming.mockImplementation(async () => ({ data: {
            deadline: '2026-10-18T12:10:00+00:00', serverNow: new Date(Date.now()).toISOString(),
            allowLateSubmission: false, finished: false,
        } }));
        await act(async () => vi.advanceTimersByTimeAsync(5000));
        expect(screen.queryByText('A tanár frissítette az időbeállításaidat.')).not.toBeInTheDocument();
        expect(assignmentApi.submitAssignment).not.toHaveBeenCalled();
    });
    it('uses the session deadline instead of stale common settings', async () => {
        vi.useFakeTimers(); vi.setSystemTime(NOW);
        await openPlayer(new Date(NOW + 600000).toISOString());
        await act(async () => vi.advanceTimersByTimeAsync(1000));
        expect(screen.getByRole('timer')).toHaveTextContent('09:59');
    });
    it('rechecks at expiry and applies a just-granted extension without losing the answer', async () => {
        vi.useFakeTimers(); vi.setSystemTime(NOW);
        await openPlayer(new Date(NOW + 2000).toISOString());
        assignmentApi.getSessionTiming.mockImplementation(async () => ({ data: {
            deadline: new Date(NOW + 600000).toISOString(), serverNow: new Date(Date.now()).toISOString(), allowLateSubmission: false,
        } }));
        fireEvent.change(screen.getByRole('textbox', { name: 'Válaszod' }), { target: { value: 'apple' } });
        await act(async () => vi.advanceTimersByTimeAsync(3000));
        expect(assignmentApi.submitAssignment).not.toHaveBeenCalled();
        expect(screen.getByRole('textbox', { name: 'Válaszod' })).toHaveValue('apple');
        expect(screen.getByText('A tanár frissítette az időbeállításaidat.')).toBeInTheDocument();
    });
    it('includes the current typed answer in automatic submission', async () => {
        vi.useFakeTimers(); vi.setSystemTime(NOW);
        await openPlayer(new Date(NOW + 2000).toISOString());
        fireEvent.change(screen.getByRole('textbox', { name: 'Válaszod' }), { target: { value: 'apple' } });
        await act(async () => vi.advanceTimersByTimeAsync(3000));
        expect(assignmentApi.submitAssignment).toHaveBeenCalledWith('session', [{ exerciseId: 'word', answer: 'apple', isRetry: undefined, retried: false }], expect.any(Number));
        expect(screen.getByRole('heading', { name: 'Sikeres Beküldés!' })).toHaveFocus();
    });
    it('retains answers when server expiry verification is unavailable', async () => {
        vi.useFakeTimers(); vi.setSystemTime(NOW);
        await openPlayer(new Date(NOW + 2000).toISOString());
        assignmentApi.getSessionTiming.mockRejectedValue(new Error('offline'));
        fireEvent.change(screen.getByRole('textbox', { name: 'Válaszod' }), { target: { value: 'apple' } });
        await act(async () => vi.advanceTimersByTimeAsync(3000));
        expect(assignmentApi.submitAssignment).not.toHaveBeenCalled();
        expect(screen.getByRole('textbox', { name: 'Válaszod' })).toHaveValue('apple');
        expect(screen.getByRole('alert')).toHaveTextContent('lejárat szerveroldali ellenőrzése nem sikerült');
    });
    it('does not restore a common timer when the authoritative deadline is unlimited', async () => {
        vi.useFakeTimers(); vi.setSystemTime(NOW);
        await openPlayer(null);
        await act(async () => vi.advanceTimersByTimeAsync(61000));
        expect(screen.queryByRole('timer')).not.toBeInTheDocument();
        expect(assignmentApi.submitAssignment).not.toHaveBeenCalled();
    });
});
