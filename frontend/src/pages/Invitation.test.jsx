import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import Invitation from './Invitation';
import api from '../services/api';
import { postLoginDestination, readInvitation, rememberInvitation } from '../utils/invitationIntent';
const feed = vi.hoisted(() => ({ notifications: undefined, autoRefreshEnabled: true, refreshNotifications: vi.fn() }));
const auth = vi.hoisted(() => ({ user: null, logout: vi.fn(), updateProfile: vi.fn() }));
vi.mock('../context/AuthContext', () => ({ useAuth: () => auth }));
vi.mock('../context/NotificationContext', () => ({ useNotifications: () => feed }));
vi.mock('../services/api', () => ({ default: { get: vi.fn(), post: vi.fn() } }));
const token = 'a'.repeat(43);
const ready = { kind: 'friend', title: 'Alice#1234', description: 'Meghívó', state: 'READY', classroomId: null };
const student = { userId: 'student', name: 'Teszt', role: 'STUDENT', verified: true };
function Tree({ path = `/invite/friend#token=${token}` }) { return <MemoryRouter initialEntries={[path]}><Routes>
    <Route path="/invite/:kind" element={<Invitation />} /><Route path="/login" element={<h1>Bejelentkezés</h1>} />
    <Route path="/register" element={<h1>Regisztráció</h1>} /><Route path="/dashboard" element={<h1>Dashboard</h1>} />
    <Route path="/friends" element={<h1>Közösség</h1>} />
</Routes></MemoryRouter>; }
beforeEach(() => {
    vi.clearAllMocks(); sessionStorage.clear(); localStorage.clear(); auth.user = { ...student };
    feed.notifications = undefined; feed.autoRefreshEnabled = true;
    api.post.mockImplementation(url => Promise.resolve({ data: url.endsWith('/confirm') ? { ...ready, state: 'PENDING' } : ready }));
});
afterEach(cleanup);
it('does not load private previews or create relationships for a guest and preserves the login destination', async () => {
    auth.user = null; render(<Tree />);
    expect(screen.queryByText('Alice#1234')).not.toBeInTheDocument(); expect(api.post).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole('link', { name: 'Bejelentkezés' }));
    expect(screen.getByRole('heading', { name: 'Bejelentkezés' })).toBeInTheDocument();
    expect(postLoginDestination()).toBe(`/invite/friend#token=${token}`);
});
it('only previews on open and requires an explicit confirmation before sending a pending request', async () => {
    render(<Tree />); await screen.findByRole('heading', { name: 'Alice#1234' });
    expect(api.post.mock.calls.map(([url]) => url)).toEqual(['/invitations/preview']);
    fireEvent.click(screen.getByRole('button', { name: 'Barátkérelem küldése' }));
    await screen.findByRole('status', { name: 'Meghívó: állapot' });
    expect(api.post).toHaveBeenCalledWith('/invitations/confirm', { kind: 'friend', token });
    expect(readInvitation()).toBeNull(); expect(screen.queryByRole('button', { name: 'Barátkérelem küldése' })).not.toBeInTheDocument();
});
it.each(['PENDING', 'INCOMING', 'CONNECTED', 'SELF', 'FORBIDDEN', 'REJECTED'])('does not resubmit or auto-accept the %s state', async state => {
    api.post.mockResolvedValue({ data: { ...ready, state } }); render(<Tree />);
    await screen.findByRole('heading', { name: 'Alice#1234' });
    expect(screen.queryByRole('button', { name: 'Barátkérelem küldése' })).not.toBeInTheDocument();
    expect(api.post.mock.calls.map(([url]) => url)).toEqual(['/invitations/preview']);
});
it('blocks unverified accounts before loading a preview or sending a request', () => {
    auth.user.verified = false; render(<Tree />);
    expect(screen.getByRole('button', { name: 'Megerősítés ellenőrzése' })).toBeInTheDocument();
    expect(api.post).not.toHaveBeenCalled();
});
it('never resumes an earlier invitation from an invalid explicit link', () => {
    rememberInvitation({ kind: 'friend', token }); render(<Tree path="/invite/friend#token=bad" />);
    expect(screen.getByRole('alert')).toHaveTextContent('Hiányzó vagy hibás');
    expect(api.post).not.toHaveBeenCalled(); expect(readInvitation()).toBeNull();
});
it('ignores the old account preview after an account switch', async () => {
    let resolve; api.post.mockImplementationOnce(() => new Promise(done => { resolve = done; }));
    const view=render(<Tree />); auth.user={ ...student, userId: 'owner' };
    api.post.mockResolvedValue({ data: { ...ready, state: 'SELF' } }); view.rerender(<Tree />);
    await screen.findByText('Ez a saját meghívólinked. Másokkal megosztva használhatod.');
    await act(async () => resolve({ data: ready }));
    expect(screen.queryByRole('button', { name: 'Barátkérelem küldése' })).not.toBeInTheDocument();
});
it('clears an unavailable invitation without creating a request', async () => {
    api.post.mockRejectedValue({ response: { status: 404, data: { message: 'Visszavont meghívó' } } });
    render(<Tree />); await screen.findByText('Visszavont meghívó');
    expect(readInvitation()).toBeNull(); expect(api.post).toHaveBeenCalledTimes(1);
});

it('refreshes an approved classroom request without resubmitting it', async () => {
    const classroomId = '12345678-1234-1234-1234-123456789abc';
    api.post.mockResolvedValueOnce({ data: { ...ready, kind: 'classroom', state: 'PENDING', classroomId } })
        .mockResolvedValueOnce({ data: { ...ready, kind: 'classroom', state: 'CONNECTED', classroomId } });
    render(<Tree path={`/invite/classroom#token=${token}`} />);
    fireEvent.click(await screen.findByRole('button', { name: 'Állapot frissítése' }));
    const link = await screen.findByRole('link', { name: 'Osztályterem megnyitása' });
    expect(link).toHaveAttribute('href', `/classrooms/${classroomId}`);
    expect(screen.queryByText('A kérelmed jóváhagyásra vár.')).not.toBeInTheDocument();
    expect(api.post.mock.calls.map(([url]) => url)).toEqual(['/invitations/preview', '/invitations/preview']);
});

it.each([true, false])('updates pending state from notifications only when auto refresh is %s', async enabled => {
    feed.autoRefreshEnabled = enabled;
    api.post.mockResolvedValue({ data: { ...ready, state: 'PENDING' } });
    const view = render(<Tree />);
    await screen.findByRole('button', { name: 'Állapot frissítése' });
    api.post.mockResolvedValue({ data: { ...ready, state: 'CONNECTED' } });
    feed.notifications = { total: 1 };
    view.rerender(<Tree />);
    if (enabled) {
        await screen.findByRole('link', { name: 'Közösség megnyitása' });
        expect(screen.queryByText('A kérelmed jóváhagyásra vár.')).not.toBeInTheDocument();
        expect(api.post).toHaveBeenCalledTimes(2);
    } else {
        expect(screen.getByText('A kérelmed jóváhagyásra vár.')).toBeInTheDocument();
        expect(api.post).toHaveBeenCalledTimes(1);
    }
});
