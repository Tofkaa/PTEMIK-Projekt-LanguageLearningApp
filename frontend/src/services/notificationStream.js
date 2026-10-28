import api, { refreshAccessToken } from './api';
import { authSnapshot, endSession, getAccessToken, isCurrentAuth, requireCurrentAuth, subscribeAuth } from './authSession';

const MAX_LINE = 8192;
// Client-side timing only: the server still verifies the signature, session and expiry.
const tokenLifetime = token => {
    try {
        const body = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
        const expiry = JSON.parse(atob(body)).exp;
        return Number.isFinite(expiry) ? Math.max(0, expiry * 1000 - Date.now() - 1000) : 60000;
    } catch { return 60000; }
};

/** Header-authenticated invalidation stream. Returns an immediate, idempotent cleanup function. */
export function openNotificationStream({ onConnected, onPing }) {
    const scope = authSnapshot();
    let stopped = false, controller = null, retryTimer = null, lifetimeTimer = null, failures = 0;
    const current = () => !stopped && isCurrentAuth(scope) && !!getAccessToken();
    const close = () => {
        if (stopped) return;
        stopped = true; controller?.abort(); clearTimeout(retryTimer); clearTimeout(lifetimeTimer); unsubscribe();
    };
    const unsubscribe = subscribeAuth(close);
    const renew = async token => {
        requireCurrentAuth(scope);
        if (getAccessToken() === token) await refreshAccessToken({ ...scope, token }, true);
        requireCurrentAuth(scope);
    };
    const fetchStream = token => fetch(`${(api.defaults?.baseURL || '/api').replace(/\/$/, '')}/notifications/stream`, {
        headers: { Accept: 'text/event-stream', Authorization: `Bearer ${token}` },
        credentials: 'omit', cache: 'no-store', redirect: 'error', signal: controller.signal,
    });
    const connect = async () => {
        if (!current()) { close(); return; }
        controller = new AbortController();
        lifetimeTimer = setTimeout(() => controller.abort(), 15000);
        let reader = null, token = getAccessToken(), rotate = false, refreshOnRotate = false;
        const openedAt = Date.now();
        try {
            if (tokenLifetime(token) <= 0) { await renew(token); token = getAccessToken(); }
            if (!current()) return;
            let response = await fetchStream(token);
            if (response.status === 401) {
                await response.body?.cancel(); await renew(token); token = getAccessToken();
                if (!current()) return;
                response = await fetchStream(token);
            }
            if ([401, 403].includes(response.status)) {
                await response.body?.cancel(); if (response.status === 401) endSession(scope.epoch); close(); return;
            }
            if (!response.ok || !response.headers.get('content-type')?.includes('text/event-stream') || !response.body) {
                await response.body?.cancel(); throw new Error('Invalid notification stream');
            }
            clearTimeout(lifetimeTimer);
            const remaining = tokenLifetime(token); refreshOnRotate = remaining <= 300000;
            lifetimeTimer = setTimeout(() => { rotate = true; controller.abort(); }, Math.max(1, Math.min(300000, remaining)));
            onConnected?.(); reader = response.body.getReader();
            const decoder = new TextDecoder();
            let buffer = '', event = '', hasData = false;
            const parse = (text, final = false) => {
                buffer += text;
                for (;;) {
                    const end = buffer.search(/[\r\n]/);
                    if (end < 0 || (!final && buffer[end] === '\r' && end === buffer.length - 1)) break;
                    if (end > MAX_LINE) throw new Error('Invalid notification frame');
                    const line = buffer.slice(0, end);
                    buffer = buffer.slice(end + (buffer[end] === '\r' && buffer[end + 1] === '\n' ? 2 : 1));
                    if (!line) {
                        if (event === 'ping' && hasData && current()) onPing?.();
                        event = ''; hasData = false;
                    } else if (line.startsWith('event:')) event = line.slice(6).replace(/^ /, '');
                    else if (line === 'data' || line.startsWith('data:')) hasData = true;
                }
                if (buffer.length > MAX_LINE) throw new Error('Invalid notification frame');
            };
            while (current()) {
                const chunk = await reader.read();
                if (chunk.done) { parse(decoder.decode(), true); break; }
                parse(decoder.decode(chunk.value, { stream: true }));
            }
        } catch { /* Polling remains available; retries never log request/token/error objects. */ }
        finally {
            clearTimeout(lifetimeTimer);
            if (reader) { try { await reader.cancel(); } catch { /* Already closed. */ } reader.releaseLock(); }
            controller?.abort();
        }
        if (!current()) { close(); return; }
        if (rotate && refreshOnRotate) {
            try { await renew(token); } catch { /* Auth rejection closes via the shared auth lifecycle. */ }
        }
        if (!current()) { close(); return; }
        if (Date.now() - openedAt > 10000) failures = 0;
        const delay = rotate ? 50 : Math.min(30000, 1000 * 2 ** Math.min(failures++, 5) * (0.75 + Math.random() * 0.5));
        retryTimer = setTimeout(connect, delay);
    };
    void connect();
    return close;
}
