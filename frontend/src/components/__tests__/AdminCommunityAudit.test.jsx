import { act, cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import UserManagement from '../admin/UserManagement';
import SystemLogs from '../admin/SystemLogs';
import ChallengeHistory from '../ChallengeHistory';
import AdminDashboard from '../../pages/AdminDashboard';
import { adminApi } from '../../services/adminApi';
import api from '../../services/api';

const notifications = vi.hoisted(() => ({ totalHistory: 0 }));
vi.mock('../../context/AuthContext', () => ({ useAuth: () => ({ user: { userId: 'self', name: 'Admin' } }) }));
vi.mock('../../context/NotificationContext', () => ({ useNotifications: () => ({ notifications }) }));
vi.mock('../../services/adminApi', () => ({ adminApi: {
    getAllUsers: vi.fn(), updateUserRole: vi.fn(), toggleUserStatus: vi.fn(), getSystemLogs: vi.fn(),
    getAllTopics: vi.fn(), getAllAchievements: vi.fn(), getAllClassrooms: vi.fn(),
} }));
vi.mock('../../services/api', () => ({ default: { get: vi.fn() } }));

const users = [
    { userId: 'self', name: 'Admin', email: 'admin@example.test', role: 'ADMIN', active: true },
    { userId: 'student', name: 'Anna', email: 'anna@example.test', role: 'STUDENT', active: true },
];
const history = [{ challengeId: 'duel', challengerName: 'Admin', opponentName: 'Anna',
    lessonTitle: 'Gyümölcsök', difficulty: 'MEDIUM', status: 'COMPLETED', winnerName: 'Admin', language: 'en' }];

beforeEach(() => {
    vi.resetAllMocks();
    notifications.totalHistory = 0;
    adminApi.getAllUsers.mockResolvedValue({ data: structuredClone(users) });
    adminApi.updateUserRole.mockResolvedValue({});
    adminApi.toggleUserStatus.mockResolvedValue({});
    adminApi.getSystemLogs.mockResolvedValue({ data: [] });
    adminApi.getAllTopics.mockResolvedValue({ data: [] });
    adminApi.getAllAchievements.mockResolvedValue({ data: [] });
    adminApi.getAllClassrooms.mockResolvedValue({ data: [] });
    api.get.mockResolvedValue({ data: structuredClone(history) });
});
afterEach(() => { cleanup(); vi.restoreAllMocks(); });

const roleName = 'Jogosultság: Anna (anna@example.test)';
const suspendName = 'Felfüggesztés: Anna (anna@example.test)';

describe('admin user operations preserve the working context', () => {
    it('ignores empty, unsupported and unchanged role selections without sending a request', async () => {
        render(<UserManagement />);
        const select = await screen.findByRole('combobox', { name: roleName });
        select.focus();
        for (const value of ['', 'DELETED', 'STUDENT']) {
            fireEvent.change(select, { target: { value } });
            expect(select).toHaveValue('STUDENT');
        }
        expect(adminApi.updateUserRole).not.toHaveBeenCalled();
        expect(select).toHaveFocus();
        expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    });

    it('keeps the selected row and focus during role updates, blocks duplicate/conflicting writes, and retains success', async () => {
        let finish;
        adminApi.updateUserRole.mockImplementation(() => new Promise(resolve => { finish = resolve; }));
        render(<UserManagement />);
        const select = await screen.findByRole('combobox', { name: roleName });
        const table = screen.getByRole('table');
        const confirm = vi.spyOn(window, 'confirm').mockReturnValue(true);
        select.focus();
        fireEvent.change(select, { target: { value: 'TEACHER' } });
        fireEvent.change(select, { target: { value: 'ADMIN' } });
        fireEvent.click(screen.getByRole('button', { name: suspendName }));
        fireEvent.click(screen.getByRole('button', { name: 'Felhasználók újratöltése' }));
        expect(adminApi.updateUserRole).toHaveBeenCalledExactlyOnceWith('student', 'TEACHER');
        expect(adminApi.toggleUserStatus).not.toHaveBeenCalled();
        expect(confirm).not.toHaveBeenCalled();
        expect(select).toHaveFocus();
        expect(select).toHaveAttribute('aria-busy', 'true');
        expect(table).toBeInTheDocument();
        await act(async () => finish({}));
        expect(select).toHaveValue('TEACHER');
        expect(select).toHaveFocus();
        expect(select).toHaveAttribute('aria-disabled', 'false');
        expect(screen.getByText('Jogosultság sikeresen frissítve!').closest('[role="status"]')).toBeInTheDocument();
        expect(adminApi.getAllUsers).toHaveBeenCalledTimes(1);
        expect(screen.getByRole('table')).toBe(table);
    });

    it('retains the prior role and focus after failure, then permits correction/retry', async () => {
        adminApi.updateUserRole.mockRejectedValueOnce(new Error('offline'));
        render(<UserManagement />);
        const select = await screen.findByRole('combobox', { name: roleName });
        select.focus();
        fireEvent.change(select, { target: { value: 'TEACHER' } });
        expect(await screen.findByRole('alert')).toHaveTextContent('Hiba a jogosultság módosításakor.');
        expect(select).toHaveValue('STUDENT');
        expect(select).toHaveFocus();
        fireEvent.change(select, { target: { value: 'TEACHER' } });
        await screen.findByText('Jogosultság sikeresen frissítve!');
        expect(select).toHaveValue('TEACHER');
        expect(select).toHaveFocus();
        expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    });

    it('updates account status in place and retains the same action button and success message', async () => {
        vi.spyOn(window, 'confirm').mockReturnValue(true);
        let finish;
        adminApi.toggleUserStatus.mockImplementation(() => new Promise(resolve => { finish = resolve; }));
        render(<UserManagement />);
        const action = await screen.findByRole('button', { name: suspendName });
        action.focus();
        fireEvent.click(action);
        fireEvent.click(action);
        expect(adminApi.toggleUserStatus).toHaveBeenCalledExactlyOnceWith('student', false);
        expect(action).toHaveFocus();
        await act(async () => finish({}));
        const restoredAction = screen.getByRole('button', { name: 'Visszaállítás: Anna (anna@example.test)' });
        expect(restoredAction).toBe(action);
        expect(restoredAction).toHaveFocus();
        expect(screen.getByText('Felhasználó sikeresen felfüggesztve.').closest('[role="status"]')).toBeInTheDocument();
        expect(adminApi.getAllUsers).toHaveBeenCalledTimes(1);
    });

    it('keeps a failed status action available without changing the account', async () => {
        vi.spyOn(window, 'confirm').mockReturnValue(true);
        adminApi.toggleUserStatus.mockRejectedValueOnce(new Error('offline'));
        render(<UserManagement />);
        const action = await screen.findByRole('button', { name: suspendName });
        action.focus();
        fireEvent.click(action);
        expect(await screen.findByRole('alert')).toHaveTextContent('Hiba a státusz módosításakor.');
        expect(action).toHaveAccessibleName(suspendName);
        expect(action).toHaveFocus();
        expect(action).toHaveAttribute('aria-disabled', 'false');
    });

    it('does not modify self or cancelled accounts and explains the self restriction', async () => {
        vi.spyOn(window, 'confirm').mockReturnValue(false);
        render(<UserManagement />);
        const ownRole = await screen.findByRole('combobox', { name: 'Jogosultság: Admin (admin@example.test)' });
        expect(ownRole).toBeDisabled();
        expect(ownRole).toHaveAccessibleDescription(/Saját fiókod/);
        fireEvent.change(ownRole, { target: { value: 'STUDENT' } });
        fireEvent.click(screen.getByRole('button', { name: suspendName }));
        expect(adminApi.updateUserRole).not.toHaveBeenCalled();
        expect(adminApi.toggleUserStatus).not.toHaveBeenCalled();
    });

    it('offers an in-place keyboard retry after initial loading fails', async () => {
        vi.spyOn(console, 'error').mockImplementation(() => {});
        let finish;
        adminApi.getAllUsers.mockReset().mockRejectedValueOnce(new Error('offline'))
            .mockImplementationOnce(() => new Promise(resolve => { finish = resolve; }));
        render(<UserManagement />);
        await screen.findByRole('alert');
        const retry = screen.getByRole('button', { name: 'Felhasználók újratöltése' });
        const table = screen.getByRole('table');
        retry.focus();
        fireEvent.click(retry);
        fireEvent.click(retry);
        expect(retry).toHaveFocus();
        expect(retry).toHaveAttribute('aria-busy', 'true');
        expect(screen.getByRole('table')).toBe(table);
        await act(async () => finish({ data: structuredClone(users) }));
        expect(retry).toHaveFocus();
        expect(adminApi.getAllUsers).toHaveBeenCalledTimes(2);
        expect(screen.queryByRole('alert')).not.toBeInTheDocument();
        expect(screen.getByRole('combobox', { name: roleName })).toBeInTheDocument();
    });

    it('announces filtered counts without moving focus and exposes named table relationships', async () => {
        render(<UserManagement />);
        const search = await screen.findByRole('textbox', { name: 'Felhasználók keresése név vagy e-mail alapján' });
        const table = screen.getByRole('table', { name: 'Felhasználók, fiókstátuszok és jogosultságok kezelése' });
        expect(within(table).getAllByRole('columnheader')).toHaveLength(5);
        within(table).getAllByRole('columnheader').forEach(header => expect(header).toHaveAttribute('scope', 'col'));
        expect(within(table).getAllByRole('rowheader')).toHaveLength(2);
        expect(screen.getByRole('region')).toHaveAttribute('tabindex', '0');
        search.focus();
        fireEvent.change(search, { target: { value: 'anna@' } });
        expect(screen.getByText('Összes: 1 fő')).toHaveAttribute('role', 'status');
        fireEvent.change(search, { target: { value: 'nincs-talalat' } });
        expect(screen.getByText('Összes: 0 fő')).toHaveAttribute('aria-atomic', 'true');
        expect(screen.getByText('Nincs találat.')).toBeInTheDocument();
        expect(search).toHaveFocus();
    });
});

describe('community background updates and log semantics', () => {
    it('navigates the real vertical admin tabs with arrow keys and connects each tab to its panel', async () => {
        const { container } = render(<AdminDashboard />);
        await screen.findByRole('combobox', { name: roleName });
        const tabs = screen.getByRole('tablist', { name: 'Adminisztrációs funkciók' });
        expect(tabs).toHaveAttribute('aria-orientation', 'vertical');
        const names = ['Felhasználók', 'Tananyag CMS', 'Kitüntetések', 'Osztálytermek', 'Rendszernapló'];
        let active = within(tabs).getByRole('tab', { name: names[0] });
        active.focus();
        for (const name of names.slice(1)) {
            fireEvent.keyDown(active, { key: 'ArrowDown', code: 'ArrowDown' });
            active = within(tabs).getByRole('tab', { name });
            await waitFor(() => expect(active).toHaveAttribute('aria-selected', 'true'));
            expect(active).toHaveFocus();
            const panel = document.getElementById(active.getAttribute('aria-controls'));
            expect(panel).toHaveAttribute('role', 'tabpanel');
            expect(panel).toHaveAttribute('aria-labelledby', active.id);
        }
        const ids = [...container.querySelectorAll('[id]')].map(node => node.id);
        expect(new Set(ids).size).toBe(ids.length);
        expect(container.querySelector('button button, button a, a button')).toBeNull();
        for (const node of container.querySelectorAll('[aria-labelledby], [aria-describedby], [aria-controls]')) {
            for (const attribute of ['aria-labelledby', 'aria-describedby', 'aria-controls']) {
                for (const id of (node.getAttribute(attribute) || '').split(/\s+/).filter(Boolean)) {
                    expect(document.getElementById(id), `${attribute}: ${id}`).not.toBeNull();
                }
            }
        }
    });
    it('keeps the history content mounted during notification refreshes', async () => {
        let finish;
        api.get.mockResolvedValueOnce({ data: structuredClone(history) })
            .mockImplementationOnce(() => new Promise(resolve => { finish = resolve; }));
        const { rerender } = render(<MemoryRouter><ChallengeHistory /></MemoryRouter>);
        const opponent = await screen.findByRole('heading', { name: 'Ellenfél: Anna' });
        notifications.totalHistory = 1;
        rerender(<MemoryRouter><ChallengeHistory /></MemoryRouter>);
        expect(opponent).toBeInTheDocument();
        expect(screen.queryByText('Előzmények betöltése…')).not.toBeInTheDocument();
        await act(async () => finish({ data: [{ ...history[0], opponentName: 'Béla' }] }));
        expect(screen.getByRole('heading', { name: 'Ellenfél: Béla' })).toBe(opponent);
    });

    it('ignores a delayed history response after a more recent notification response', async () => {
        let finishOld;
        api.get.mockResolvedValueOnce({ data: structuredClone(history) })
            .mockImplementationOnce(() => new Promise(resolve => { finishOld = resolve; }))
            .mockResolvedValueOnce({ data: [{ ...history[0], opponentName: 'Friss eredmény' }] });
        const { rerender } = render(<MemoryRouter><ChallengeHistory /></MemoryRouter>);
        await screen.findByRole('heading', { name: 'Ellenfél: Anna' });
        notifications.totalHistory = 1;
        rerender(<MemoryRouter><ChallengeHistory /></MemoryRouter>);
        notifications.totalHistory = 2;
        rerender(<MemoryRouter><ChallengeHistory /></MemoryRouter>);
        await screen.findByRole('heading', { name: 'Ellenfél: Friss eredmény' });
        await act(async () => finishOld({ data: [{ ...history[0], opponentName: 'Elavult eredmény' }] }));
        expect(screen.queryByText('Elavult eredmény')).not.toBeInTheDocument();
        expect(screen.getByRole('heading', { name: 'Ellenfél: Friss eredmény' })).toBeInTheDocument();
    });

    it('keeps textual log meanings while hiding decorative emoji and identifying table headers', async () => {
        adminApi.getSystemLogs.mockResolvedValue({ data: [{ logId: 'one', actionType: 'USER_BANNED', details: 'Tesztművelet' }] });
        render(<SystemLogs />);
        const table = await screen.findByRole('table', { name: 'Adminisztrációs események időpontja, végrehajtója és részletei' });
        expect(screen.getByText('FELFÜGGESZTÉS')).toBeInTheDocument();
        expect(screen.getByText('🛑')).toHaveAttribute('aria-hidden', 'true');
        within(table).getAllByRole('columnheader').forEach(header => expect(header).toHaveAttribute('scope', 'col'));
    });
});
