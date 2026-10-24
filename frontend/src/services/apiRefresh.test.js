import { afterEach, beforeEach, expect, it, vi } from 'vitest';

let api;
let browser;
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
});
afterEach(() => { vi.unstubAllGlobals(); localStorage.clear(); sessionStorage.clear(); });

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
    expect(browser.location.href).toBe('/login');
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
