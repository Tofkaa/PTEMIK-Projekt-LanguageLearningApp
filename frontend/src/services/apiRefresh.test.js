import { afterEach, beforeEach, expect, it, vi } from 'vitest';

let api;
let browser;
let session;
const response = (config, data, status = 200) => ({ config, data, status, statusText: '', headers: {} });
const reject = (config, status) => Promise.reject(Object.assign(new Error('Test request failed'), {
    config, ...(status ? { response: response(config, {}, status) } : {}),
}));

beforeEach(async () => {
    vi.resetModules();
    localStorage.clear(); sessionStorage.clear();
    browser = { location: { href: 'https://localhost/settings' }, dispatchEvent: vi.fn() };
    vi.stubGlobal('window', browser);
    api = (await import('./api.jsx')).default;
    session = await import('./authSession.js');
});
afterEach(() => { vi.unstubAllGlobals(); localStorage.clear(); sessionStorage.clear(); });

it('lets a background autosave report its own network error without a duplicate global alert', async () => {
    api.defaults.adapter = config => reject(config);
    await expect(api.put('/assignments/sessions/test/draft', {}, { skipGlobalError: true })).rejects.toThrow();
    expect(browser.dispatchEvent).not.toHaveBeenCalled();
});

it.each([false, true])('refreshes a short access token without leaving the page (remember=%s)', async remember => {
    const storage = remember ? localStorage : sessionStorage;
    storage.setItem('token', 'old-test-token');
    api.defaults.adapter = vi.fn(config => {
        if (config.url === '/auth/refresh') return Promise.resolve(response(config, { accessToken: 'fresh-test-token' }));
        return config._retry ? Promise.resolve(response(config, 'saved')) : reject(config, 401);
    });
    expect((await api.put('/draft', { answer: 'draft' })).data).toBe('saved');
    expect(storage.getItem('token')).toBe('fresh-test-token');
    expect((remember ? sessionStorage : localStorage).getItem('token')).toBeNull();
    expect(browser.location.href).toBe('https://localhost/settings');
    expect(api.defaults.adapter).toHaveBeenCalledTimes(3);
    const retry = api.defaults.adapter.mock.calls.at(-1)[0];
    expect(JSON.parse(retry.data)).toEqual({ answer: 'draft' });
    expect(retry.headers.Authorization).toBe('Bearer fresh-test-token');
});

it.each([undefined, 500, 503])('keeps authentication and permits retry after temporary refresh failure %s', async status => {
    sessionStorage.setItem('token', 'old-test-token');
    let failing = true;
    api.defaults.adapter = vi.fn(config => {
        if (config.url === '/auth/refresh') return failing ? reject(config, status)
            : Promise.resolve(response(config, { accessToken: 'fresh-test-token' }));
        return config._retry ? Promise.resolve(response(config, 'saved')) : reject(config, 401);
    });
    await expect(api.put('/draft', { answer: 'draft' })).rejects.toThrow('Test request failed');
    expect(sessionStorage.getItem('token')).toBe('old-test-token');
    expect(browser.location.href).toBe('https://localhost/settings');
    expect(browser.dispatchEvent).toHaveBeenCalledWith(expect.objectContaining({ type: 'api-error' }));
    failing = false;
    expect((await api.put('/draft', { answer: 'draft' })).data).toBe('saved');
});

it.each([400, 401, 403])('requires login when refresh is explicitly rejected with %s', async status => {
    sessionStorage.setItem('token', 'old-test-token');
    api.defaults.adapter = config => reject(config, config.url === '/auth/refresh' ? status : 401);
    await expect(api.get('/draft')).rejects.toThrow();
    expect(sessionStorage.getItem('token')).toBeNull();
    // React's protected routes handle navigation; public reset forms must not be forcibly reloaded.
    expect(browser.location.href).toBe('https://localhost/settings');
});

it('does not refresh or discard credentials for a genuine permission denial', async () => {
    sessionStorage.setItem('token', 'current-token');
    api.defaults.adapter = vi.fn(config => reject(config, 403));
    await expect(api.get('/admin/users')).rejects.toThrow();
    expect(api.defaults.adapter).toHaveBeenCalledTimes(1);
    expect(sessionStorage.getItem('token')).toBe('current-token');
});

it('reports a shared refresh failure once when foreground work joins a silent background save', async () => {
    sessionStorage.setItem('token','old-token');let finish;
    api.defaults.adapter = config => config.url === '/auth/refresh' ? new Promise((_,fail) => {
        finish=()=>fail(Object.assign(new Error('offline'),{config}));
    }) : reject(config,401);
    const background=Promise.allSettled([api.get('/background',{skipGlobalError:true})]);
    await vi.waitFor(()=>expect(finish).toBeTypeOf('function'));
    const foreground=Promise.allSettled([api.get('/foreground')]);
    await new Promise(resolve=>setTimeout(resolve,0));
    finish();await background;await foreground;
    expect(browser.dispatchEvent).toHaveBeenCalledTimes(1);
});

it('retries every queued request at most once even when the refreshed token is rejected', async () => {
    sessionStorage.setItem('token', 'old-token');
    api.defaults.adapter = vi.fn(config => config.url === '/auth/refresh'
        ? Promise.resolve(response(config, { accessToken: 'new-token' })) : reject(config, 401));
    const results = await Promise.allSettled([api.get('/first'), api.get('/second')]);
    expect(results.every(result => result.status === 'rejected')).toBe(true);
    expect(api.defaults.adapter.mock.calls.filter(([config]) => config.url === '/auth/refresh')).toHaveLength(1);
    expect(api.defaults.adapter.mock.calls.length).toBeLessThanOrEqual(5);
    expect(sessionStorage.getItem('token')).toBeNull();
});

it('shares one successful refresh and keeps both original request bodies', async () => {
    sessionStorage.setItem('token', 'old-token');
    api.defaults.adapter = vi.fn(config => config.url === '/auth/refresh'
        ? Promise.resolve(response(config, { accessToken: 'new-token' }))
        : config._retry ? Promise.resolve(response(config, config.data)) : reject(config, 401));
    const results = await Promise.all([api.put('/first', { answer: 'one' }), api.put('/second', { answer: 'two' })]);
    expect(results.map(result => result.data.answer)).toEqual(['one', 'two']);
    expect(api.defaults.adapter.mock.calls.filter(([config]) => config.url === '/auth/refresh')).toHaveLength(1);
    expect(api.defaults.adapter.mock.calls.find(([config]) => config.url === '/auth/refresh')[0].headers.Authorization).toBeUndefined();
});

it('reuses the refreshed token for a later 401 from an older request', async () => {
    sessionStorage.setItem('token', 'old-token');
    let finishLate;
    api.defaults.adapter = vi.fn(config => {
        if (config.url === '/auth/refresh') return Promise.resolve(response(config, { accessToken: 'new-token' }));
        if (config._retry) return Promise.resolve(response(config, 'done'));
        if (config.url === '/late') return new Promise((_, fail) => { finishLate = () => fail(Object.assign(new Error('late'), { config, response: response(config, {}, 401) })); });
        return reject(config, 401);
    });
    const late = api.get('/late');
    await api.get('/first');
    finishLate();
    expect((await late).data).toBe('done');
    expect(api.defaults.adapter.mock.calls.filter(([config]) => config.url === '/auth/refresh')).toHaveLength(1);
});

it.each([200, 500])('ignores a refresh finishing after logout with status %s', async status => {
    sessionStorage.setItem('token', 'old-token');
    let finish;
    api.defaults.adapter = config => config.url === '/auth/refresh' ? new Promise((resolve, fail) => {
        finish = () => status === 200 ? resolve(response(config, { accessToken: 'old-fresh-token' }))
            : fail(Object.assign(new Error('old server error'), { config, response: response(config, {}, status) }));
    }) : reject(config, 401);
    const result = Promise.allSettled([api.get('/draft')]);
    await vi.waitFor(() => expect(finish).toBeTypeOf('function'));
    session.endSession(); finish();
    expect((await result)[0].reason.code).toBe('ERR_CANCELED');
    expect(session.getAccessToken()).toBeNull();
    expect(browser.dispatchEvent).not.toHaveBeenCalled();
});

it('keeps the new account when an old refresh response finishes after a switch', async () => {
    sessionStorage.setItem('token', 'account-a');
    let finish;
    api.defaults.adapter = config => config.url === '/auth/refresh' ? new Promise(resolve => {
        finish = () => resolve(response(config, { accessToken: 'old-account-a-token' }));
    }) : reject(config, 401);
    const result = Promise.allSettled([api.get('/draft')]);
    await vi.waitFor(() => expect(finish).toBeTypeOf('function'));
    const epoch = session.beginAuthentication(false); session.storeAccessToken('account-b', epoch);
    finish();
    expect((await result)[0].reason.code).toBe('ERR_CANCELED');
    expect(session.getAccessToken()).toBe('account-b');
});

it('rejects successful data from the previous account before consumers can read it', async () => {
    sessionStorage.setItem('token', 'account-a');
    let finish;
    api.defaults.adapter = config => new Promise(resolve => { finish = () => resolve(response(config, { private: 'account-a' })); });
    const result = Promise.allSettled([api.get('/profile')]);
    await vi.waitFor(() => expect(finish).toBeTypeOf('function'));
    const epoch = session.beginAuthentication(false); session.storeAccessToken('account-b', epoch);
    finish();
    expect((await result)[0].reason.code).toBe('ERR_CANCELED');
});

it('does not silently adopt another tabs refresh-cookie identity', async () => {
    const token = sid => `test.${btoa(JSON.stringify({ sid }))}.signature`;
    sessionStorage.setItem('token', token('session-a'));
    api.defaults.adapter = config => config.url === '/auth/refresh'
        ? Promise.resolve(response(config, { accessToken: token('session-b') })) : reject(config, 401);
    await expect(api.get('/draft')).rejects.toMatchObject({ code: 'ERR_CANCELED' });
    expect(session.getAccessToken()).toBeNull();
});

it.each([null, 'undefined', {}, 42])('preserves credentials on malformed refresh data %s', async accessToken => {
    sessionStorage.setItem('token', 'old-token');
    api.defaults.adapter = config => config.url === '/auth/refresh'
        ? Promise.resolve(response(config, { accessToken })) : reject(config, 401);
    await expect(api.get('/draft')).rejects.toThrow();
    expect(session.getAccessToken()).toBe('old-token');
});

it('shares one refresh across concurrent requests and rejects both without redirect on network failure', async () => {
    sessionStorage.setItem('token', 'old-test-token');
    let failRefresh;
    api.defaults.adapter = vi.fn(config => config.url === '/auth/refresh'
        ? new Promise((_, fail) => { failRefresh = () => fail(Object.assign(new Error('offline'), { config })); })
        : reject(config, 401));
    const requests = Promise.allSettled([api.get('/first'), api.get('/second')]);
    await vi.waitFor(() => expect(failRefresh).toBeTypeOf('function'));
    failRefresh();
    expect((await requests).map(result => result.status)).toEqual(['rejected', 'rejected']);
    expect(api.defaults.adapter.mock.calls.filter(([config]) => config.url === '/auth/refresh')).toHaveLength(1);
    expect(sessionStorage.getItem('token')).toBe('old-test-token');
    expect(browser.location.href).toBe('https://localhost/settings');
    expect(browser.dispatchEvent).toHaveBeenCalledTimes(1);
});
