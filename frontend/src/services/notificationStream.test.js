import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { openNotificationStream } from './notificationStream';
import { beginAuthentication, endSession, getAccessToken, getAuthEpoch, storeAccessToken } from './authSession';
import { refreshAccessToken } from './api';
vi.mock('./api', () => ({ default: { defaults: { baseURL: '/api' } }, refreshAccessToken: vi.fn() }));
let cleanup, controller, signals;
const token = seconds => `header.${btoa(JSON.stringify({ sid: 'same-session', exp: Math.floor(Date.now() / 1000) + seconds }))}.signature`;
const flush = () => vi.advanceTimersByTimeAsync(0);
const response = () => ({ status: 200, ok: true, headers: { get: () => 'text/event-stream' },
    body: new ReadableStream({ start(value) { controller = value; } }) });
const stream = () => {
    fetch.mockImplementation((_url, options) => {
        const result = response(); const ownController = controller; signals.push(options.signal);
        options.signal.addEventListener('abort', () => { try { ownController.error(new DOMException('Aborted', 'AbortError')); } catch { /* Closed. */ } });
        return Promise.resolve(result);
    });
};
beforeEach(() => {
    vi.useFakeTimers(); vi.resetAllMocks(); vi.spyOn(Math, 'random').mockReturnValue(0.5);
    localStorage.clear(); sessionStorage.clear(); endSession(); storeAccessToken(token(3600), beginAuthentication(false));
    vi.stubGlobal('fetch', vi.fn()); cleanup = null; signals = [];
});
afterEach(async () => { cleanup?.(); await flush(); endSession(); vi.clearAllTimers(); vi.useRealTimers(); vi.unstubAllGlobals(); vi.restoreAllMocks(); });
it('uses a bearer header without URL credentials and parses fragmented CRLF pings', async () => {
    stream(); const connected = vi.fn(), ping = vi.fn(); cleanup = openNotificationStream({ onConnected: connected, onPing: ping }); await flush();
    const [url, options] = fetch.mock.calls[0]; expect(url).toBe('/api/notifications/stream'); expect(url).not.toContain(getAccessToken());
    expect(options.headers.Authorization).toBe(`Bearer ${getAccessToken()}`); expect(options.credentials).toBe('omit'); expect(connected).toHaveBeenCalledTimes(1);
    for (const part of ['event: pi', 'ng\r', '\ndata: up', 'date\r\n\r\n: heartbeat\n\n']) controller.enqueue(new TextEncoder().encode(part));
    await flush(); expect(ping).toHaveBeenCalledTimes(1);
});
it('refreshes once after 401 and retries with the current token', async () => {
    const accepted = response(); fetch.mockResolvedValueOnce({ status: 401, body: null }).mockResolvedValueOnce(accepted);
    const fresh = token(7200); refreshAccessToken.mockImplementation(async () => { storeAccessToken(fresh, getAuthEpoch()); return fresh; });
    cleanup = openNotificationStream({}); await flush(); expect(refreshAccessToken).toHaveBeenCalledTimes(1);
    expect(fetch.mock.calls[1][1].headers.Authorization).toBe(`Bearer ${fresh}`); controller.close();
});
it('never refreshes or loops on 403 and retains the account token', async () => {
    const current = getAccessToken(); fetch.mockResolvedValue({ status: 403, body: null });
    cleanup = openNotificationStream({}); await flush(); await vi.advanceTimersByTimeAsync(60000);
    expect(fetch).toHaveBeenCalledTimes(1); expect(refreshAccessToken).not.toHaveBeenCalled(); expect(getAccessToken()).toBe(current);
});
it('ends the session after the refreshed token is rejected too', async () => {
    fetch.mockResolvedValue({ status: 401, body: null }); refreshAccessToken.mockImplementation(async () => { storeAccessToken(token(7200), getAuthEpoch()); });
    cleanup = openNotificationStream({}); await flush(); expect(fetch).toHaveBeenCalledTimes(2); expect(getAccessToken()).toBeNull();
    await vi.advanceTimersByTimeAsync(60000); expect(fetch).toHaveBeenCalledTimes(2);
});
it('backs off network failures and stops reconnecting after cleanup', async () => {
    fetch.mockRejectedValue(new Error('network failure with secret diagnostics'));
    cleanup = openNotificationStream({}); await flush(); await vi.advanceTimersByTimeAsync(999); expect(fetch).toHaveBeenCalledTimes(1);
    await vi.advanceTimersByTimeAsync(1); expect(fetch).toHaveBeenCalledTimes(2); cleanup(); await vi.advanceTimersByTimeAsync(60000); expect(fetch).toHaveBeenCalledTimes(2);
});
it('aborts immediately on account change and ignores late activity', async () => {
    stream(); const ping = vi.fn(); cleanup = openNotificationStream({ onPing: ping }); await flush(); beginAuthentication(false); await flush();
    expect(signals[0].aborted).toBe(true); expect(ping).not.toHaveBeenCalled(); await vi.advanceTimersByTimeAsync(60000); expect(fetch).toHaveBeenCalledTimes(1);
});
it('rotates before expiry through shared refresh', async () => {
    storeAccessToken(token(2), getAuthEpoch()); stream(); refreshAccessToken.mockImplementation(async () => { storeAccessToken(token(7200), getAuthEpoch()); });
    cleanup = openNotificationStream({}); await flush(); await vi.advanceTimersByTimeAsync(1100); expect(refreshAccessToken).toHaveBeenCalledTimes(1); expect(signals[0].aborted).toBe(true);
});
it('uses the latest token after a server-side close', async () => {
    stream(); cleanup = openNotificationStream({}); await flush(); const fresh = token(7200); storeAccessToken(fresh, getAuthEpoch()); controller.close();
    await flush(); await vi.advanceTimersByTimeAsync(1000); expect(fetch.mock.calls[1][1].headers.Authorization).toBe(`Bearer ${fresh}`); expect(refreshAccessToken).not.toHaveBeenCalled();
});
it('rejects oversized frames without delivering a ping', async () => {
    stream(); const ping = vi.fn(); cleanup = openNotificationStream({ onPing: ping }); await flush(); controller.enqueue(new TextEncoder().encode('event: ' + 'x'.repeat(9000)));
    await flush(); expect(signals[0].aborted).toBe(true); expect(ping).not.toHaveBeenCalled();
});
it('bounds a stalled handshake', async () => {
    fetch.mockImplementation((_url, options) => new Promise((_resolve, reject) => options.signal.addEventListener('abort', () => reject(new DOMException('Aborted', 'AbortError')))));
    cleanup = openNotificationStream({}); await flush(); await vi.advanceTimersByTimeAsync(16000); expect(fetch).toHaveBeenCalledTimes(2);
});
