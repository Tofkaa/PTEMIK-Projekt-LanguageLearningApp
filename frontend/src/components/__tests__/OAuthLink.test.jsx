import { StrictMode } from 'react';
import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import OAuthConnections from '../OAuthConnections';
import OAuthLinkConfirmation from '../../pages/OAuthLinkConfirmation';
import api from '../../services/api';
const auth = vi.hoisted(() => ({ user: { userId: 'first' } }));
vi.mock('../../context/AuthContext', () => ({ useAuth: () => auth }));
vi.mock('../../services/api', () => ({ default: { get: vi.fn(), post: vi.fn() } }));
beforeEach(() => {
    vi.resetAllMocks(); auth.user = { userId: 'first' };
    api.get.mockResolvedValue({ data: [{ provider: 'google', linked: false }, { provider: 'discord', linked: true }] });
    window.history.replaceState({}, '', '/oauth-link/confirm#proof=test-proof');
});
afterEach(() => { cleanup(); window.history.replaceState({}, '', '/'); });
const review = { provider: 'google', accountEmail: 'original@example.test', providerEmail: 'provider@example.test' };
function page() { return <MemoryRouter initialEntries={['/oauth-link/confirm']}><Routes>
    <Route path="/oauth-link/confirm" element={<OAuthLinkConfirmation />} />
    <Route path="/settings" element={<h1>Beállítások</h1>} />
</Routes></MemoryRouter>; }
it('asks email approval first and names/focuses the code field; failures preserve typed code', async () => {
    api.post.mockResolvedValue({ data: { provider: 'google', email: 'original@example.test' } });
    render(<OAuthConnections />);
    expect(await screen.findByText('Discord: hozzákapcsolva')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Discord hozzákapcsolása' })).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Google hozzákapcsolása' }));
    await screen.findByLabelText('Összekapcsolási kód');
    await waitFor(() => expect(screen.getByLabelText('Összekapcsolási kód')).toHaveFocus());
    expect(screen.getByLabelText('Összekapcsolási kód')).toHaveAttribute('autocomplete', 'one-time-code');
    expect(screen.getByLabelText('Összekapcsolási kód')).toHaveAccessibleDescription(/original@example.test.*15 percig/);
    expect(api.post).toHaveBeenCalledExactlyOnceWith('/oauth-links/request', { provider: 'google' }, { skipGlobalError: true });
    fireEvent.change(screen.getByLabelText('Összekapcsolási kód'), { target: { value: '123456' } });
    api.post.mockRejectedValueOnce({ response: { data: { message: 'Hibás kód.' } } });
    fireEvent.click(screen.getByRole('button', { name: 'Tovább a szolgáltatóhoz' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Hibás');
    expect(screen.getByLabelText('Összekapcsolási kód')).toHaveValue('123456');
});
it('can cancel without changing providers', async () => {
    api.post.mockResolvedValue({ data: { provider: 'google', email: 'original@example.test' } });
    render(<OAuthConnections />);
    fireEvent.click(await screen.findByRole('button', { name: 'Google hozzákapcsolása' }));
    fireEvent.click(await screen.findByRole('button', { name: 'Összekapcsolás megszakítása' }));
    expect(await screen.findByRole('button', { name: 'Google hozzákapcsolása' })).toBeEnabled();
    expect(api.post).toHaveBeenLastCalledWith('/oauth-links/cancel', null, { skipGlobalError: true });
});
it('StrictMode previews without linking, clears URL proof and needs explicit confirmation', async () => {
    api.post.mockResolvedValue({ data: review });render(<StrictMode>{page()}</StrictMode>);
    const confirm = await screen.findByRole('button', { name: 'Összekapcsolás megerősítése' });
    expect(window.location.hash).toBe('');expect(screen.getByText(review.accountEmail)).toBeInTheDocument();
    expect(api.post.mock.calls.every(([url]) => url === '/oauth-links/preview')).toBe(true);
    expect(screen.getByRole('heading')).toHaveFocus();fireEvent.click(confirm);
    expect(await screen.findByRole('heading', { name: 'Beállítások' })).toBeInTheDocument();
    expect(api.post).toHaveBeenLastCalledWith('/oauth-links/confirm', { proof: 'test-proof', confirmed: true }, { skipGlobalError: true });
});
it('missing proof never sends an API request', () => {
    window.history.replaceState({}, '', '/oauth-link/confirm');render(page());
    expect(screen.getByRole('alert')).toHaveTextContent('hiányzik');expect(api.post).not.toHaveBeenCalled();
    expect(screen.queryByRole('button', { name: 'Összekapcsolás megerősítése' })).not.toBeInTheDocument();
});
it('wrong/expired approval shows a recoverable error without confirmation', async () => {
    api.post.mockRejectedValue({ response: { data: { message: 'Érvénytelen vagy lejárt.' } } });render(page());
    expect(await screen.findByRole('alert')).toHaveTextContent('lejárt');
    expect(screen.queryByRole('button', { name: 'Összekapcsolás megerősítése' })).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Vissza a Beállításokhoz' }));
    expect(await screen.findByRole('heading', { name: 'Beállítások' })).toBeInTheDocument();
});
it('allows retry after network failure without automatic confirmation', async () => {
    api.post.mockResolvedValueOnce({ data: review }).mockRejectedValueOnce({ response: { data: { message: 'Hálózati hiba.' } } }).mockResolvedValueOnce({});
    render(page());fireEvent.click(await screen.findByRole('button', { name: 'Összekapcsolás megerősítése' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Hálózati');
    fireEvent.click(screen.getByRole('button', { name: 'Összekapcsolás megerősítése' }));
    expect(await screen.findByRole('heading', { name: 'Beállítások' })).toBeInTheDocument();
});
it('account switch clears previous review and prevents stale confirmation', async () => {
    api.post.mockResolvedValue({ data: review });const view=render(page());
    await screen.findByRole('button', { name: 'Összekapcsolás megerősítése' });
    auth.user={ userId: 'second' };view.rerender(page());
    await waitFor(() => expect(screen.queryByText(review.accountEmail)).not.toBeInTheDocument());
    expect(screen.queryByRole('button', { name: 'Összekapcsolás megerősítése' })).not.toBeInTheDocument();
});

it('a late confirmation response from a previous account cannot redirect the new account', async () => {
    let complete;
    api.post.mockResolvedValueOnce({ data: review }).mockImplementationOnce(() => new Promise(resolve => { complete = resolve; }));
    const view = render(page());
    fireEvent.click(await screen.findByRole('button', { name: 'Összekapcsolás megerősítése' }));
    await waitFor(() => expect(api.post).toHaveBeenCalledTimes(2));
    auth.user = { userId: 'second' }; view.rerender(page());
    await act(async () => complete({}));
    expect(screen.queryByRole('heading', { name: 'Beállítások' })).not.toBeInTheDocument();
    expect(screen.getByRole('alert')).toHaveTextContent('hiányzik');
});
