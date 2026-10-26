import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { AuthProvider, useAuth } from './AuthContext';
import api from '../services/api';
import { endSession, getAccessToken } from '../services/authSession';
import { vocabularyApi } from '../services/vocabularyApi';

vi.mock('../services/vocabularyApi', () => ({ vocabularyApi: { getVocabularyMap: vi.fn() } }));
let auth;
const response = (config, data, status = 200) => ({ config, data, status, statusText: '', headers: {} });
const fail = (config, status) => Promise.reject(Object.assign(new Error('test failure'), { config, response: response(config, {}, status) }));
function Probe() {
    auth = useAuth();
    return <><output aria-label="Fiók">{auth.user?.userId || 'nincs'}</output>
        <output aria-label="Szókincs">{Object.keys(auth.vocabularyMap).join(',')}</output></>;
}
beforeEach(() => {
    endSession(); localStorage.clear(); sessionStorage.clear(); vi.clearAllMocks();
    vocabularyApi.getVocabularyMap.mockResolvedValue({ data: {} });
    api.defaults.adapter = config => Promise.resolve(response(config, { userId: 'me' }));
});
afterEach(() => { cleanup(); endSession(); localStorage.clear(); sessionStorage.clear(); });
async function mount() {
    render(<AuthProvider><Probe /></AuthProvider>);
    await screen.findByLabelText('Fiók');
}

it.each([true, false])('switches storage mode without leaving a token from the previous account (remember=%s)', async remember => {
    await mount();
    await act(() => auth.login('old-token', { userId: 'old' }, !remember));
    await act(() => auth.login('new-token', { userId: 'new' }, remember));
    expect((remember ? localStorage : sessionStorage).getItem('token')).toBe('new-token');
    expect((remember ? sessionStorage : localStorage).getItem('token')).toBeNull();
    expect(screen.getByLabelText('Fiók')).toHaveTextContent('new');
});

it('does not restore vocabulary after logout when a previous request completes', async () => {
    await mount();let finish;
    vocabularyApi.getVocabularyMap.mockImplementationOnce(() => new Promise(resolve => { finish = resolve; }));
    let pending;
    act(() => { pending = auth.login('old-token', { userId: 'old' }).catch(error => error.code); });
    await act(() => auth.logout(true));
    await act(async () => { finish({ data: { secret: 'old vocabulary' } }); expect(await pending).toBe('ERR_CANCELED'); });
    expect(screen.getByLabelText('Fiók')).toHaveTextContent('nincs');
    expect(screen.getByLabelText('Szókincs')).toBeEmptyDOMElement();
    expect(getAccessToken()).toBeNull();
});

it('serializes login A, logout, then login B so older cookie responses cannot win', async () => {
    await mount();const calls=[];let finishA;
    api.defaults.adapter = vi.fn(config => {
        if (config.url === '/auth/login') {
            const email=JSON.parse(config.data).email;calls.push(email);
            if (email === 'a') return new Promise(resolve => { finishA = () => resolve(response(config, { accessToken: 'token-a' })); });
            return Promise.resolve(response(config, { accessToken: 'token-b' }));
        }
        if (config.url === '/auth/logout') { calls.push('logout');return Promise.resolve(response(config, {})); }
        return Promise.resolve(response(config, { userId: config.headers.Authorization === 'Bearer token-b' ? 'b' : 'a' }));
    });
    let a,b,logout;
    act(() => { a=auth.authenticate({ email:'a',password:'test' }).catch(error => error.code); });
    await waitFor(() => expect(finishA).toBeTypeOf('function'));
    act(() => { logout=auth.logout();b=auth.authenticate({ email:'b',password:'test',rememberMe:false }); });
    expect(getAccessToken()).toBeNull();
    await act(async () => { finishA();expect(await a).toBe('ERR_CANCELED');await logout;await b; });
    expect(calls).toEqual(['a','logout','b']);
    expect(getAccessToken()).toBe('token-b');
    expect(screen.getByLabelText('Fiók')).toHaveTextContent('b');
});

it('keeps credentials and the current route on bootstrap 500, then recovers through retry', async () => {
    sessionStorage.setItem('token','kept-token');let offline=true;
    api.defaults.adapter = config => offline ? fail(config,500) : Promise.resolve(response(config,{ userId:'me' }));
    render(<AuthProvider><Probe /></AuthProvider>);
    expect(await screen.findByRole('alert')).toHaveTextContent('munkamenetedet megőriztük');
    expect(getAccessToken()).toBe('kept-token');
    offline=false;fireEvent.click(screen.getByRole('button',{ name:'Újrapróbálás' }));
    expect(await screen.findByLabelText('Fiók')).toHaveTextContent('me');
});

it('clears React identity when a refresh is rejected instead of leaving a stale user', async () => {
    await mount();await act(() => auth.login('old-token',{ userId:'old' }));
    api.defaults.adapter = config => fail(config, config.url === '/auth/refresh' ? 400 : 401);
    await act(async () => { await expect(api.get('/draft')).rejects.toThrow(); });
    expect(screen.getByLabelText('Fiók')).toHaveTextContent('nincs');expect(getAccessToken()).toBeNull();
});
