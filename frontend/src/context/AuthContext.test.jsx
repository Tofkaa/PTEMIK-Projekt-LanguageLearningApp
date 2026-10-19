import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { AuthProvider, useAuth } from './AuthContext';
import { vocabularyApi } from '../services/vocabularyApi';

vi.mock('../services/api', () => ({ default: { get: vi.fn() } }));
vi.mock('../services/vocabularyApi', () => ({ vocabularyApi: { getVocabularyMap: vi.fn() } }));

beforeEach(() => {
    localStorage.clear(); sessionStorage.clear(); vi.clearAllMocks();
    vocabularyApi.getVocabularyMap.mockResolvedValue({ data: {} });
});
afterEach(() => { cleanup(); localStorage.clear(); sessionStorage.clear(); });

function ProfileExample({ rememberMe }) {
    const { user, login, updateProfile } = useAuth();
    return <>
        <button onClick={() => login('original-access-token', { name: 'Teszt', xp: 0 }, rememberMe)}>Belépés</button>
        <button onClick={() => updateProfile({ name: 'Teszt', xp: 20 })}>Eredmény frissítése</button>
        <output aria-label="XP">{user?.xp ?? 'nincs'}</output>
    </>;
}

it.each([false, true])('updates progress without rewriting or moving the current token (remember=%s)', async rememberMe => {
    render(<AuthProvider><ProfileExample rememberMe={rememberMe} /></AuthProvider>);
    fireEvent.click(await screen.findByRole('button', { name: 'Belépés' }));
    await waitFor(() => expect(vocabularyApi.getVocabularyMap).toHaveBeenCalledTimes(1));
    const storage = rememberMe ? localStorage : sessionStorage;
    const otherStorage = rememberMe ? sessionStorage : localStorage;
    expect(storage.getItem('token')).toBe('original-access-token');
    // A refresh interceptor may replace the token before a lesson's profile update.
    storage.setItem('token', 'refreshed-access-token');
    fireEvent.click(screen.getByRole('button', { name: 'Eredmény frissítése' }));
    await waitFor(() => expect(screen.getByLabelText('XP')).toHaveTextContent('20'));
    expect(storage.getItem('token')).toBe('refreshed-access-token');
    expect(otherStorage.getItem('token')).toBeNull();
    expect(vocabularyApi.getVocabularyMap).toHaveBeenCalledTimes(2);
});
