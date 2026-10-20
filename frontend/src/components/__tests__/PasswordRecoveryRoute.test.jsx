import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import App from '../../App';
import { useAuth } from '../../context/AuthContext';

vi.mock('../../context/AuthContext', () => ({
    AuthProvider: ({ children }) => children,
    useAuth: vi.fn(),
}));
vi.mock('../../context/NotificationContext', () => ({
    NotificationProvider: ({ children }) => children,
    useNotifications: () => ({ notifications: {} }),
}));
vi.mock('../../context/ThemeContext', () => ({
    ThemeProvider: ({ children }) => children,
    useTheme: () => ({ theme: 'light' }),
}));
vi.mock('../NavigationBar', () => ({ default: () => null }));
vi.mock('../GlobalErrorToast', () => ({ default: () => null }));
vi.mock('../../pages/Dashboard', () => ({ default: () => <h1>Dashboard</h1> }));

afterEach(() => {
    cleanup();
    localStorage.clear();
    window.history.replaceState({}, '', '/');
});

describe('password recovery through the application router', () => {
    it.each([false, true])('opens the token link when authenticated=%s', async authenticated => {
        useAuth.mockReturnValue({ user: authenticated ? { userId: 'self', verified: true } : null });
        if (authenticated) localStorage.setItem('token', 'existing-test-session');
        window.history.replaceState({}, '', '/reset-password?token=recovery-test-token');
        render(<App />);
        expect(await screen.findByRole('heading', { name: 'Új jelszó beállítása' })).toBeInTheDocument();
        expect(screen.getByLabelText('Új jelszó', { exact: true })).toBeInTheDocument();
        expect(window.location.search).toBe('?token=recovery-test-token');
        expect(screen.queryByRole('heading', { name: 'Dashboard' })).not.toBeInTheDocument();
    });

    it('still redirects an authenticated user away from the login page', async () => {
        useAuth.mockReturnValue({ user: { userId: 'self', verified: true } });
        window.history.replaceState({}, '', '/login');
        render(<App />);
        expect(await screen.findByRole('heading', { name: 'Dashboard' })).toBeInTheDocument();
    });
});
