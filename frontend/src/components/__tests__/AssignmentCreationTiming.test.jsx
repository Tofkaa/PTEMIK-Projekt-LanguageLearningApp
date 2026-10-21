import { act, cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import TeacherClassroomDetail from '../TeacherClassroomDetail';
import { assignmentApi } from '../../services/assignmentApi';
import { classroomApi } from '../../services/classroomApi';
import { lessonApi } from '../../services/lessonApi';
import { formatToUtcForServer } from '../../utils/dateUtils';

vi.mock('../../services/assignmentApi', () => ({ assignmentApi: {
    getClassroomAssignments: vi.fn(), getClassroomStatistics: vi.fn(), getAdvancedClassroomStatistics: vi.fn(), createAssignment: vi.fn(),
} }));
vi.mock('../../services/classroomApi', () => ({ classroomApi: { getMembers: vi.fn(), moderateMember: vi.fn(), kickMember: vi.fn() } }));
vi.mock('../../services/lessonApi', () => ({ lessonApi: { getAllLessonsForChallenge: vi.fn(), getLessonExercises: vi.fn() } }));
vi.mock('../../context/AuthContext', () => ({ useAuth: () => ({ user: { userId: 'teacher' } }) }));
vi.mock('../../context/NotificationContext', () => ({ useNotifications: () => ({ notifications: {} }) }));
afterEach(cleanup);
beforeEach(() => {
    vi.clearAllMocks();
    classroomApi.moderateMember.mockReset();
    classroomApi.getMembers.mockImplementation((id, status) => Promise.resolve({ data: status === 'ACCEPTED'
        ? [{ memberId: 'member', userId: 'student', studentName: 'Anna', email: 'anna@example.test' }] : [] }));
    assignmentApi.getClassroomAssignments.mockResolvedValue({ data: [] });
    assignmentApi.getClassroomStatistics.mockResolvedValue({ data: null });
    assignmentApi.getAdvancedClassroomStatistics.mockResolvedValue({ data: null });
    assignmentApi.createAssignment.mockResolvedValue({});
    lessonApi.getAllLessonsForChallenge.mockResolvedValue({ data: [{ lessonId: 'lesson', title: 'Gyümölcsök', orderIndex: 1 }] });
    lessonApi.getLessonExercises.mockResolvedValue({ data: [{ exerciseId: 'question', difficulty: 'MEDIUM', content: { question: 'Banán?' } }] });
});

async function prepare() {
    render(<MemoryRouter initialEntries={['/classrooms/room']}><Routes><Route path="/classrooms/:id" element={<TeacherClassroomDetail />} /></Routes></MemoryRouter>);
    fireEvent.click(await screen.findByRole('button', { name: '+ Új Feladat / Teszt Kiírása' }));
    const dialog = await screen.findByRole('dialog', { name: 'Feladat / Tananyag Összeállítása' });
    fireEvent.change(within(dialog).getByRole('textbox', { name: 'Cím', exact: true }), { target: { value: 'Időpróba' } });
    fireEvent.change(within(dialog).getByRole('spinbutton', { name: 'Idő (perc)', exact: true }), { target: { value: '10' } });
    fireEvent.click(within(dialog).getByRole('button', { name: 'Idő növelése 1 perccel' }));
    expect(within(dialog).getByRole('spinbutton', { name: 'Idő (perc)', exact: true })).toHaveValue(11);
    fireEvent.click(within(dialog).getByRole('button', { name: 'Idő csökkentése 1 perccel' }));
    expect(within(dialog).getByRole('spinbutton', { name: 'Idő (perc)', exact: true })).toHaveValue(10);
    fireEvent.change(within(dialog).getByRole('combobox'), { target: { value: 'lesson' } });
    fireEvent.click(await within(dialog).findByRole('checkbox', { name: /Banán\?/ }));
    fireEvent.click(within(dialog).getByText('Egyéni időbeállítások (0 tanuló)'));
    fireEvent.click(within(dialog).getByRole('checkbox', { name: 'Egyéni időbeállítás: Anna (anna@example.test)' }));
    return dialog;
}

async function openPendingMember(isProcessed = () => false) {
    const member = { memberId: 'member', userId: 'student', studentName: 'Anna', userTag: '42' };
    classroomApi.getMembers.mockImplementation((id, status) => Promise.resolve({ data: status === 'PENDING' && !isProcessed() ? [member] : [] }));
    render(<MemoryRouter initialEntries={['/classrooms/room']}><Routes><Route path="/classrooms/:id" element={<TeacherClassroomDetail />} /></Routes></MemoryRouter>);
    await screen.findByRole('button', { name: '+ Új Feladat / Teszt Kiírása' });
    fireEvent.click(screen.getByRole('tab', { name: 'Tagság kezelése' }));
    return screen.findByRole('button', { name: 'Csatlakozás elfogadása: Anna#42' });
}

it.each([0, 4])('names statistics and handles progress when the assignment count is %s', async totalAssignments => {
    assignmentApi.getClassroomStatistics.mockResolvedValue({ data: {
        classAverage: 75, totalAssignments, studentProgress: [{ studentName: 'Anna', studentEmail: 'anna@example.test',
            completedCount: 0, averageScore: 75, lastActivity: 'Nincs adat' }],
    } });
    assignmentApi.getAdvancedClassroomStatistics.mockResolvedValue({ data: {
        assignmentHeaders: [{ assignmentId: 'missing', title: 'Első vizsga' }, { assignmentId: 'zero', title: 'Második vizsga' }],
        heatmapData: [{ studentName: 'Anna', scores: { zero: 0 } }], topMistakes: [],
    } });
    render(<MemoryRouter initialEntries={['/classrooms/room']}><Routes><Route path="/classrooms/:id" element={<TeacherClassroomDetail />} /></Routes></MemoryRouter>);
    await screen.findByRole('button', { name: '+ Új Feladat / Teszt Kiírása' });
    fireEvent.click(screen.getByRole('tab', { name: 'Statisztika & Haladás' }));
    expect(await screen.findByRole('heading', { name: 'Osztálystatisztikák', level: 2 })).toBeInTheDocument();
    const region = screen.getByRole('region', { name: 'Diákok aggregált teljesítménye, görgethető táblázat' });
    expect(region).toHaveAttribute('tabindex', '0');
    expect(within(region).getByRole('table', { name: 'Diákok aggregált teljesítménye' })).toBeInTheDocument();
    expect(within(region).getByRole('rowheader', { name: /Anna/ })).toHaveAttribute('scope', 'row');
    const progress = within(region).getByRole('progressbar', { name: 'Anna haladása' });
    expect(progress).toHaveAttribute('aria-valuenow', '0');
    expect(progress).toHaveAttribute('aria-valuetext', `Összesen ${totalAssignments} feladatból 0 befejezett`);
    fireEvent.click(screen.getByRole('tab', { name: 'Nehézségi Hőtérkép' }));
    const matrix = screen.getByRole('region', { name: 'Teljesítménymátrix, görgethető táblázat' });
    expect(matrix).toHaveAttribute('tabindex', '0');
    expect(within(matrix).getByRole('rowheader', { name: 'Anna' })).toHaveAttribute('scope', 'row');
    expect(within(matrix).getByRole('cell', { name: 'Nincs beadva' })).toBeInTheDocument();
    expect(within(matrix).getByRole('cell', { name: '0%' })).toBeInTheDocument();
});

it('marks mistake question languages separately and distinguishes repeated questions', async () => {
    const exercise = { exerciseId: 'ex', type: 'MULTIPLE_CHOICE', content: {
        question: 'Which animal can fly?', questionLang: 'en', answerLang: 'en', options: ['Bird'] } };
    assignmentApi.getClassroomStatistics.mockResolvedValue({ data: { classAverage: 0, totalAssignments: 1, studentProgress: [] } });
    assignmentApi.getAdvancedClassroomStatistics.mockResolvedValue({ data: { assignmentHeaders: [], heatmapData: [],
        topMistakes: [1, 2].map(() => ({ question: 'Which animal can fly?', assignmentTitle: 'Vizsga', mistakeCount: 1, exercise })),
    } });
    render(<MemoryRouter initialEntries={['/classrooms/room']}><Routes><Route path="/classrooms/:id" element={<TeacherClassroomDetail />} /></Routes></MemoryRouter>);
    await screen.findByRole('button', { name: '+ Új Feladat / Teszt Kiírása' });
    fireEvent.click(screen.getByRole('tab', { name: 'Statisztika & Haladás' }));
    fireEvent.click(await screen.findByRole('tab', { name: 'Gyakori Hibák' }));
    for (const question of screen.getAllByText('Which animal can fly?')) expect(question).toHaveAttribute('lang', 'en');
    const preview = screen.getByRole('button', { name: 'előnézet: Vizsga, 1. hibacsoport' });
    expect(screen.getByRole('button', { name: 'előnézet: Vizsga, 2. hibacsoport' })).toBeInTheDocument();
    fireEvent.click(preview);
    expect(preview).toHaveAttribute('aria-expanded', 'true');
    expect(document.getElementById(preview.getAttribute('aria-controls'))).toBeInTheDocument();
});

it('names membership actions and moves focus to the member list after approval', async () => {
    let processed = false;
    let finish;
    classroomApi.moderateMember.mockImplementation(() => new Promise(resolve => { finish = () => { processed = true; resolve({}); }; }));
    const approve = await openPendingMember(() => processed);
    approve.focus(); fireEvent.click(approve); fireEvent.click(approve);
    expect(classroomApi.moderateMember).toHaveBeenCalledExactlyOnceWith('room', 'student', true);
    expect(approve).toHaveFocus();
    await act(async () => finish());
    await waitFor(() => expect(screen.getByRole('heading', { name: 'Tagság kezelése' })).toHaveFocus());
    expect(screen.getByRole('status')).toHaveTextContent('Az osztálytagság módosítása sikerült.');
});

it('retains the membership card and focused action when moderation fails', async () => {
    classroomApi.moderateMember.mockRejectedValue({ response: { data: { message: 'A csatlakozási kérelem nem módosítható.' } } });
    const approve = await openPendingMember();
    approve.focus(); fireEvent.click(approve);
    await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent('A csatlakozási kérelem nem módosítható.'));
    expect(approve).toHaveFocus();
    expect(approve).toHaveAttribute('aria-disabled', 'false');
});

it('publishes assignment and individual timing in one request, then clears the draft', async () => {
    const dialog = await prepare();
    const timing = within(dialog).getByRole('group', { name: 'Anna időbeállításai' });
    fireEvent.change(within(timing).getByRole('spinbutton', { name: 'Többletidő (perc)' }), { target: { value: '20' } });
    fireEvent.change(within(timing).getByLabelText('Egyéni beadási határidő (helyi idő)'), { target: { value: '2026-11-18T20:00' } });
    fireEvent.click(within(timing).getByRole('checkbox', { name: 'Időkorlát-mentesség' }));
    fireEvent.click(within(dialog).getByRole('button', { name: 'Dolgozat Kiosztása' }));
    await waitFor(() => expect(assignmentApi.createAssignment).toHaveBeenCalledWith('room', expect.objectContaining({
        timeLimitMinutes: 10, accommodations: [{ userId: 'student', extraMinutes: 0, unlimited: true,
            availableUntilOverride: formatToUtcForServer('2026-11-18T20:00') }],
    })));
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    fireEvent.click(screen.getByRole('button', { name: '+ Új Feladat / Teszt Kiírása' }));
    expect(await screen.findByText('Egyéni időbeállítások (0 tanuló)')).toBeInTheDocument();
});

it('retains individual fields and announces a rejected publication for correction', async () => {
    assignmentApi.createAssignment.mockRejectedValue({ response: { data: { message: 'A tanuló már nem osztálytag.' } } });
    const dialog = await prepare();
    fireEvent.change(within(dialog).getByRole('spinbutton', { name: 'Többletidő (perc)' }), { target: { value: '15' } });
    fireEvent.click(within(dialog).getByRole('button', { name: 'Dolgozat Kiosztása' }));
    await waitFor(() => expect(within(dialog).getByRole('alert')).toHaveTextContent('A tanuló már nem osztálytag.'));
    expect(within(dialog).getByRole('spinbutton', { name: 'Többletidő (perc)' })).toHaveValue(15);
    expect(within(dialog).getByRole('textbox', { name: 'Cím', exact: true })).toHaveValue('Időpróba');
});
