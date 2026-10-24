import { cleanup, render, screen, within } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import VocabularyHub from '../../pages/VocabularyHub';
import AssignmentSubmissions from '../../pages/AssignmentSubmissions';
import api from '../../services/api';
import { assignmentApi } from '../../services/assignmentApi';

vi.mock('../../services/api', () => ({ default: { get: vi.fn() } }));
vi.mock('../../services/assignmentApi', () => ({ assignmentApi: { getAssignmentSessions: vi.fn() } }));
const auth = vi.hoisted(() => ({ user: { userId: 'table-user', activeCourseCode: 'en' } }));
vi.mock('../../context/AuthContext', () => ({ useAuth: () => auth }));
vi.mock('../../context/NotificationContext', () => ({ useNotifications: () => ({ notifications: {} }) }));

beforeEach(() => vi.clearAllMocks());
afterEach(cleanup);

describe('keyboard-accessible data tables', () => {
    it.each(['en', 'de'])('names the vocabulary scroll region and marks %s words separately from Hungarian translations', async lang => {
        auth.user.activeCourseCode = lang;
        api.get.mockResolvedValue({ data: [{ word: 'Test', translation: 'próba', srsLevel: 1 }] });
        render(<MemoryRouter><VocabularyHub /></MemoryRouter>);
        const region = await screen.findByRole('region', { name: 'Szószedet, görgethető táblázat' });
        region.focus();
        expect(region).toHaveFocus();
        expect(region).toHaveAttribute('tabindex', '0');
        const table = within(region).getByRole('table', { name: 'Szószedet, jelentések és ismétlési időpontok' });
        for (const header of within(table).getAllByRole('columnheader')) expect(header).toHaveAttribute('scope', 'col');
        expect(within(table).getByRole('rowheader', { name: 'Test' })).toHaveAttribute('lang', lang);
        expect(within(table).getByRole('cell', { name: 'próba' })).toHaveAttribute('lang', 'hu');
    });

    it('keeps a submission table focusable even when its row already contains a grading button', async () => {
        assignmentApi.getAssignmentSessions.mockResolvedValue({ data: [{
            sessionId: 'submission', studentName: 'Diák', studentEmail: 'student@example.test',
            finalScore: 80, teacherScore: null, graded: false, answers: [], finishedAt: '2026-10-21T12:00:00Z',
        }] });
        render(<MemoryRouter initialEntries={['/assignment/task/submissions']}><Routes>
            <Route path="/assignment/:id/submissions" element={<AssignmentSubmissions />} />
        </Routes></MemoryRouter>);
        const region = await screen.findByRole('region', { name: 'Beadások, görgethető táblázat' });
        region.focus();
        expect(region).toHaveFocus();
        expect(region).toHaveAttribute('tabindex', '0');
        const table = within(region).getByRole('table', { name: 'Beadások, pontszámok és értékelési műveletek' });
        expect(within(table).getAllByRole('columnheader')).toHaveLength(6);
        for (const header of within(table).getAllByRole('columnheader')) expect(header).toHaveAttribute('scope', 'col');
        expect(within(table).getByRole('rowheader', { name: 'Diák student@example.test' })).toHaveAttribute('scope', 'row');
        expect(within(table).getByRole('button', { name: /^Értékelés: Diák/ })).toBeEnabled();
    });
});
