import { cleanup, fireEvent, render, screen, within } from '@testing-library/react';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import Dashboard from '../../pages/Dashboard';
import NavigationBar from '../NavigationBar';
import api from '../../services/api';

vi.mock('../../services/api', () => ({ default: { get: vi.fn() } }));
vi.mock('../Leaderboard', () => ({ default: () => null }));
vi.mock('../TutorialTour', () => ({ default: () => null }));
vi.mock('../ThemeToggle', () => ({ default: () => null }));
vi.mock('../../context/AuthContext', () => ({ useAuth: () => ({
    user: { userId: 'student', name: 'Teszt', xp: 120, activeCourseCode: 'en' },
    changeLanguage: vi.fn(), logout: vi.fn(),
}) }));
vi.mock('../../context/NotificationContext', () => ({ useNotifications: () => ({ notifications: {} }) }));

const lessons = [
    { lessonId: 'intro', title: 'Bemutatkozás', description: 'Köszönés és bemutatkozás.', topicName: 'Alapok', completed: true, completedDifficulty: 'EASY' },
    { lessonId: 'food', title: 'Ételek', description: 'Ételek neveinek gyakorlása.', topicName: 'Alapok', completed: false },
];
beforeEach(() => {
    vi.clearAllMocks();
    api.get.mockResolvedValue({ data: lessons });
});
afterEach(cleanup);

function openDashboard() {
    render(<MemoryRouter initialEntries={['/dashboard']}><Routes>
        <Route path="/dashboard" element={<Dashboard />} />
        <Route path="/lesson/:id" element={<LessonDestination />} />
    </Routes></MemoryRouter>);
}

function LessonDestination() {
    const { search } = useLocation();
    return <><h1>Leckelejátszó</h1><p>{search}</p></>;
}

describe('dashboard screen reader context', () => {
    it('provides one shared, initially collapsed explanation instead of repeating the rules on cards', async () => {
        api.get.mockResolvedValue({ data: lessons.map(lesson => ({ ...lesson, difficulty: 'MEDIUM',
            availableDifficulties: ['EASY', 'MEDIUM', 'HARD'], completedDifficulties: lesson.completed ? ['EASY'] : [] })) });
        openDashboard();
        await screen.findByRole('button', { name: 'Folytatás: Ételek' });
        const explanation = screen.getByText('Hogyan működnek a csillagok?').closest('details');
        expect(explanation).not.toHaveAttribute('open');
        expect(screen.getAllByText(/Az első teljesítésért szintenként jár XP/)).toHaveLength(1);
        expect(screen.queryByRole('combobox')).not.toBeInTheDocument();
    });
    it('recommends a missing star with an explicit replay level instead of repeating the profile level', async () => {
        api.get.mockResolvedValue({ data: [{ ...lessons[0], difficulty: 'MEDIUM',
            availableDifficulties: ['EASY', 'MEDIUM', 'HARD'], completedDifficulties: ['EASY', 'MEDIUM'] }] });
        openDashboard();
        const action = await screen.findByRole('button', { name: 'Következő csillag: Bemutatkozás, Profi szint' });
        expect(screen.getByRole('heading', { name: 'Már csak egy csillag hiányzik ebből a leckéből!' })).toBeInTheDocument();
        fireEvent.click(action);
        expect(screen.getByText('?difficulty=HARD')).toBeInTheDocument();
    });

    it('prioritises a new lesson over mastery replays', async () => {
        api.get.mockResolvedValue({ data: lessons.map(lesson => ({ ...lesson, difficulty: 'MEDIUM',
            availableDifficulties: ['EASY', 'MEDIUM', 'HARD'], completedDifficulties: lesson.completed ? ['EASY'] : [] })) });
        openDashboard();
        expect(await screen.findByRole('button', { name: 'Folytatás: Ételek' })).toBeInTheDocument();
        expect(screen.queryByRole('button', { name: /^Következő csillag/ })).not.toBeInTheDocument();
    });

    it('celebrates a fully mastered course and offers replay even when the profile level is unavailable', async () => {
        api.get.mockResolvedValue({ data: [{ ...lessons[0], difficulty: 'HARD', mastered: true,
            availableDifficulties: ['EASY', 'MEDIUM'], completedDifficulties: ['EASY', 'MEDIUM'] }] });
        openDashboard();
        const action = await screen.findByRole('button', { name: 'Szabad gyakorlás: Bemutatkozás, Kezdő szint' });
        expect(screen.getByRole('heading', { name: 'Minden csillagot megszereztél!' })).toBeInTheDocument();
        fireEvent.click(action);
        expect(screen.getByText('?difficulty=EASY')).toBeInTheDocument();
    });
    it('includes each lesson title and description when its action receives focus', async () => {
        openDashboard();
        const start = await screen.findByRole('button', { name: 'Indítás: Ételek' });
        expect(start).toHaveAccessibleDescription('Ételek neveinek gyakorlása.');
        const repeat = screen.getByRole('button', { name: 'Ismétlés: Bemutatkozás' });
        expect(repeat).toHaveAccessibleDescription('Köszönés és bemutatkozás.');
        repeat.focus();
        expect(repeat).toHaveFocus();
        const completed = screen.getByRole('article', { name: 'Bemutatkozás – Teljesítve' });
        expect(within(completed).getByText('✅')).toHaveAttribute('aria-hidden', 'true');
        fireEvent.click(start);
        expect(screen.getByRole('heading', { name: 'Leckelejátszó' })).toBeInTheDocument();
    });

    it('provides a heading hierarchy and named progress indicators without decorative emoji in names', async () => {
        openDashboard();
        await screen.findByRole('button', { name: 'Indítás: Ételek' });
        expect(screen.getByRole('heading', { level: 1 })).toHaveAccessibleName('Üdv újra, Teszt!');
        expect(screen.getByRole('heading', { level: 2, name: 'Tanulási Útvonal' })).toBeInTheDocument();
        expect(screen.getByRole('heading', { level: 3, name: 'Alapok' })).toBeInTheDocument();
        expect(screen.getByRole('heading', { level: 4, name: 'Ételek' })).toBeInTheDocument();
        expect(screen.getByRole('heading', { name: 'Teljesítményed' })).toHaveAccessibleName('Teljesítményed');
        expect(screen.getByRole('progressbar', { name: 'Elvégzett leckék aránya' })).toHaveAttribute('aria-valuenow', '50');
        expect(screen.getByRole('progressbar', { name: 'Előrehaladás a következő szintig' })).toHaveAttribute('aria-valuenow', '20');
    });

    it.each([
        ['first', [lessons[1]], 'Első lecke indítása: Ételek'],
        ['continue', lessons, 'Folytatás: Ételek'],
        ['complete', [lessons[0]], 'Tudás felfrissítése: Bemutatkozás'],
    ])('names the %s hero action with its destination lesson', async (_, data, name) => {
        api.get.mockResolvedValue({ data });
        openDashboard();
        expect(await screen.findByRole('button', { name })).toBeInTheDocument();
    });

    it('keeps navigation names free of decorative icons and exposes the selected language', async () => {
        render(<MemoryRouter><NavigationBar /></MemoryRouter>);
        expect(screen.getByRole('button', { name: 'Navigációs menü' })).toBeInTheDocument();
        expect(screen.getByRole('link', { name: 'Dashboard' })).toHaveAccessibleName('Dashboard');
        expect(screen.getByRole('link', { name: 'Osztálytermek' })).toHaveAccessibleName('Osztálytermek');
        fireEvent.click(screen.getByRole('button', { name: 'Tanult nyelv: Angol' }));
        expect(await screen.findByRole('button', { name: 'Angol' })).toHaveAttribute('aria-pressed', 'true');
        expect(screen.getByRole('button', { name: 'Spanyol' })).toHaveAttribute('aria-pressed', 'false');
    });
});
