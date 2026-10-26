import { act, cleanup, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import AchievementsSection from './AchievementSection';
import api from '../../services/api';
vi.mock('../../services/api', () => ({ default: { get: vi.fn(), post: vi.fn() } }));
let observers;
const award = (id, name) => ({ achievementId: id, userAchievementId: id, name, description: 'Leírás', isUnlocked: true, unread: true });
beforeEach(() => {
    vi.clearAllMocks(); observers = [];
    Object.defineProperty(document, 'hidden', { configurable: true, value: false });
    vi.stubGlobal('IntersectionObserver', class {
        constructor(callback) { this.callback = callback; this.cards = []; observers.push(this); }
        observe(card) { this.cards.push(card); } disconnect() {}
    });
    api.get.mockResolvedValue({ data: [award('a', 'Első'), award('b', 'Második')] }); api.post.mockResolvedValue({});
});
afterEach(() => { cleanup(); vi.unstubAllGlobals(); });
it('automatically marks only cards actually viewed in the profile as read', async () => {
    render(<AchievementsSection />); await screen.findByRole('heading', { name: 'Első Új' });
    expect(screen.queryByRole('button')).not.toBeInTheDocument(); expect(api.post).not.toHaveBeenCalled();
    const observer = await waitFor(() => { expect(observers.length).toBeGreaterThan(0); return observers.at(-1); });
    await act(async () => observer.callback([{ target: observer.cards[0], isIntersecting: true }]));
    expect(api.post).toHaveBeenCalledWith('/achievements/acknowledge', { awardIds: ['a'], read: true });
    expect(screen.getByRole('heading', { name: 'Első', exact: true })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Második Új' })).toBeInTheDocument();
});
it('does not mark hidden-tab content as read', async () => {
    Object.defineProperty(document, 'hidden', { configurable: true, value: true });
    render(<AchievementsSection />); await screen.findByRole('heading', { name: 'Első Új' });
    const observer = await waitFor(() => { expect(observers.length).toBeGreaterThan(0); return observers.at(-1); });
    await act(async () => observer.callback([{ target: observer.cards[0], isIntersecting: true }]));
    expect(api.post).not.toHaveBeenCalled();
});
it('keeps the gallery and new marker when acknowledgement fails', async () => {
    api.post.mockRejectedValue(new Error('offline')); render(<AchievementsSection />);
    await screen.findByRole('heading', { name: 'Első Új' }); const observer = await waitFor(() => { expect(observers.length).toBeGreaterThan(0); return observers.at(-1); });
    await act(async () => observer.callback([{ target: observer.cards[0], isIntersecting: true }]));
    expect(screen.getByRole('heading', { name: 'Első Új' })).toBeInTheDocument();
});
it('applies concurrent acknowledgements even when the first result refreshes the observer', async () => {
    const resolvers = []; api.post.mockImplementation(() => new Promise(resolve => resolvers.push(resolve)));
    render(<AchievementsSection />); await screen.findByRole('heading', { name: 'Első Új' }); const observer = await waitFor(() => { expect(observers.length).toBeGreaterThan(0); return observers.at(-1); });
    act(() => observer.callback([{ target: observer.cards[0], isIntersecting: true }]));
    act(() => observer.callback([{ target: observer.cards[1], isIntersecting: true }]));
    await act(async () => resolvers[0]({})); await act(async () => resolvers[1]({}));
    await waitFor(() => expect(screen.queryAllByText('Új')).toHaveLength(0));
});
