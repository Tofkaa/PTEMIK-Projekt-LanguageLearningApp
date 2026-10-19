import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import TeacherClassrooms from '../TeacherClassrooms';
import AssignmentSubmissions from '../../pages/AssignmentSubmissions';
import Settings from '../../pages/Settings';
import { classroomApi } from '../../services/classroomApi';
import { assignmentApi } from '../../services/assignmentApi';

vi.mock('../../services/classroomApi', () => ({ classroomApi: { getTeacherClassrooms: vi.fn() } }));
vi.mock('../../services/assignmentApi', () => ({ assignmentApi: { getAssignmentSessions: vi.fn() } }));
vi.mock('../../services/api', () => ({ default: { put: vi.fn() } }));
vi.mock('../../context/AuthContext', () => ({ useAuth: () => ({
    user: { userId: 'teacher', name: 'Teszt', preferredDifficulty: 'DYNAMIC', authProvider: 'LOCAL' },
    setUser: vi.fn(), logout: vi.fn(),
}) }));
vi.mock('../../context/NotificationContext', () => ({ useNotifications: () => ({ notifications: {} }) }));

beforeEach(() => {
    vi.clearAllMocks();
    sessionStorage.clear();
    classroomApi.getTeacherClassrooms.mockResolvedValue({ data: [] });
});
afterEach(() => { cleanup(); sessionStorage.clear(); });

describe('named modal controls', () => {
    it.each(['close button', 'Escape'])('names the classroom dialog and restores focus after %s', async method => {
        render(<MemoryRouter><TeacherClassrooms /></MemoryRouter>);
        const origin = await screen.findByRole('button', { name: '+ Új osztály létrehozása' });
        origin.focus();
        fireEvent.click(origin);
        const dialog = await screen.findByRole('dialog', { name: 'Új osztály létrehozása' });
        expect(within(dialog).getByRole('textbox', { name: 'Osztály neve' })).toBeRequired();
        expect(within(dialog).getByRole('textbox', { name: 'Leírás' })).toBeInTheDocument();
        expect(within(dialog).getByRole('combobox', { name: 'Tanított Nyelv (Kurzus)' })).toHaveValue('en');
        const close = within(dialog).getByRole('button', { name: 'Ablak bezárása' });
        close.focus();
        if (method === 'Escape') fireEvent.keyDown(close, { key: 'Escape', keyCode: 27 });
        else fireEvent.click(close);
        await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
        expect(origin).toHaveFocus();
    });

    it('makes the classroom entry a link that retains its destination', async () => {
        classroomApi.getTeacherClassrooms.mockResolvedValue({ data: [{ classroomId: 'room-1', name: 'Angol csoport', courseCode: 'en' }] });
        render(<MemoryRouter><TeacherClassrooms /></MemoryRouter>);
        expect(await screen.findByRole('link', { name: 'Angol csoport' })).toHaveAttribute('href', '/classrooms/room-1');
    });

    it('names grading fields and closes without submitting a grade', async () => {
        assignmentApi.getAssignmentSessions.mockResolvedValue({ data: [{
            sessionId: 'session-1', studentName: 'Teszt Diák', teacherScore: null, finalScore: 80,
            graded: false, answers: [], finishedAt: '2026-10-18T12:00:00Z',
        }] });
        render(<MemoryRouter initialEntries={['/assignment/task-1/submissions']}><Routes>
            <Route path="/assignment/:id/submissions" element={<AssignmentSubmissions />} />
        </Routes></MemoryRouter>);
        const origin = await screen.findByRole('button', { name: /^Értékelés: Teszt Diák/ });
        origin.focus();
        fireEvent.click(origin);
        const dialog = await screen.findByRole('dialog', { name: 'Dolgozat Értékelése: Teszt Diák' });
        expect(within(dialog).getByRole('spinbutton', { name: 'Végső Pontszám (%)' })).toHaveValue(80);
        expect(within(dialog).getByRole('textbox', { name: 'Tanári Megjegyzés (Opcionális)' })).toBeInTheDocument();
        fireEvent.click(within(dialog).getByRole('button', { name: 'Értékelés bezárása' }));
        await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
        expect(origin).toHaveFocus();
    });

    it('exposes the adaptive difficulty through its visible label', () => {
        sessionStorage.setItem('settingsActiveTab', 'learning');
        render(<MemoryRouter><Settings /></MemoryRouter>);
        const difficulty = screen.getByRole('combobox', { name: 'Célzott Nehézség' });
        expect(difficulty).toHaveValue('DYNAMIC');
        expect(within(difficulty).getAllByRole('option')).toHaveLength(4);
        fireEvent.change(difficulty, { target: { value: 'EASY' } });
        expect(difficulty).toHaveValue('EASY');
    });
});
