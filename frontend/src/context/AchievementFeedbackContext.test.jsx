import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, useNavigate } from 'react-router-dom';
import { beforeEach, expect, it, vi } from 'vitest';
import { AchievementFeedbackProvider } from './AchievementFeedbackContext';
import EarnedAchievements from '../components/EarnedAchievements';

const state = vi.hoisted(() => ({ user: { userId: 'student' }, autoRefreshEnabled: true }));
vi.mock('./AuthContext', () => ({ useAuth: () => ({ user: state.user }) }));
vi.mock('./NotificationContext', () => ({ useNotifications: () => ({ autoRefreshEnabled: state.autoRefreshEnabled }) }));
vi.mock('../services/api', () => ({ default: { get: vi.fn(), post: vi.fn() } }));
import api from '../services/api';
const awards = [{ userAchievementId: 'a1', name: 'Első lecke', description: 'Tanultál' }];
function Navigation() { const navigate = useNavigate(); return <button onClick={() => navigate('/dashboard')}>Kilépés</button>; }
function Tree({ path = '/dashboard', inline = false }) { return <MemoryRouter initialEntries={[path]}>
    <AchievementFeedbackProvider><Navigation />{inline && <EarnedAchievements awards={awards} />}</AchievementFeedbackProvider>
</MemoryRouter>; }
beforeEach(() => { vi.clearAllMocks(); state.user = { userId: 'student' }; state.autoRefreshEnabled = true; api.get.mockResolvedValue({ data: awards }); api.post.mockResolvedValue({}); });
it('groups awards and deduplicates polling against already presented IDs', async () => {
    render(<Tree />); await screen.findByRole('region', { name: 'Új kitüntetések' });
    expect(screen.getByRole('status')).toHaveTextContent('Új kitüntetés: Első lecke');
    await waitFor(() => expect(api.post).toHaveBeenCalledWith('/achievements/acknowledge', { awardIds: ['a1'], read: false }));
    fireEvent.click(screen.getByRole('button', { name: 'Értesítés bezárása' }));
    await act(async () => window.dispatchEvent(new Event('achievement-refresh')));
    expect(screen.queryByRole('region', { name: 'Új kitüntetések' })).not.toBeInTheDocument();
});
it('does not show or announce during an exam, then delivers after exit', async () => {
    render(<Tree path="/assignment/session/123/play" />);
    await waitFor(() => expect(api.get).toHaveBeenCalled());
    expect(screen.getByRole('status')).toBeEmptyDOMElement(); expect(api.post).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole('button', { name: 'Kilépés' }));
    await screen.findByRole('region', { name: 'Új kitüntetések' });
});
it('renders own lesson awards inline without replaying a global card', async () => {
    render(<Tree path="/lesson/123" inline />);
    await screen.findByRole('region', { name: 'Most szerzett kitüntetések' });
    fireEvent.click(screen.getByRole('button', { name: 'Kilépés' }));
    expect(screen.queryByRole('region', { name: 'Új kitüntetések' })).not.toBeInTheDocument();
});
it('discards an old account response after switching accounts', async () => {
    let resolve; api.get.mockImplementationOnce(() => new Promise(done => { resolve = done; }));
    const view = render(<Tree />); state.user = { userId: 'other' }; api.get.mockResolvedValue({ data: [] }); view.rerender(<Tree />);
    await act(async () => resolve({ data: awards }));
    expect(screen.queryByRole('region', { name: 'Új kitüntetések' })).not.toBeInTheDocument();
});
it('respects paused auto refresh while allowing an explicit refresh', async () => {
    state.autoRefreshEnabled = false; render(<Tree />); expect(api.get).not.toHaveBeenCalled();
    await act(async () => window.dispatchEvent(new Event('achievement-refresh')));
    await screen.findByRole('region', { name: 'Új kitüntetések' });
});
it('shows multiple awards in one card', async () => {
    api.get.mockResolvedValue({ data: [...awards, { ...awards[0], userAchievementId: 'a2', name: 'Barátok' }] }); render(<Tree />);
    await screen.findByRole('region', { name: 'Új kitüntetések' });
    expect(screen.getByRole('status')).toHaveTextContent('2 új kitüntetést szereztél!'); expect(screen.getAllByRole('region')).toHaveLength(1);
});

it('keeps pending feedback silent while a modal dialog is open', async () => {
    const dialog = document.createElement('div'); dialog.setAttribute('role', 'dialog'); dialog.setAttribute('aria-modal', 'true'); document.body.appendChild(dialog);
    render(<Tree />); await waitFor(() => expect(api.get).toHaveBeenCalled());
    expect(screen.queryByRole('region', { name: 'Új kitüntetések' })).not.toBeInTheDocument(); expect(api.post).not.toHaveBeenCalled();
    await act(async () => dialog.remove()); await screen.findByRole('region', { name: 'Új kitüntetések' });
});
it('does not acknowledge or spend a pending card in a hidden tab', async () => {
    Object.defineProperty(document, 'hidden', { configurable: true, value: true });
    render(<Tree />); await waitFor(() => expect(api.get).toHaveBeenCalled());
    expect(api.post).not.toHaveBeenCalled(); expect(screen.getByRole('status')).toBeEmptyDOMElement();
    Object.defineProperty(document, 'hidden', { configurable: true, value: false }); fireEvent(document, new Event('visibilitychange'));
    await screen.findByRole('region', { name: 'Új kitüntetések' });
});
