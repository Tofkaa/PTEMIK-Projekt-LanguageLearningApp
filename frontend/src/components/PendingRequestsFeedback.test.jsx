import { act, cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import PendingRequests from './PendingRequests';
import api from '../services/api';

vi.mock('../services/api', () => ({ default: { get: vi.fn(), post: vi.fn() } }));
vi.mock('../context/NotificationContext', () => ({ useNotifications: () => ({ notifications: {}, refreshNotifications: vi.fn() }) }));
beforeEach(() => {
    vi.clearAllMocks();
    vi.spyOn(document, 'hidden', 'get').mockReturnValue(false);
    vi.stubGlobal('IntersectionObserver', undefined);
    api.get.mockResolvedValue({ data: [1, 2].map(id => ({ friendshipId: `${id}`, senderName: `Teszt${id}`, senderTag: '42' })) });
    api.post.mockResolvedValue({ data: { message: 'Barátkérelem elfogadva.' } });
});
afterEach(() => { cleanup(); vi.useRealTimers(); vi.restoreAllMocks(); vi.unstubAllGlobals(); });
const advance = ms => act(() => vi.advanceTimersByTime(ms));
async function setup() {
    render(<PendingRequests />);
    await screen.findByRole('button', { name: 'Elfogad: Teszt1#42' });
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout', 'performance'] });
}
async function accept(name) {
    const button = screen.getByRole('button', { name });
    act(() => button.focus());
    await act(async () => fireEvent.click(button));
}
it('removes the success message after ten seconds without reloading or moving list focus', async () => {
    await setup(); await accept('Elfogad: Teszt1#42');
    expect(screen.getByRole('status')).toHaveTextContent('Barátkérelem elfogadva.');
    advance(10000);
    expect(screen.queryByRole('status')).not.toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Beérkező kérelmek (1)' })).toHaveFocus();
    expect(api.get).toHaveBeenCalledTimes(1);
});
it('gives an identical new success message its own full interval', async () => {
    await setup(); await accept('Elfogad: Teszt1#42'); advance(6000);
    await accept('Elfogad: Teszt2#42'); advance(5000);
    expect(screen.getByRole('status')).toHaveTextContent('Barátkérelem elfogadva.');
    advance(5000); expect(screen.queryByRole('status')).not.toBeInTheDocument();
});
it('keeps action errors visible and preserves the failed request', async () => {
    await setup(); api.post.mockRejectedValueOnce({ response: { data: { message: 'Próbáld újra később.' } } });
    await accept('Elfogad: Teszt1#42'); advance(60000);
    expect(screen.getByRole('alert')).toHaveTextContent('Próbáld újra később.');
    expect(screen.getByRole('button', { name: 'Elfogad: Teszt1#42' })).toHaveFocus();
});
