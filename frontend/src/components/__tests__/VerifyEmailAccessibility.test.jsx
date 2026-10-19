import { StrictMode } from 'react';
import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import VerifyEmail from '../VerifyEmail';
import { authApi } from '../../services/authApi';

vi.mock('../../services/authApi', () => ({ authApi: { verifyEmail: vi.fn() } }));
beforeEach(() => vi.resetAllMocks());
afterEach(() => { cleanup(); vi.useRealTimers(); });

function showVerification(path = '/verify?token=test-only') {
    return render(<StrictMode><MemoryRouter initialEntries={[path]}><Routes>
        <Route path="/verify" element={<VerifyEmail />} />
        <Route path="/login" element={<h1>Bejelentkezés</h1>} />
    </Routes></MemoryRouter></StrictMode>);
}

it.each([
    [{ message: 'A megerősítő link lejárt.' }, 'A megerősítő link lejárt.'],
    ['Érvénytelen megerősítő kód.', 'Érvénytelen megerősítő kód.'],
    [{ message: { unexpected: true } }, 'A link lejárt vagy érvénytelen. Kérjük, próbáld újra.'],
])('renders and announces verification failure without crashing for response %j', async (data, message) => {
    authApi.verifyEmail.mockRejectedValue({ response: { data } });
    showVerification();
    await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent(message));
    expect(screen.getByRole('heading', { level: 1, name: 'Sikertelen aktiválás' })).toHaveFocus();
    expect(authApi.verifyEmail).toHaveBeenCalledExactlyOnceWith('test-only');
    fireEvent.click(screen.getByRole('button', { name: 'Vissza a bejelentkezéshez' }));
    expect(screen.getByRole('heading', { name: 'Bejelentkezés' })).toBeInTheDocument();
});

it('shows a missing-link error without making a verification request', () => {
    showVerification('/verify');
    expect(screen.getByRole('alert')).toHaveTextContent('Érvénytelen vagy hiányzó megerősítő link.');
    expect(screen.getByRole('heading', { level: 1 })).toHaveFocus();
    expect(authApi.verifyEmail).not.toHaveBeenCalled();
});

it('keeps successful activation readable until explicit navigation and focuses its heading', async () => {
    let finish;
    authApi.verifyEmail.mockImplementation(() => new Promise(resolve => { finish = resolve; }));
    showVerification();
    expect(screen.getByRole('heading', { level: 1, name: 'Fiók aktiválása...' })).toHaveFocus();
    await act(async () => finish({ data: 'Fiókodat sikeresen aktiváltuk!' }));
    expect(screen.getByRole('status')).toHaveTextContent('Fiókodat sikeresen aktiváltuk!');
    expect(screen.getByRole('heading', { level: 1, name: 'Sikeres aktiválás!' })).toHaveFocus();
    vi.useFakeTimers();
    act(() => vi.advanceTimersByTime(60_000));
    expect(screen.getByRole('status')).toHaveTextContent('Fiókodat sikeresen aktiváltuk!');
    expect(authApi.verifyEmail).toHaveBeenCalledExactlyOnceWith('test-only');
    vi.useRealTimers();
    fireEvent.click(screen.getByRole('button', { name: 'Tovább a Bejelentkezéshez' }));
    expect(screen.getByRole('heading', { name: 'Bejelentkezés' })).toBeInTheDocument();
});
