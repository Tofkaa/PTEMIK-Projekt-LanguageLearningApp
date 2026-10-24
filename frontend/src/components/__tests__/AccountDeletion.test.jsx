import { act, cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import AccountDeletion from '../../pages/AccountDeletion';
import Settings from '../../pages/Settings';
import api from '../../services/api';

const auth = vi.hoisted(() => ({ user: null, logout: vi.fn(), setUser: vi.fn() }));
vi.mock('../../context/AuthContext', () => ({ useAuth: () => auth }));
vi.mock('../../services/api', () => ({ default: { post: vi.fn(), delete: vi.fn(), get: vi.fn() } }));

beforeEach(() => {
    vi.resetAllMocks(); sessionStorage.clear();
    auth.user = { userId: 'oauth-user', email: 'existing@example.test', name: 'Teszt', authProvider: 'GOOGLE' };
    auth.logout.mockResolvedValue();
    api.post.mockResolvedValue({ data: { email: 'existing@example.test', expiresAt: '2026-10-24T14:15:00Z' } });
});
afterEach(() => { cleanup(); sessionStorage.clear(); });
const renderPage = (path = '/account-deletion#token=test-secret') => render(<MemoryRouter initialEntries={[path]}><AccountDeletion /></MemoryRouter>);

describe('OAuth deletion approval page', () => {
    it('opening a valid link only previews and requires a separate unchecked confirmation', async () => {
        renderPage();
        const check = await screen.findByRole('checkbox');
        expect(check).not.toBeChecked();
        expect(check).toHaveAccessibleDescription(/existing@example.test.*végleges.*Jóváhagyás lejárata/);
        expect(screen.getByRole('button', { name: 'Fiókom törlése', exact: true })).toBeDisabled();
        expect(screen.getByText('existing@example.test')).toBeInTheDocument();
        expect(api.post).toHaveBeenCalledExactlyOnceWith('/users/me/deletion/preview', { token: 'test-secret' });
        expect(api.delete).not.toHaveBeenCalled();
        expect(auth.logout).not.toHaveBeenCalled();
        await waitFor(() => expect(screen.getByRole('heading', { level: 1 })).toHaveFocus());
        fireEvent.click(check);
        expect(screen.getByRole('button', { name: 'Fiókom törlése', exact: true })).toBeEnabled();
        expect(api.post).toHaveBeenCalledTimes(1);
        fireEvent.click(check);
        expect(screen.getByRole('button', { name: 'Fiókom törlése', exact: true })).toBeDisabled();
    });

    it('shows a recoverable expired or wrong-session rejection without confirmation controls', async () => {
        api.post.mockRejectedValue({ response: { data: { message: 'Lejárt vagy másik munkamenethez tartozó link.' } } });
        renderPage();
        expect(await screen.findByRole('alert')).toHaveTextContent('Lejárt vagy másik munkamenethez tartozó link.');
        expect(screen.queryByRole('checkbox')).not.toBeInTheDocument();
        expect(screen.getByRole('link', { name: 'Új jóváhagyó link kérése a Beállításokban' })).toHaveAttribute('href', '/settings');
        expect(api.post).toHaveBeenCalledTimes(1);
    });

    it('does not transmit a missing token or a token without an authenticated account', () => {
        const view = renderPage('/account-deletion');
        expect(screen.getByRole('alert')).toHaveTextContent('Hiányzó');
        expect(api.post).not.toHaveBeenCalled();
        view.unmount(); auth.user = null;
        renderPage();
        expect(screen.getByRole('link', { name: 'Bejelentkezés', exact: true })).toBeInTheDocument();
        expect(api.post).not.toHaveBeenCalled();
        expect(screen.queryByRole('checkbox')).not.toBeInTheDocument();
    });

    it('preserves deliberate confirmation after a failed final submission and logs out only after success', async () => {
        api.post.mockResolvedValueOnce({ data: { email: 'existing@example.test', expiresAt: '2026-10-24T14:15:00Z' } })
            .mockRejectedValueOnce({ response: { data: { message: 'Átmeneti hálózati hiba.' } } })
            .mockResolvedValueOnce({});
        renderPage();
        const check = await screen.findByRole('checkbox');
        fireEvent.click(check);
        fireEvent.click(screen.getByRole('button', { name: 'Fiókom törlése', exact: true }));
        expect(await screen.findByRole('alert')).toHaveTextContent('Átmeneti hálózati hiba.');
        expect(check).toBeChecked();
        expect(auth.logout).not.toHaveBeenCalled();
        fireEvent.click(screen.getByRole('button', { name: 'Fiókom törlése', exact: true }));
        expect(await screen.findByRole('heading', { name: 'A fiókod megszűnt' })).toBeInTheDocument();
        expect(auth.logout).toHaveBeenCalledExactlyOnceWith(true);
        expect(api.post.mock.calls.slice(1)).toEqual([
            ['/users/me/deletion/confirm', { token: 'test-secret', confirmed: true }],
            ['/users/me/deletion/confirm', { token: 'test-secret', confirmed: true }],
        ]);
        expect(screen.getByRole('link', { name: 'Vissza a belépéshez' })).toBeInTheDocument();
    });

    it('cannot submit twice while the first final request is pending', async () => {
        let finish;
        api.post.mockResolvedValueOnce({ data: { email: 'existing@example.test', expiresAt: '2026-10-24T14:15:00Z' } })
            .mockImplementationOnce(() => new Promise(resolve => { finish = resolve; }));
        renderPage(); fireEvent.click(await screen.findByRole('checkbox'));
        const button = screen.getByRole('button', { name: 'Fiókom törlése', exact: true });
        fireEvent.click(button); fireEvent.click(button);
        expect(api.post).toHaveBeenCalledTimes(2);
        expect(screen.getByRole('checkbox')).toBeDisabled();
        expect(screen.getByRole('heading', { name: 'Fióktörlés folyamatban…' })).toHaveFocus();
        await act(async () => finish({}));
        expect(auth.logout).toHaveBeenCalledTimes(1);
    });

    it('clears the previous account review and checkbox when the authenticated account changes', async () => {
        api.post.mockResolvedValueOnce({ data: { email: 'existing@example.test', expiresAt: '2026-10-24T14:15:00Z' } })
            .mockRejectedValueOnce({ response: { data: { message: 'Másik fiókhoz tartozó link.' } } });
        const view = renderPage();
        fireEvent.click(await screen.findByRole('checkbox'));
        auth.user = { ...auth.user, userId: 'different-account', email: 'other@example.test' };
        view.rerender(<MemoryRouter initialEntries={['/account-deletion#token=test-secret']}><AccountDeletion /></MemoryRouter>);
        expect(screen.queryByRole('checkbox')).not.toBeInTheDocument();
        expect(await screen.findByRole('alert')).toHaveTextContent('Másik fiókhoz tartozó link.');
        expect(screen.queryByText('existing@example.test')).not.toBeInTheDocument();
        expect(api.post).toHaveBeenCalledTimes(2);
        expect(auth.logout).not.toHaveBeenCalled();
    });
});

describe.each(['GOOGLE', 'DISCORD'])('%s deletion request in settings', provider => {
    it('requests email approval without a password, typed deletion word, or account deletion', async () => {
        auth.user = { ...auth.user, authProvider: provider };
        sessionStorage.setItem('settingsActiveTab', 'privacy');
        render(<MemoryRouter><Settings /></MemoryRouter>);
        const opener = screen.getByRole('button', { name: /Fiók.*törlése/i });
        opener.focus(); fireEvent.click(opener);
        const dialog = await screen.findByRole('dialog');
        expect(within(dialog).queryByLabelText('Jelszó', { exact: true })).not.toBeInTheDocument();
        expect(within(dialog).queryByRole('textbox')).not.toBeInTheDocument();
        expect(within(dialog).getByText('existing@example.test')).toBeInTheDocument();
        fireEvent.click(within(dialog).getByRole('button', { name: 'Jóváhagyó link kérése' }));
        expect(await within(dialog).findByRole('status')).toHaveTextContent('levél küldését elindítottuk');
        expect(api.post).toHaveBeenCalledExactlyOnceWith('/users/me/deletion/request');
        expect(api.delete).not.toHaveBeenCalled();
        expect(auth.logout).not.toHaveBeenCalled();
        fireEvent.click(within(dialog).getByRole('button', { name: 'Mégse', exact: true }));
        await waitFor(() => expect(opener).toHaveFocus());
    });
});
