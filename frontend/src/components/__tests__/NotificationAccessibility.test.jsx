import { useEffect } from 'react';
import { act, cleanup, fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { NotificationProvider, useNotifications } from '../../context/NotificationContext';
import AccessibilityFooter from '../AccessibilityFooter';
import api from '../../services/api';

const auth = vi.hoisted(() => ({ user: { userId: 'teacher', role: 'TEACHER' }, syncXp: vi.fn() }));
vi.mock('../../context/AuthContext', () => ({ useAuth: () => auth }));
vi.mock('../../services/api', () => ({ default: { get: vi.fn() } }));
let streams;
vi.mock('../../services/notificationStream', () => ({ openNotificationStream: ({ onConnected, onPing }) => {
    const stream = { handlers: { open: onConnected, ping: onPing }, close: vi.fn() }; streams.push(stream); return stream.close;
} }));
beforeEach(() => {
    vi.clearAllMocks();
    localStorage.clear(); sessionStorage.clear();
    sessionStorage.setItem('token', 'test-token');
    auth.user = { userId: 'teacher', role: 'TEACHER' };
    streams = [];
    api.get.mockResolvedValue({ data: { pendingFriendRequests: 2 } });
});
afterEach(() => { cleanup(); vi.useRealTimers(); vi.unstubAllGlobals(); localStorage.clear(); sessionStorage.clear(); });

function State() {
    const { notifications } = useNotifications();
    return <p>Kérelmek: {notifications.pendingFriends}</p>;
}
function Example({ path = '/dashboard' }) {
    return <MemoryRouter initialEntries={[path]}><NotificationProvider>
        <State /><AccessibilityFooter />
    </NotificationProvider></MemoryRouter>;
}
const openFooter = () => fireEvent.click(screen.getByText('Oldaltérkép és értesítési beállítások'));

describe('site index and controllable notification updates', () => {
    it('lists guest pages without exposing authenticated actions', () => {
        auth.user = null;
        render(<Example path="/login" />);
        openFooter();
        expect(screen.getByRole('navigation', { name: 'Oldaltérkép' })).toBeVisible();
        expect(screen.getByRole('link', { name: 'Regisztráció' })).toHaveAttribute('href', '/register');
        expect(screen.queryByRole('link', { name: 'Admin Panel' })).not.toBeInTheDocument();
        expect(screen.queryByRole('switch')).not.toBeInTheDocument();
    });

    it('includes admin navigation only for an admin and keeps only notification controls during exercise steps', () => {
        auth.user = { userId: 'admin', role: 'ADMIN' };
        const view = render(<Example />);
        openFooter();
        expect(screen.getByRole('link', { name: 'Admin Panel' })).toHaveAttribute('href', '/admin');
        view.unmount();
        render(<Example path="/assignment/session/session-1/play" />);
        expect(screen.queryByText('Oldaltérkép és értesítési beállítások')).not.toBeInTheDocument();
        fireEvent.click(screen.getByText('Értesítési beállítások', { exact: true }));
        expect(screen.getByRole('switch')).toBeVisible();
        expect(screen.queryByRole('navigation', { name: 'Oldaltérkép' })).not.toBeInTheDocument();
    });

    it('pauses polling, window-focus and SSE updates while preserving counts, then permits manual refresh and resume', async () => {
        render(<Example />);
        await screen.findByText('Kérelmek: 2');
        openFooter();
        const toggle = screen.getByRole('switch', { name: 'Értesítésszámlálók automatikus frissítése' });
        vi.useFakeTimers();
        fireEvent.click(toggle);
        expect(toggle).not.toBeChecked();
        expect(streams[0].close).toHaveBeenCalledTimes(1);
        expect(localStorage.getItem('notificationAutoRefresh')).toBe('paused');
        const calls = api.get.mock.calls.length;
        await act(async () => {
            vi.advanceTimersByTime(120_000);
            window.dispatchEvent(new Event('focus'));
            streams[0].handlers.ping();
        });
        expect(api.get).toHaveBeenCalledTimes(calls);
        expect(screen.getByText('Kérelmek: 2')).toBeInTheDocument();
        api.get.mockResolvedValue({ data: { pendingFriendRequests: 5 } });
        await act(async () => fireEvent.click(screen.getByRole('button', { name: 'Értesítések frissítése most' })));
        expect(screen.getByText('Kérelmek: 5')).toBeInTheDocument();
        expect(screen.getByRole('status')).toHaveTextContent('Értesítések frissítve.');
        await act(async () => fireEvent.click(toggle));
        expect(toggle).toBeChecked();
        expect(streams).toHaveLength(2);
        const resumedCalls = api.get.mock.calls.length;
        await act(async () => vi.advanceTimersByTime(60_000));
        expect(api.get).toHaveBeenCalledTimes(resumedCalls + 1);
    });

    it('ignores an in-flight automatic response after pause and never reuses another account’s counts', async () => {
        let finish;
        api.get.mockReturnValueOnce(new Promise(resolve => { finish = resolve; }));
        const view = render(<Example />);
        openFooter();
        fireEvent.click(screen.getByRole('switch'));
        await act(async () => finish({ data: { pendingFriendRequests: 99 } }));
        expect(screen.getByText('Kérelmek: 0')).toBeInTheDocument();
        api.get.mockResolvedValue({ data: { pendingFriendRequests: 7 } });
        await act(async () => fireEvent.click(screen.getByRole('button', { name: 'Értesítések frissítése most' })));
        expect(screen.getByText('Kérelmek: 7')).toBeInTheDocument();
        auth.user = { userId: 'student', role: 'STUDENT' };
        view.rerender(<Example />);
        expect(screen.getByText('Kérelmek: 0')).toBeInTheDocument();
        expect(screen.queryByRole('link', { name: 'Admin Panel' })).not.toBeInTheDocument();
    });

    it('remembers a paused preference on a fresh page and keeps a failed manual refresh retryable', async () => {
        localStorage.setItem('notificationAutoRefresh', 'paused');
        api.get.mockRejectedValueOnce(new Error('offline'));
        vi.spyOn(console, 'error').mockImplementation(() => {});
        render(<Example />);
        openFooter();
        expect(screen.getByRole('switch')).not.toBeChecked();
        expect(api.get).not.toHaveBeenCalled();
        const refresh = screen.getByRole('button', { name: 'Értesítések frissítése most' });
        await act(async () => fireEvent.click(refresh));
        expect(screen.getByRole('status')).toHaveTextContent('A frissítés nem sikerült.');
        expect(refresh).toHaveAttribute('aria-disabled', 'false');
    });
});

it('synchronizes server XP from an accepted notification snapshot', async () => {
    api.get.mockResolvedValue({ data: { pendingFriendRequests: 2, currentXp: 525 } });
    render(<Example />); await screen.findByText('Kérelmek: 2');
    expect(auth.syncXp).toHaveBeenCalledWith('teacher', 525);
});

it('rejects a late old-course summary and even a callback retained from the previous course', async () => {
    const pending=[];let retained;
    api.get.mockImplementation((url, config) => new Promise(resolve => pending.push({ config, resolve })));
    function Probe() {const value=useNotifications();useEffect(() => { retained=value.refreshNotifications; }, [value.refreshNotifications]);return <p>Due: {value.notifications.dueVocabularyCount}</p>;}
    auth.user = { userId: 'teacher', activeCourseId: 'en', activeCourseRevision: 0 };
    const view=render(<NotificationProvider><Probe /></NotificationProvider>);
    const oldRefresh=retained;
    auth.user = { ...auth.user, activeCourseId: 'de', activeCourseRevision: 1 };
    view.rerender(<NotificationProvider><Probe /></NotificationProvider>);
    expect(pending).toHaveLength(2);
    await act(async () => { await oldRefresh(); });
    expect(pending).toHaveLength(2);
    expect(pending[1].config.params.courseId).toBe('de');
    await act(() => pending[1].resolve({ data: { vocabularyCourseId: 'de', dueVocabularyCount: 3 } }));
    await act(() => pending[0].resolve({ data: { vocabularyCourseId: 'en', dueVocabularyCount: 99 } }));
    expect(screen.getByText('Due: 3')).toBeInTheDocument();
});
