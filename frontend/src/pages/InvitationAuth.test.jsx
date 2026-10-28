import { StrictMode } from 'react';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import Login from './Login';
import Register from './Register';
import OAuth2RedirectHandler from '../components/OAuth2RedirectHandler';
import GuestRoute from '../components/GuestRoute';
import api from '../services/api';
import { clearInvitation, rememberInvitation } from '../utils/invitationIntent';

const auth = vi.hoisted(() => ({ user: null, authenticate: vi.fn(), completeOAuthLogin: vi.fn() }));
vi.mock('../context/AuthContext', () => ({ useAuth: () => auth }));
vi.mock('../context/ThemeContext.jsx', () => ({ useTheme: () => ({ theme: 'dark' }) }));
vi.mock('../components/ThemeToggle.jsx', () => ({ default: () => null }));
vi.mock('../services/api', () => ({ default: { get: vi.fn(), post: vi.fn() } }));
const token = 'a'.repeat(43);
const destination = `/invite/friend#token=${token}`;
function Destination() { const location = useLocation(); return <h1>{location.pathname + location.hash}</h1>; }
function Tree({ path }) { return <MemoryRouter initialEntries={[path]}><Routes>
    <Route path="/register" element={<Register />} />
    <Route path="/login" element={<GuestRoute><Login /></GuestRoute>} />
    <Route path="/oauth2/redirect" element={<OAuth2RedirectHandler />} />
    <Route path="/invite/:kind" element={<Destination />} />
    <Route path="/dashboard" element={<h1>Dashboard</h1>} />
</Routes></MemoryRouter>; }
beforeEach(() => {
    vi.clearAllMocks(); sessionStorage.clear(); localStorage.clear(); auth.user = null;
    rememberInvitation({ kind: 'friend', token });
    api.post.mockResolvedValue({ data: { accessToken: 'access-token' } });
    api.get.mockResolvedValue({ data: { userId: 'student', verified: true } });
    auth.authenticate.mockImplementation(async () => { clearInvitation(); });
    auth.completeOAuthLogin.mockImplementation(async () => { clearInvitation(); });
});
afterEach(cleanup);
it('returns from a password login to the captured invitation without submitting a connection', async () => {
    render(<Tree path="/login" />);
    fireEvent.change(screen.getByLabelText('Email cím'), { target: { value: 'student@example.test' } });
    fireEvent.change(screen.getByLabelText('Jelszó'), { target: { value: 'test-password' } });
    fireEvent.click(screen.getByRole('button', { name: 'Belépés' }));
    await screen.findByRole('heading', { name: destination });
    expect(auth.authenticate).toHaveBeenCalledExactlyOnceWith({ email: 'student@example.test', password: 'test-password', rememberMe: false });
    expect(api.post).not.toHaveBeenCalled();
});
it('returns from an OAuth callback to the captured invitation without submitting a connection', async () => {
    render(<Tree path="/oauth2/redirect#code=oauth-code" />);
    await screen.findByRole('heading', { name: destination });
    expect(auth.completeOAuthLogin).toHaveBeenCalledExactlyOnceWith('oauth-code');
    expect(api.post).not.toHaveBeenCalled();
});
it('retains the invitation after a failed OAuth callback so login can be retried', async () => {
    auth.completeOAuthLogin.mockRejectedValue(new Error('Network unavailable'));
    render(<Tree path="/oauth2/redirect#code=oauth-code" />);
    await screen.findByRole('heading', { name: 'Bejelentkezés' });
    expect(sessionStorage.getItem('pendingInvitation')).toContain(token);
    expect(localStorage.getItem('token')).toBeNull();
    expect(api.post).not.toHaveBeenCalled();
});
it('does not treat a leftover browser token as an authenticated account', async () => {
    localStorage.setItem('token', 'stale-token'); render(<Tree path="/login" />);
    await waitFor(() => expect(screen.getByRole('heading', { name: 'Bejelentkezés' })).toBeInTheDocument());
    expect(api.get).not.toHaveBeenCalled();
});

it('explains returning to the original tab and preserves its invitation through registration and login', async () => {
    render(<Tree path="/register" />);
    fireEvent.change(screen.getByLabelText('Teljes név'), { target: { value: 'Teszt' } });
    fireEvent.change(screen.getByLabelText('Email cím'), { target: { value: 'new@example.test' } });
    fireEvent.change(screen.getByLabelText('Jelszó', { exact: true }), { target: { value: 'test-password' } });
    fireEvent.change(screen.getByLabelText('Jelszó újra'), { target: { value: 'test-password' } });
    fireEvent.click(screen.getByRole('checkbox'));
    fireEvent.click(screen.getByRole('button', { name: 'Regisztráció', exact: true }));
    await screen.findByText('Az emailcímed megerősítése után térj vissza erre a lapra, és jelentkezz be a meghívó folytatásához.');
    expect(sessionStorage.getItem('pendingInvitation')).toContain(token);
    expect(localStorage.getItem('registrationInvitation')).toBeNull();
    fireEvent.click(screen.getByRole('button', { name: 'Vissza a bejelentkezéshez' }));
    fireEvent.change(screen.getByLabelText('Email cím'), { target: { value: 'new@example.test' } });
    fireEvent.change(screen.getByLabelText('Jelszó'), { target: { value: 'test-password' } });
    fireEvent.click(screen.getByRole('button', { name: 'Belépés' }));
    await screen.findByRole('heading', { name: destination });
    expect(api.post.mock.calls.map(([url]) => url)).toEqual(['/auth/register']);
    expect(auth.authenticate).toHaveBeenCalledExactlyOnceWith({ email: 'new@example.test', password: 'test-password', rememberMe: false });
});

it('retains the captured invitation during the StrictMode OAuth effect replay', async () => {
    render(<StrictMode><Tree path="/oauth2/redirect#code=oauth-once" /></StrictMode>);
    await screen.findByRole('heading', { name: destination });
    expect(auth.completeOAuthLogin).toHaveBeenCalledExactlyOnceWith('oauth-once');
    expect(api.post).not.toHaveBeenCalled();
});

it.each([
    ['email_in_use', 'Ehhez az emailcímhez már tartozik fiók.'],
    ['unverified_email', 'A belépéshez előbb erősítsd meg az emailcímedet'],
    ['identity_unavailable', 'Ezzel a szolgáltatói fiókkal most nem sikerült bejelentkezni.'],
    ['access_denied', 'A közösségi belépés megszakadt.'],
])('announces the fixed %s OAuth failure and retains the invitation for retry', async (code, message) => {
    render(<Tree path={`/login?oauth_error=${code}`} />);
    expect(screen.getByRole('alert')).toHaveTextContent(message);
    expect(sessionStorage.getItem('pendingInvitation')).toContain(token);
    expect(auth.authenticate).not.toHaveBeenCalled();
    expect(auth.completeOAuthLogin).not.toHaveBeenCalled();
});
it('does not render an arbitrary OAuth error or object prototype key from the URL', () => {
    render(<Tree path="/login?oauth_error=toString" />);
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Belépés' })).toBeEnabled();
});
