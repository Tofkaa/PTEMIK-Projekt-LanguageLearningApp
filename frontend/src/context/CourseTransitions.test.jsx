import { useEffect } from 'react';
import { act, cleanup, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { AuthProvider, useAuth } from './AuthContext';
import { vocabularyApi } from '../services/vocabularyApi';
import { userApi } from '../services/userApi';
import { endSession } from '../services/authSession';

vi.mock('../services/vocabularyApi', () => ({ vocabularyApi: { getVocabularyMap: vi.fn() } }));
vi.mock('../services/userApi', () => ({ userApi: { changeActiveCourse: vi.fn() } }));
let auth, requests;
const profile = course => ({ userId: 'student', activeCourseCode: course, xp: 0 });
function Probe() {
    const currentAuth = useAuth();
    useEffect(() => { auth = currentAuth; }, [currentAuth]);
    return <><output aria-label="course">{currentAuth.user?.activeCourseCode ?? 'none'}</output>
        <output aria-label="words">{Object.keys(currentAuth.vocabularyMap).join(',')}</output></>;
}
beforeEach(() => {
    endSession(); localStorage.clear(); sessionStorage.clear(); vi.clearAllMocks(); requests = [];
    vocabularyApi.getVocabularyMap.mockResolvedValue({ data: {} });
    userApi.changeActiveCourse.mockImplementation(course => new Promise((resolve, reject) => requests.push({ course, resolve, reject })));
});
afterEach(() => { cleanup(); endSession(); });
async function mount() {
    render(<AuthProvider><Probe /></AuthProvider>);
    await screen.findByLabelText('course');
    await act(() => auth.login('token', profile('en')));
}

it('serializes rapid switches in intent order and publishes the final course', async () => {
    await mount(); let first, second;
    act(() => { first = auth.changeLanguage('de'); second = auth.changeLanguage('es'); });
    await waitFor(() => expect(requests).toHaveLength(1));
    expect(requests[0].course).toBe('de');
    await act(async () => { requests[0].resolve({ data: profile('de') }); await first; });
    await waitFor(() => expect(requests).toHaveLength(2));
    expect(requests[1].course).toBe('es');
    await act(async () => { requests[1].resolve({ data: profile('es') }); await second; });
    expect(screen.getByLabelText('course')).toHaveTextContent('es');
});

it('retains a committed intermediate course if a later switch fails', async () => {
    await mount(); let first, second;
    act(() => { first = auth.changeLanguage('de'); second = auth.changeLanguage('es').catch(() => 'failed'); });
    await waitFor(() => expect(requests).toHaveLength(1));
    await act(async () => { requests[0].resolve({ data: profile('de') }); await first; });
    await act(async () => { requests[1].reject(new Error('unavailable')); expect(await second).toBe('failed'); });
    expect(screen.getByLabelText('course')).toHaveTextContent('de');
    // The failed queue entry must not prevent a subsequent retry.
    let retry;
    act(() => { retry = auth.changeLanguage('es'); });
    await waitFor(() => expect(requests).toHaveLength(3));
    await act(async () => { requests[2].resolve({ data: profile('es') }); await retry; });
    expect(screen.getByLabelText('course')).toHaveTextContent('es');
});

it('does not execute a queued switch in a replacement account', async () => {
    await mount(); let first, second;
    act(() => {
        first = auth.changeLanguage('de').catch(error => error.code);
        second = auth.changeLanguage('es').catch(error => error.code);
    });
    await waitFor(() => expect(requests).toHaveLength(1));
    await act(() => auth.logout(true));
    await act(() => auth.login('new-token', { ...profile('en'), userId: 'other' }));
    await act(async () => {
        requests[0].resolve({ data: profile('de') });
        expect(await first).toBe('ERR_CANCELED'); expect(await second).toBe('ERR_CANCELED');
    });
    expect(requests).toHaveLength(1);
    expect(screen.getByLabelText('course')).toHaveTextContent('en');
});

it('ignores a previous course vocabulary response even if it finishes last', async () => {
    await mount(); let finishOld, old, change;
    vocabularyApi.getVocabularyMap.mockImplementationOnce(() => new Promise(resolve => { finishOld = resolve; }));
    act(() => { old = auth.updateProfile(profile('en')); });
    vocabularyApi.getVocabularyMap.mockResolvedValue({ data: { neu: 'új' } });
    act(() => { change = auth.changeLanguage('de'); });
    await waitFor(() => expect(requests).toHaveLength(1));
    await act(async () => { requests[0].resolve({ data: profile('de') }); await change; });
    await act(async () => { finishOld({ data: { old: 'régi' } }); await old; });
    expect(screen.getByLabelText('words')).toHaveTextContent('neu');
});

it('does not switch back when a lesson returns an older profile', async () => {
    await mount(); let change;
    act(() => { change = auth.changeLanguage('de'); });
    await waitFor(() => expect(requests).toHaveLength(1));
    await act(async () => { requests[0].resolve({ data: profile('de') }); await change; });
    await act(() => auth.updateProfile({ ...profile('en'), xp: 20 }));
    expect(screen.getByLabelText('course')).toHaveTextContent('de');
});
