import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import StudentClassrooms from '../StudentClassrooms';
import UserManagement from '../admin/UserManagement';
import ForgotPassword from '../../pages/ForgotPassword';
import ResetPassword from '../../pages/ResetPassword';
import { classroomApi } from '../../services/classroomApi';
import { adminApi } from '../../services/adminApi';
import { authApi } from '../../services/authApi';

vi.mock('../../services/classroomApi', () => ({ classroomApi: { getStudentClassrooms: vi.fn(), joinClassroom: vi.fn() } }));
vi.mock('../../services/adminApi', () => ({ adminApi: { getAllUsers: vi.fn(), updateUserRole: vi.fn(), toggleUserStatus: vi.fn() } }));
vi.mock('../../services/authApi', () => ({ authApi: { forgotPassword: vi.fn(), resetPassword: vi.fn() } }));
vi.mock('../../context/AuthContext', () => ({ useAuth: () => ({ user: { userId: 'self' } }) }));
vi.mock('../../context/NotificationContext', () => ({ useNotifications: () => ({ notifications: {} }) }));

beforeEach(() => {
    vi.resetAllMocks();
    classroomApi.getStudentClassrooms.mockResolvedValue({ data: [{ classroomId: 'room', name: 'Angol csoport', courseCode: 'en' }] });
});
afterEach(() => { cleanup(); vi.useRealTimers(); });

describe('form and classroom accessibility', () => {
    it('offers a keyboard link to the student classroom and labels its invite field', async () => {
        render(<MemoryRouter><StudentClassrooms /></MemoryRouter>);
        expect(await screen.findByRole('link', { name: 'Angol csoport' })).toHaveAttribute('href', '/classrooms/room');
        expect(screen.getByRole('textbox', { name: 'Meghívókód' })).toHaveAccessibleDescription(/8 karakteres meghívókódot/);
    });

    it('retains focus during a join request, prevents duplicates and announces success', async () => {
        let resolveJoin;
        classroomApi.joinClassroom.mockImplementation(() => new Promise(resolve => { resolveJoin = resolve; }));
        render(<MemoryRouter><StudentClassrooms /></MemoryRouter>);
        const field = await screen.findByRole('textbox', { name: 'Meghívókód' });
        fireEvent.change(field, { target: { value: 'ABC12345' } });
        const submit = screen.getByRole('button', { name: 'Kérelem Küldése' });
        submit.focus();
        fireEvent.click(submit);
        expect(submit).toHaveFocus();
        expect(submit).toHaveAttribute('aria-disabled', 'true');
        expect(field).toHaveAttribute('readonly');
        fireEvent.click(submit);
        expect(classroomApi.joinClassroom).toHaveBeenCalledTimes(1);
        await act(async () => resolveJoin({}));
        expect(screen.getByRole('status')).toHaveTextContent('Csatlakozási kérelem elküldve!');
        expect(field).toHaveValue('');
        expect(submit).toHaveFocus();
    });

    it('announces join failure and retains the invite code for correction', async () => {
        classroomApi.joinClassroom.mockRejectedValue({ response: { data: { message: 'Érvénytelen kód.' } } });
        render(<MemoryRouter><StudentClassrooms /></MemoryRouter>);
        const field = await screen.findByRole('textbox', { name: 'Meghívókód' });
        fireEvent.change(field, { target: { value: 'BAD12345' } });
        fireEvent.click(screen.getByRole('button', { name: 'Kérelem Küldése' }));
        await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent('Érvénytelen kód.'));
        expect(field).toHaveValue('BAD12345');
        expect(field).not.toHaveAttribute('readonly');
    });

    it('identifies which user a role or account action belongs to without changing permissions', async () => {
        adminApi.getAllUsers.mockResolvedValue({ data: [
            { userId: 'self', name: 'Admin', email: 'admin@example.test', role: 'ADMIN', active: true },
            { userId: 'other', name: 'Diák', email: 'student@example.test', role: 'STUDENT', active: true },
        ] });
        render(<UserManagement />);
        expect(await screen.findByRole('combobox', { name: 'Jogosultság: Admin (admin@example.test)' })).toBeDisabled();
        expect(screen.getByRole('combobox', { name: 'Jogosultság: Diák (student@example.test)' })).toBeEnabled();
        expect(screen.getByRole('button', { name: 'Felfüggesztés: Diák (student@example.test)' })).toBeEnabled();
        fireEvent.change(screen.getByRole('textbox', { name: 'Felhasználók keresése név vagy e-mail alapján' }), { target: { value: 'student@' } });
        expect(screen.queryByRole('combobox', { name: /Jogosultság: Admin/ })).not.toBeInTheDocument();
        expect(adminApi.updateUserRole).not.toHaveBeenCalled();
        expect(adminApi.toggleUserStatus).not.toHaveBeenCalled();
    });

    it('announces a password-link response and leaves focus on meaningful content', async () => {
        authApi.forgotPassword.mockResolvedValue({ data: 'Ha létezik a fiók, elküldtük a linket.' });
        render(<MemoryRouter><ForgotPassword /></MemoryRouter>);
        fireEvent.change(screen.getByRole('textbox', { name: 'Email cím' }), { target: { value: 'student@example.test' } });
        fireEvent.click(screen.getByRole('button', { name: 'Visszaállító link küldése' }));
        await waitFor(() => expect(screen.getByRole('status')).toHaveTextContent('elküldtük a linket'));
        expect(screen.getByRole('heading', { level: 1 })).toHaveFocus();
        expect(screen.getByRole('link', { name: 'Vissza a belépéshez' })).toBeInTheDocument();
    });

    it('allows a deliberate new reset request without retyping the email or sending automatically', async () => {
        authApi.forgotPassword.mockResolvedValue({ data: 'Ha létezik a fiók, elküldtük a linket.' });
        render(<MemoryRouter><ForgotPassword /></MemoryRouter>);
        const email = screen.getByRole('textbox', { name: 'Email cím' });
        expect(email).toHaveAccessibleDescription(/1 óráig érvényes/);
        fireEvent.change(email, { target: { value: 'student@example.test' } });
        fireEvent.click(screen.getByRole('button', { name: 'Visszaállító link küldése' }));
        fireEvent.click(await screen.findByRole('button', { name: 'Új visszaállító link kérése' }));
        expect(screen.getByRole('textbox', { name: 'Email cím' })).toHaveValue('student@example.test');
        expect(authApi.forgotPassword).toHaveBeenCalledTimes(1);
        await waitFor(() => expect(screen.getByRole('heading', { level: 1 })).toHaveFocus());
        fireEvent.click(screen.getByRole('button', { name: 'Visszaállító link küldése' }));
        await screen.findByRole('button', { name: 'Új visszaállító link kérése' });
        expect(authApi.forgotPassword.mock.calls).toEqual([['student@example.test'], ['student@example.test']]);
    });

    it('announces mismatched passwords without transmitting them', async () => {
        render(<MemoryRouter initialEntries={['/reset-password?token=test']}><ResetPassword /></MemoryRouter>);
        fireEvent.change(screen.getByLabelText('Új jelszó', { exact: true }), { target: { value: 'password-one' } });
        fireEvent.change(screen.getByLabelText('Új jelszó megerősítése'), { target: { value: 'password-two' } });
        fireEvent.click(screen.getByRole('button', { name: 'Jelszó mentése' }));
        expect(screen.getByRole('alert')).toHaveTextContent('A két jelszó nem egyezik!');
        expect(authApi.resetPassword).not.toHaveBeenCalled();
    });

    it('keeps reset success visible until the user explicitly returns to login', async () => {
        authApi.resetPassword.mockResolvedValue({ data: 'Jelszó sikeresen frissítve!' });
        render(<MemoryRouter initialEntries={['/reset-password?token=test']}><Routes>
            <Route path="/reset-password" element={<ResetPassword />} />
            <Route path="/login" element={<h1>Bejelentkezés</h1>} />
        </Routes></MemoryRouter>);
        for (const label of ['Új jelszó', 'Új jelszó megerősítése']) {
            const field = screen.getByLabelText(label, { exact: true });
            expect(field).toHaveAttribute('autocomplete', 'new-password');
            fireEvent.change(field, { target: { value: 'example-password' } });
        }
        fireEvent.click(screen.getByRole('button', { name: 'Jelszó mentése' }));
        await waitFor(() => expect(screen.getByRole('status')).toHaveTextContent('Jelszó sikeresen frissítve!'));
        vi.useFakeTimers();
        act(() => vi.advanceTimersByTime(30_000));
        expect(screen.getByRole('status')).toHaveTextContent('Jelszó sikeresen frissítve!');
        vi.useRealTimers();
        fireEvent.click(screen.getByRole('link', { name: 'Vissza a belépéshez' }));
        expect(screen.getByRole('heading', { name: 'Bejelentkezés' })).toBeInTheDocument();
    });
});
