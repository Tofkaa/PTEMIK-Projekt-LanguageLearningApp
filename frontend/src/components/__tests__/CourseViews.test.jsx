import { act, cleanup, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import Dashboard from '../../pages/Dashboard';
import VocabularyHub from '../../pages/VocabularyHub';
import api from '../../services/api';

const auth = vi.hoisted(() => ({ user: { userId: 'one', activeCourseCode: 'en' } }));
vi.mock('../../context/AuthContext', () => ({ useAuth: () => auth }));
vi.mock('../../context/NotificationContext', () => ({ useNotifications: () => ({ notifications: {} }) }));
vi.mock('../Leaderboard', () => ({ default: () => null }));
vi.mock('../TutorialTour', () => ({ default: () => null }));
vi.mock('../../services/api', () => ({ default: { get: vi.fn() } }));
let requests;
beforeEach(() => {
    auth.user = { userId: 'one', activeCourseCode: 'en' }; requests = [];
    api.get.mockImplementation((url, config) => new Promise((resolve, reject) => requests.push({ url, ...config, resolve, reject })));
});
afterEach(cleanup);

it('reloads the hub on language change and never relabels old English words as German', async () => {
    const view = render(<MemoryRouter><VocabularyHub /></MemoryRouter>);
    await waitFor(() => expect(requests).toHaveLength(1));
    auth.user = { ...auth.user, activeCourseCode: 'de' };
    view.rerender(<MemoryRouter><VocabularyHub /></MemoryRouter>);
    await act(() => requests[1].resolve({ data: [{ word: 'Apfel', translation: 'alma', srsLevel: 1 }] }));
    await act(() => requests[0].resolve({ data: [{ word: 'apple', translation: 'alma', srsLevel: 1 }] }));
    expect(screen.getByRole('rowheader', { name: 'Apfel' })).toHaveAttribute('lang', 'de');
    expect(screen.queryByRole('rowheader', { name: 'apple' })).not.toBeInTheDocument();
});

it('clears dashboard lessons when replacing the account even if both accounts use English', async () => {
    const view = render(<MemoryRouter><Dashboard /></MemoryRouter>);
    await act(() => requests[0].resolve({ data: [{ lessonId: 'private', title: 'Korábbi fiók leckéje', difficulty: 'EASY' }] }));
    expect(screen.getByRole('heading', { name: 'Korábbi fiók leckéje' })).toBeInTheDocument();
    auth.user = { userId: 'two', activeCourseCode: 'en' };
    view.rerender(<MemoryRouter><Dashboard /></MemoryRouter>);
    expect(screen.queryByRole('heading', { name: 'Korábbi fiók leckéje' })).not.toBeInTheDocument();
    await waitFor(() => expect(requests).toHaveLength(2));
    await act(() => requests[1].resolve({ data: [{ lessonId: 'new', title: 'Másik fiók leckéje', difficulty: 'EASY' }] }));
    expect(screen.getByRole('heading', { name: 'Másik fiók leckéje' })).toBeInTheDocument();
});
