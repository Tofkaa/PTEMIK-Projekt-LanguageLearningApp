import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import InvitationShare from './InvitationShare';
import api from '../services/api';
const auth = vi.hoisted(() => ({ user: { userId: 'owner', name: 'Alice', verified: true, role: 'STUDENT' } }));
vi.mock('../context/AuthContext', () => ({ useAuth: () => auth }));
vi.mock('../services/api', () => ({ default: { post: vi.fn(), put: vi.fn(), delete: vi.fn() } }));
let copy;
beforeEach(() => {
    vi.clearAllMocks(); auth.user={ userId: 'owner', name: 'Alice', verified: true, role: 'STUDENT' };
    copy=vi.fn().mockResolvedValue(); Object.defineProperty(navigator, 'clipboard', { configurable: true, value: { writeText: copy } });
    api.post.mockResolvedValue({ data: { kind: 'friend', token: 'a'.repeat(43) } });
    api.put.mockResolvedValue({ data: { kind: 'friend', token: 'b'.repeat(43) } }); api.delete.mockResolvedValue({});
});
afterEach(() => { cleanup(); vi.useRealTimers(); vi.restoreAllMocks(); vi.unstubAllGlobals(); });
it('copies a reusable link without rotating it', async () => {
    render(<InvitationShare />); fireEvent.click(screen.getByRole('button', { name: 'Meghívólink másolása: saját profil' }));
    await waitFor(() => expect(copy).toHaveBeenCalledWith(`${window.location.origin}/invite/friend#token=${'a'.repeat(43)}`));
    expect(api.post).toHaveBeenCalledWith('/invitations/friend'); expect(api.put).not.toHaveBeenCalled();
});
it('provides a focused read-only manual-copy fallback when the clipboard is blocked', async () => {
    copy.mockRejectedValue(new Error('blocked')); render(<InvitationShare />);
    fireEvent.click(screen.getByRole('button', { name: 'Meghívólink másolása: saját profil' }));
    const field=await screen.findByRole('textbox', { name: 'Meghívólink kézi másolása' });
    expect(field).toHaveAttribute('readonly'); expect(field.value).toContain('/invite/friend#token=');
    await waitFor(() => expect(field).toHaveFocus());
});
it('uses separate explicit operations to rotate and revoke', async () => {
    render(<InvitationShare />); fireEvent.click(screen.getByText('Meghívólink kezelése'));
    fireEvent.click(screen.getByRole('button', { name: 'Új link készítése és másolása' }));
    await waitFor(() => expect(api.put).toHaveBeenCalledWith('/invitations/friend'));
    await waitFor(() => expect(screen.getByRole('button', { name: 'Link visszavonása' })).toBeEnabled());
    fireEvent.click(screen.getByRole('button', { name: 'Link visszavonása' }));
    await waitFor(() => expect(api.delete).toHaveBeenCalledWith('/invitations/friend'));
});
it('does not expose link management for unverified accounts', () => {
    auth.user.verified=false; render(<InvitationShare />); expect(screen.queryByRole('button')).not.toBeInTheDocument(); expect(api.post).not.toHaveBeenCalled();
});
it('ignores a completed copy request from a previous account', async () => {
    let resolve; api.post.mockImplementation(() => new Promise(done => { resolve=done; }));
    const view=render(<InvitationShare />); fireEvent.click(screen.getByRole('button', { name: 'Meghívólink másolása: saját profil' }));
    auth.user={ ...auth.user, userId: 'other' }; view.rerender(<InvitationShare />);
    await act(async () => resolve({ data: { kind: 'friend', token: 'a'.repeat(43) } }));
    expect(copy).not.toHaveBeenCalled();
});

it('dismisses successful copying but keeps the focused manual-copy link available', async () => {
    copy.mockRejectedValue(new Error('blocked'));
    vi.stubGlobal('IntersectionObserver', undefined);
    vi.spyOn(document, 'hidden', 'get').mockReturnValue(false);
    render(<InvitationShare />);
    fireEvent.click(screen.getByRole('button', { name: 'Meghívólink másolása: saját profil' }));
    const field = await screen.findByRole('textbox', { name: 'Meghívólink kézi másolása' });
    expect(screen.getByRole('status')).toHaveTextContent('A link elkészült.');
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] });
    const alert = screen.getByRole('status').querySelector('.alert');
    fireEvent.mouseEnter(alert); fireEvent.mouseLeave(alert);
    act(() => vi.advanceTimersByTime(10000));
    expect(screen.queryByRole('status')).not.toBeInTheDocument();
    expect(field).toBeInTheDocument(); expect(field).toHaveFocus();
});
