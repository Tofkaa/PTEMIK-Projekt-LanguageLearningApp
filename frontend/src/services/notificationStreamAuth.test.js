import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { waitFor } from '@testing-library/react';
import api from './api';
import { openNotificationStream } from './notificationStream';
import { beginAuthentication, endSession, getAccessToken, storeAccessToken } from './authSession';
let cleanup, streamController;
const token = version => `header.${btoa(JSON.stringify({ sid: 'same-session', exp: Math.floor(Date.now() / 1000) + 3600, version }))}.signature`;
const ok = (config, data) => ({ config, data, status: 200, statusText: '', headers: {} });
beforeEach(() => { endSession(); localStorage.clear(); sessionStorage.clear(); vi.stubGlobal('fetch', vi.fn()); });
afterEach(async () => { cleanup?.(); streamController?.close(); await Promise.resolve(); endSession(); vi.unstubAllGlobals(); });
it('shares one real API refresh between a stream 401 and a simultaneous summary 401', async () => {
    const old = token('old'), fresh = token('new'); storeAccessToken(old, beginAuthentication(false));
    let finishRefresh; let refreshes = 0;
    api.defaults.adapter = config => {
        if (config.url === '/auth/refresh') { refreshes++; return new Promise(resolve => { finishRefresh = () => resolve(ok(config, { accessToken: fresh })); }); }
        if (config.headers.Authorization === `Bearer ${old}`) return Promise.reject(Object.assign(new Error('unauthorized'), { config, response: { status: 401 } }));
        return Promise.resolve(ok(config, { total: 2 }));
    };
    const accepted = { status: 200, ok: true, headers: { get: () => 'text/event-stream' },
        body: new ReadableStream({ start(controller) { streamController = controller; } }) };
    fetch.mockResolvedValueOnce({ status: 401, body: null }).mockResolvedValueOnce(accepted);
    cleanup = openNotificationStream({}); const summary = api.get('/notifications/summary');
    await waitFor(() => expect(finishRefresh).toBeTypeOf('function'));
    await Promise.resolve(); finishRefresh(); expect((await summary).data.total).toBe(2);
    await waitFor(() => expect(fetch).toHaveBeenCalledTimes(2));
    expect(refreshes).toBe(1); expect(getAccessToken()).toBe(fresh);
    expect(fetch.mock.calls[1][1].headers.Authorization).toBe(`Bearer ${fresh}`);
});
