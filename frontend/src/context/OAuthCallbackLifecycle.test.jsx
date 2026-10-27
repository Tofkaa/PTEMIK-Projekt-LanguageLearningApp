import { StrictMode } from 'react';
import { act, cleanup, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { AuthProvider, useAuth } from './AuthContext';
import { AchievementFeedbackProvider } from './AchievementFeedbackContext';
import OAuth2RedirectHandler from '../components/OAuth2RedirectHandler';
import api from '../services/api';
import { endSession, getAccessToken } from '../services/authSession';
import { vocabularyApi } from '../services/vocabularyApi';
import { rememberInvitation } from '../utils/invitationIntent';

vi.mock('./NotificationContext', () => ({ useNotifications: () => ({ autoRefreshEnabled: false }) }));
vi.mock('../services/vocabularyApi', () => ({ vocabularyApi: { getVocabularyMap: vi.fn() } }));
const response = (config, data) => ({ config, data, status: 200, statusText: '', headers: {} });
function Destination({ name }) {
    const { user } = useAuth();
    return <><h1>{name}</h1><output aria-label="Account">{user?.userId}</output></>;
}
function Tree() {
    return <StrictMode><AuthProvider><MemoryRouter initialEntries={['/oauth2/redirect?token=synthetic-callback-token']}>
        <AchievementFeedbackProvider><Routes>
            <Route path="/oauth2/redirect" element={<OAuth2RedirectHandler />} />
            <Route path="/dashboard" element={<Destination name="Dashboard" />} />
            <Route path="/invite/classroom" element={<Destination name="Invitation" />} />
            <Route path="/login" element={<h1>Login</h1>} />
        </Routes></AchievementFeedbackProvider>
    </MemoryRouter></AuthProvider></StrictMode>;
}
beforeEach(() => {
    endSession(); localStorage.clear(); sessionStorage.clear(); vi.clearAllMocks();
    window.history.replaceState({}, '', '/oauth2/redirect');
    vocabularyApi.getVocabularyMap.mockResolvedValue({ data: {} });
});
afterEach(() => { cleanup(); endSession(); localStorage.clear(); sessionStorage.clear(); window.history.replaceState({}, '', '/'); });

it.each([false, true])('finishes OAuth once through real auth and keyed feedback providers (invitation=%s)', async invitation => {
    if (invitation) rememberInvitation({ kind: 'classroom', token: 'a'.repeat(43) });
    let finishProfile, finishVocabulary;
    const calls = [];
    api.defaults.adapter = config => {
        calls.push(config.url);
        if (calls.length > 1) return new Promise(() => {});
        return new Promise(resolve => { finishProfile = () => resolve(response(config, { userId: 'oauth-account', verified: true })); });
    };
    vocabularyApi.getVocabularyMap.mockImplementationOnce(() => new Promise(resolve => { finishVocabulary = () => resolve({ data: {} }); }));
    render(<Tree />);
    await waitFor(() => expect(finishProfile).toBeTypeOf('function'));
    await act(async () => finishProfile());
    expect(calls).toEqual(['/users/me']);
    await act(async () => finishVocabulary());
    await screen.findByRole('heading', { name: invitation ? 'Invitation' : 'Dashboard' });
    expect(screen.getByLabelText('Account')).toHaveTextContent('oauth-account');
    expect(calls).toEqual(['/users/me']);
    expect(vocabularyApi.getVocabularyMap).toHaveBeenCalledTimes(1);
    expect(getAccessToken()).toBe('synthetic-callback-token');
});
