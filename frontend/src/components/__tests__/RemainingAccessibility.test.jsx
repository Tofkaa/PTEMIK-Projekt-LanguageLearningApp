import { act, cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import AchievementManager from '../admin/AchievementManager';
import ClassroomManager from '../admin/ClassroomManager';
import SystemLogs from '../admin/SystemLogs';
import Leaderboard from '../Leaderboard';
import ActiveChallenges from '../ActiveChallenges';
import Settings from '../../pages/Settings';
import Login from '../../pages/Login';
import OAuth2RedirectHandler from '../OAuth2RedirectHandler';
import Register from '../../pages/Register';
import AssignmentSubmissions from '../../pages/AssignmentSubmissions';
import api from '../../services/api';
import { adminApi } from '../../services/adminApi';
import { assignmentApi } from '../../services/assignmentApi';

const auth = vi.hoisted(() => ({ user: { userId: 'me', name: 'Teszt', userTag: '42', email: 'test@example.test',
    preferredDifficulty: 'DYNAMIC', authProvider: 'LOCAL' }, setUser: vi.fn(), logout: vi.fn() }));
vi.mock('../../context/AuthContext', () => ({ useAuth: () => auth }));
vi.mock('../../context/NotificationContext', () => ({ useNotifications: () => ({ notifications: {} }) }));
vi.mock('../../context/ThemeContext', () => ({ useTheme: () => ({ theme: 'light' }) }));
vi.mock('../ThemeToggle', () => ({ default: () => null }));
vi.mock('../../services/api', () => ({ default: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() } }));
vi.mock('../../services/adminApi', () => ({ adminApi: {
    getAllAchievements: vi.fn(), importAchievements: vi.fn(), toggleAchievementStatus: vi.fn(),
    getAllClassrooms: vi.fn(), toggleClassroomStatus: vi.fn(), getSystemLogs: vi.fn(),
} }));
vi.mock('../../services/assignmentApi', () => ({ assignmentApi: { getAssignmentSessions: vi.fn(), gradeSession: vi.fn() } }));

beforeEach(() => {
    vi.resetAllMocks();
    sessionStorage.clear();
    adminApi.getAllAchievements.mockResolvedValue({ data: [{ achievementId: 'ach', name: 'Első lecke', active: true }] });
    adminApi.getAllClassrooms.mockResolvedValue({ data: [{ classroomId: 'room', name: 'Angol csoport', teacherName: 'Tanár', active: true }] });
    adminApi.getSystemLogs.mockResolvedValue({ data: [] });
    api.get.mockResolvedValue({ data: [] });
    api.put.mockResolvedValue({ data: { name: 'Új név', userTag: '99' } });
    api.post.mockResolvedValue({ data: {} });
    auth.logout.mockResolvedValue();
});
afterEach(() => { cleanup(); sessionStorage.clear(); vi.useRealTimers(); vi.restoreAllMocks(); });

describe('remaining admin operations', () => {
    it('labels achievement imports and preserves focus during a single status update', async () => {
        vi.spyOn(window, 'confirm').mockReturnValue(true);
        let finish;
        adminApi.toggleAchievementStatus.mockImplementation(() => new Promise(resolve => { finish = resolve; }));
        render(<AchievementManager />);
        expect(screen.getByLabelText('Kitüntetések JSON-fájlja')).toHaveAttribute('type', 'file');
        const button = await screen.findByRole('button', { name: 'Felfüggesztés: Első lecke' });
        button.focus();
        fireEvent.click(button);
        fireEvent.click(button);
        expect(adminApi.toggleAchievementStatus).toHaveBeenCalledExactlyOnceWith('ach', false);
        expect(button).toHaveFocus();
        await act(async () => finish({}));
        expect(button).toHaveAccessibleName('Visszaállítás: Első lecke');
        expect(button).toHaveFocus();
        expect(screen.getByRole('status')).toHaveTextContent('Kitüntetés státusza frissítve!');
        expect(screen.getByText('Felfüggesztve')).toBeInTheDocument();
    });

    it('keeps an invalid achievement import available for correction and retry', async () => {
        adminApi.importAchievements.mockRejectedValue({ response: { data: { message: 'Hiányzik a kitüntetés neve.' } } });
        render(<AchievementManager />);
        await screen.findByRole('button', { name: 'Felfüggesztés: Első lecke' });
        fireEvent.change(screen.getByLabelText('Kitüntetések JSON-fájlja'), { target: { files: [new File(['[]'], 'ach.json', { type: 'application/json' })] } });
        const submit = screen.getByRole('button', { name: 'Kitüntetések Feltöltése' });
        submit.focus();
        fireEvent.click(submit);
        await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent('Hiányzik a kitüntetés neve.'));
        expect(submit).toHaveFocus();
        expect(submit).toHaveAttribute('aria-disabled', 'false');
    });

    it('updates the classroom in place so the action keeps focus and identifies its target', async () => {
        vi.spyOn(window, 'confirm').mockReturnValue(true);
        adminApi.toggleClassroomStatus.mockResolvedValue({});
        render(<ClassroomManager />);
        const action = await screen.findByRole('button', { name: 'Letiltás: Angol csoport, Tanár' });
        action.focus();
        fireEvent.click(action);
        await waitFor(() => expect(action).toHaveAccessibleName('Visszaállítás: Angol csoport, Tanár'));
        expect(action).toHaveFocus();
        expect(adminApi.getAllClassrooms).toHaveBeenCalledTimes(1);
        expect(screen.getByRole('status')).toHaveTextContent('Státusz sikeresen frissítve.');
    });

    it('leaves a classroom unchanged when confirmation is cancelled', async () => {
        vi.spyOn(window, 'confirm').mockReturnValue(false);
        render(<ClassroomManager />);
        fireEvent.click(await screen.findByRole('button', { name: 'Letiltás: Angol csoport, Tanár' }));
        expect(adminApi.toggleClassroomStatus).not.toHaveBeenCalled();
    });

    it('keeps the keyboard-scrollable log region mounted during a background refresh', async () => {
        let finish;
        adminApi.getSystemLogs.mockResolvedValueOnce({ data: [] })
            .mockImplementationOnce(() => new Promise(resolve => { finish = resolve; }));
        render(<SystemLogs />);
        const region = await screen.findByRole('region', { name: 'Rendszernapló, görgethető táblázat' });
        region.focus();
        fireEvent(window, new Event('adminActionOccurred'));
        expect(region).toBeInTheDocument();
        expect(region).toHaveFocus();
        await act(async () => finish({ data: [] }));
        expect(region).toHaveFocus();
    });
});

describe('community ranking and challenge navigation', () => {
    it('provides a keyboard-scrollable leaderboard with explicit row and column headers', async () => {
        api.get.mockResolvedValue({ data: [{ userId: 'me', name: 'Teszt', userTag: '42', xp: 1000, rank: 1 }] });
        render(<Leaderboard />);
        const region = await screen.findByRole('region', { name: 'Globális ranglista, XP szerint, görgethető táblázat' });
        expect(region).toHaveAttribute('tabindex', '0');
        region.focus();
        expect(region).toHaveFocus();
        for (const header of within(region).getAllByRole('columnheader')) expect(header).toHaveAttribute('scope', 'col');
        expect(within(region).getByRole('rowheader', { name: /Teszt/ })).toHaveAttribute('scope', 'row');
        expect(within(region).getByText('11. szint')).toHaveClass('leaderboard-level');
    });

    it('gives independent names and radio groups to multiple leaderboard instances', async () => {
        api.get.mockResolvedValue({ data: [{ userId: 'me', name: 'Teszt', userTag: '42', xp: 80, rank: 1, streak: 3 }] });
        render(<><Leaderboard /><Leaderboard defaultScope="friends" /></>);
        const scopes = await screen.findAllByRole('group', { name: 'Ranglista köre' });
        expect(scopes).toHaveLength(2);
        const globalOne = within(scopes[0]).getByRole('radio', { name: 'Globális' });
        const globalTwo = within(scopes[1]).getByRole('radio', { name: 'Globális' });
        expect(globalOne.id).not.toBe(globalTwo.id);
        expect(globalOne.name).not.toBe(globalTwo.name);
        fireEvent.click(within(scopes[0]).getByRole('radio', { name: 'Barátok' }));
        expect(within(scopes[1]).getByRole('radio', { name: 'Barátok' })).toBeChecked();
        await waitFor(() => expect(screen.getAllByText('1. hely')).toHaveLength(2));
    });

    it('ignores a late response for an earlier leaderboard sort', async () => {
        let finishOld;
        api.get.mockImplementationOnce(() => new Promise(resolve => { finishOld = resolve; }))
            .mockResolvedValueOnce({ data: [{ userId: 'new', name: 'Friss ranglista', rank: 1, streak: 8 }] });
        render(<Leaderboard />);
        fireEvent.click(screen.getByRole('radio', { name: 'Streak' }));
        await screen.findByText('Friss ranglista');
        await act(async () => finishOld({ data: [{ userId: 'old', name: 'Régi ranglista', rank: 1 }] }));
        expect(screen.getByText('Friss ranglista')).toBeInTheDocument();
        expect(screen.queryByText('Régi ranglista')).not.toBeInTheDocument();
    });

    it('names the challenge opponent and lesson before navigating to the correct duel', async () => {
        api.get.mockResolvedValue({ data: [{ challengeId: 'duel', lessonId: 'lesson', lessonTitle: 'Ételek',
            challengerName: 'Másik', opponentName: 'Teszt', isMyTurn: true }] });
        render(<MemoryRouter><Routes>
            <Route path="/" element={<ActiveChallenges />} />
            <Route path="/lesson/lesson" element={<h1>Leckelejátszó</h1>} />
        </Routes></MemoryRouter>);
        fireEvent.click(await screen.findByRole('button', { name: 'Játék! Másik ellen, Ételek' }));
        expect(await screen.findByRole('heading', { name: 'Leckelejátszó' })).toBeInTheDocument();
        expect(api.post).not.toHaveBeenCalled();
    });
});

describe('persistent settings feedback and grading', () => {
    it('labels registration choices without submitting a registration', () => {
        render(<MemoryRouter><Register /></MemoryRouter>);
        expect(screen.getByRole('textbox', { name: 'Teljes név' })).toHaveAttribute('autocomplete', 'name');
        expect(screen.getByRole('textbox', { name: 'Email cím' })).toHaveAttribute('autocomplete', 'email');
        expect(screen.getByRole('button', { name: 'Belépés Google fiókkal' })).toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Belépés Discord fiókkal' })).toBeInTheDocument();
        expect(screen.getByRole('combobox', { name: 'Melyik nyelvet szeretnéd tanulni?' })).toHaveValue('en');
        expect(screen.getByRole('combobox', { name: 'Tanulási Mód' })).toHaveValue('DYNAMIC');
        const role = screen.getByRole('group', { name: 'Fiók típusa' });
        fireEvent.click(within(role).getByRole('radio', { name: 'Tanár' }));
        expect(within(role).getByRole('radio', { name: 'Tanár' })).toBeChecked();
        expect(api.post).not.toHaveBeenCalled();
    });
    it('announces registration errors and focuses the confirmation after a successful retry', async () => {
        api.post.mockRejectedValueOnce({ response: { data: { message: 'Ez az e-mail-cím már foglalt.' } } })
            .mockResolvedValueOnce({});
        render(<MemoryRouter><Register /></MemoryRouter>);
        fireEvent.change(screen.getByRole('textbox', { name: 'Teljes név' }), { target: { value: 'Teszt Elek' } });
        const email = screen.getByRole('textbox', { name: 'Email cím' });
        fireEvent.change(email, { target: { value: 'first@example.test' } });
        for (const label of ['Jelszó', 'Jelszó újra']) {
            fireEvent.change(screen.getByLabelText(label, { exact: true }), { target: { value: 'test-password' } });
        }
        fireEvent.click(screen.getByRole('checkbox'));
        fireEvent.click(screen.getByRole('button', { name: 'Regisztráció', exact: true }));
        await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent('Ez az e-mail-cím már foglalt.'));
        expect(email).toHaveValue('first@example.test');
        fireEvent.change(email, { target: { value: 'other@example.test' } });
        fireEvent.click(screen.getByRole('button', { name: 'Regisztráció', exact: true }));
        const confirmation = await screen.findByRole('heading', { level: 1, name: 'Ellenőrizd a postafiókodat!' });
        await waitFor(() => expect(confirmation).toHaveFocus());
        expect(screen.getByText('other@example.test')).toBeInTheDocument();
        expect(api.post).toHaveBeenCalledTimes(2);
    });
    it('names login credentials and OAuth actions without repeating decorative logos', () => {
        render(<MemoryRouter><Login /></MemoryRouter>);
        expect(screen.getByRole('textbox', { name: 'Email cím' })).toHaveAttribute('autocomplete', 'username');
        expect(screen.getByLabelText('Jelszó', { exact: true })).toHaveAttribute('autocomplete', 'current-password');
        expect(screen.getByRole('button', { name: 'Belépés Google fiókkal' })).toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Belépés Discord fiókkal' })).toBeInTheDocument();
        expect(api.post).not.toHaveBeenCalled();
    });
    it('announces a failed OAuth callback on login instead of silently discarding its error', async () => {
        render(<MemoryRouter initialEntries={['/oauth2/redirect']}><Routes>
            <Route path="/oauth2/redirect" element={<OAuth2RedirectHandler />} />
            <Route path="/login" element={<Login />} />
        </Routes></MemoryRouter>);
        expect(await screen.findByRole('heading', { name: 'Bejelentkezés' })).toBeInTheDocument();
        expect(screen.getByRole('alert')).toHaveTextContent('Hiányzó belépési jóváhagyás. Indíts új közösségi belépést.');
        expect(api.get).not.toHaveBeenCalled();
        expect(api.post).not.toHaveBeenCalled();
    });
    it('keeps learning settings feedback and focus after the former three-second deadline', async () => {
        sessionStorage.setItem('settingsActiveTab', 'learning');
        render(<MemoryRouter><Settings /></MemoryRouter>);
        fireEvent.change(screen.getByRole('combobox', { name: 'Célzott Nehézség' }), { target: { value: 'EASY' } });
        const save = screen.getByRole('button', { name: 'Beállítások Mentése' });
        save.focus();
        fireEvent.click(save);
        await waitFor(() => expect(screen.getByRole('status', { name: 'Tanulási beállítások: állapot' })).toHaveTextContent('Tanulási beállítások elmentve!'));
        vi.useFakeTimers();
        act(() => vi.advanceTimersByTime(30_000));
        expect(screen.getByRole('status', { name: 'Tanulási beállítások: állapot' })).toHaveTextContent('Tanulási beállítások elmentve!');
        expect(save).toHaveFocus();
    });

    it('labels password fields, announces mismatch and preserves entered values without sending', () => {
        sessionStorage.setItem('settingsActiveTab', 'security');
        render(<MemoryRouter><Settings /></MemoryRouter>);
        expect(screen.getByLabelText('Jelenlegi jelszó', { exact: true })).toHaveAttribute('autocomplete', 'current-password');
        for (const name of ['Új jelszó', 'Új jelszó megerősítése']) expect(screen.getByLabelText(name, { exact: true })).toHaveAttribute('autocomplete', 'new-password');
        fireEvent.change(screen.getByLabelText('Új jelszó', { exact: true }), { target: { value: 'new-one' } });
        fireEvent.change(screen.getByLabelText('Új jelszó megerősítése'), { target: { value: 'new-two' } });
        fireEvent.submit(screen.getByRole('button', { name: 'Jelszó frissítése' }).closest('form'));
        expect(screen.getByRole('alert', { name: 'Jelszó: hiba' })).toHaveTextContent('Az új jelszavak nem egyeznek!');
        expect(api.put).not.toHaveBeenCalled();
    });

    it.each(['Az ellenőrző kód lejárt!', 'Túl sok hibás próbálkozás történt. Kérj új ellenőrző kódot.'])('retains the requested email and credentials when a code is replaced after %s', async message => {
        sessionStorage.setItem('settingsActiveTab', 'security');
        api.post.mockImplementation(url => url.endsWith('/verify')
            ? Promise.reject({ response: { data: { message } } })
            : Promise.resolve({ data: {} }));
        render(<MemoryRouter><Settings /></MemoryRouter>);
        fireEvent.change(screen.getByLabelText('Új e-mail cím'), { target: { value: 'new@example.test' } });
        fireEvent.change(screen.getByLabelText('Jelenlegi jelszó (megerősítéshez)'), { target: { value: 'test-only-password' } });
        fireEvent.click(screen.getByRole('button', { name: 'Ellenőrző kód küldése' }));
        fireEvent.change(await screen.findByRole('textbox', { name: '6-jegyű ellenőrző kód (OTP)' }), { target: { value: '123456' } });
        fireEvent.click(screen.getByRole('button', { name: 'Kód megerősítése' }));
        expect(await screen.findByRole('alert', { name: 'E-mail cím: hiba' })).toHaveTextContent(message);
        fireEvent.click(screen.getByRole('button', { name: 'Mégse', exact: true }));
        expect(screen.getByLabelText('Új e-mail cím')).toHaveValue('new@example.test');
        expect(screen.getByLabelText('Jelenlegi jelszó (megerősítéshez)')).toHaveValue('test-only-password');
        fireEvent.click(screen.getByRole('button', { name: 'Ellenőrző kód küldése' }));
        const freshCode = await screen.findByRole('textbox', { name: '6-jegyű ellenőrző kód (OTP)' });
        expect(freshCode).toHaveValue('');
        await waitFor(() => expect(freshCode).toHaveFocus());
        expect(api.post.mock.calls.filter(([url]) => url.endsWith('/request'))).toHaveLength(2);
        expect(auth.logout).not.toHaveBeenCalled();
    });

    it('requires one password and an unchecked deliberate confirmation before account deletion', async () => {
        sessionStorage.setItem('settingsActiveTab', 'privacy');
        render(<MemoryRouter><Settings /></MemoryRouter>);
        const opener = screen.getByRole('button', { name: /Fiók.*törlése/i });
        opener.focus(); fireEvent.click(opener);
        const dialog = await screen.findByRole('dialog');
        const first = within(dialog).getByLabelText('Jelszó', { exact: true });
        const confirm = within(dialog).getByRole('checkbox', { name: 'Megértettem a fióktörlés következményeit, és kérem a fiókom törlését.' });
        const finalDelete = within(dialog).getByRole('button', { name: 'Igen, törlöm a fiókom!' });
        expect(finalDelete).toBeDisabled();
        expect(confirm).not.toBeChecked();
        expect(within(dialog).queryByLabelText('Jelszó megerősítése', { exact: true })).not.toBeInTheDocument();
        expect(first).toHaveAttribute('autocomplete', 'current-password');
        fireEvent.change(first, { target: { value: 'test-only-password' } });
        expect(finalDelete).toBeDisabled();
        expect(within(dialog).queryByRole('button', { name: 'Megerősítő mező kitöltése az első jelszóval' })).not.toBeInTheDocument();
        fireEvent.click(confirm);
        expect(finalDelete).toBeEnabled();
        expect(first).toHaveAttribute('type', 'password');
        expect(dialog.textContent).not.toContain('test-only-password');
        expect(api.delete).not.toHaveBeenCalled();
        expect(api.post).not.toHaveBeenCalled();
        expect(auth.logout).not.toHaveBeenCalled();
        fireEvent.click(within(dialog).getByRole('button', { name: 'Mégse', exact: true }));
        await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
        await waitFor(() => expect(opener).toHaveFocus());
        fireEvent.click(opener);
        const reopened = await screen.findByRole('dialog');
        expect(within(reopened).getByRole('checkbox')).not.toBeChecked();
        expect(within(reopened).getByLabelText('Jelszó', { exact: true })).toHaveValue('');
        fireEvent.click(within(reopened).getByRole('button', { name: 'Mégse', exact: true }));
        await waitFor(() => expect(opener).toHaveFocus());
    });

    it('keeps the password and confirmation on rejection and sends both only on explicit final submission', async () => {
        sessionStorage.setItem('settingsActiveTab', 'privacy');
        api.delete.mockRejectedValueOnce({ response: { data: { message: 'A megadott jelszó hibás!' } } })
            .mockResolvedValueOnce({});
        render(<MemoryRouter><Settings /></MemoryRouter>);
        const opener = screen.getByRole('button', { name: /Fiók.*törlése/i });
        opener.focus(); fireEvent.click(opener);
        const dialog = await screen.findByRole('dialog');
        const password = within(dialog).getByLabelText('Jelszó', { exact: true });
        const confirmed = within(dialog).getByRole('checkbox');
        fireEvent.click(confirmed);
        const submit = within(dialog).getByRole('button', { name: 'Igen, törlöm a fiókom!' });
        expect(submit).toBeDisabled();
        fireEvent.change(password, { target: { value: 'test-only-password' } });
        expect(api.delete).not.toHaveBeenCalled();
        fireEvent.click(submit);
        await waitFor(() => expect(within(dialog).getByRole('alert')).toHaveTextContent('A megadott jelszó hibás!'));
        expect(password).toHaveValue('test-only-password');
        expect(confirmed).toBeChecked();
        expect(auth.logout).not.toHaveBeenCalled();
        fireEvent.change(password, { target: { value: 'corrected-test-password' } });
        fireEvent.click(submit);
        await waitFor(() => expect(auth.logout).toHaveBeenCalledExactlyOnceWith(true));
        expect(api.delete.mock.calls).toEqual([
            ['/users/me', { data: { password: 'test-only-password', confirmed: true } }],
            ['/users/me', { data: { password: 'corrected-test-password', confirmed: true } }],
        ]);
        await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
        await waitFor(() => expect(opener).toHaveFocus());
    });

    it('signs out only after the password is saved and keeps an untimed accessible confirmation', async () => {
        sessionStorage.setItem('settingsActiveTab', 'security');
        let complete;
        api.put.mockImplementationOnce(() => new Promise(resolve => { complete = resolve; }));
        render(<MemoryRouter initialEntries={['/settings']}><Routes>
            <Route path="/settings" element={<Settings />} />
            <Route path="/login" element={<Login />} />
        </Routes></MemoryRouter>);
        expect(screen.getByText(/minden eszközön kijelentkeztetünk, ezen az eszközön/)).toBeVisible();
        fireEvent.change(screen.getByLabelText('Jelenlegi jelszó', { exact: true }), { target: { value: 'old-password' } });
        fireEvent.change(screen.getByLabelText('Új jelszó', { exact: true }), { target: { value: 'new-password' } });
        fireEvent.change(screen.getByLabelText('Új jelszó megerősítése', { exact: true }), { target: { value: 'new-password' } });
        fireEvent.click(screen.getByRole('button', { name: 'Jelszó frissítése' }));
        expect(auth.logout).not.toHaveBeenCalled();
        await act(async () => complete({ data: 'saved' }));
        await screen.findByRole('heading', { name: 'Bejelentkezés' });
        expect(auth.logout).toHaveBeenCalledExactlyOnceWith();
        vi.useFakeTimers();act(() => vi.advanceTimersByTime(30_000));
        expect(screen.getByRole('status')).toHaveTextContent('Minden eszközön kijelentkeztettünk.');
    });

    it('keeps the password form and session on a failed password change', async () => {
        sessionStorage.setItem('settingsActiveTab', 'security');
        api.put.mockRejectedValueOnce({ response: { data: { message: 'A jelenlegi jelszó hibás.' } } });
        render(<MemoryRouter><Settings /></MemoryRouter>);
        fireEvent.change(screen.getByLabelText('Jelenlegi jelszó', { exact: true }), { target: { value: 'wrong-password' } });
        fireEvent.change(screen.getByLabelText('Új jelszó', { exact: true }), { target: { value: 'new-password' } });
        fireEvent.change(screen.getByLabelText('Új jelszó megerősítése', { exact: true }), { target: { value: 'new-password' } });
        fireEvent.click(screen.getByRole('button', { name: 'Jelszó frissítése' }));
        expect(await screen.findByRole('alert', { name: 'Jelszó: hiba' })).toHaveTextContent('A jelenlegi jelszó hibás.');
        expect(auth.logout).not.toHaveBeenCalled();
    });

    it('preserves the email-change confirmation on the login screen without a timed redirect', async () => {
        sessionStorage.setItem('settingsActiveTab', 'security');
        render(<MemoryRouter initialEntries={['/settings']}><Routes>
            <Route path="/settings" element={<Settings />} />
            <Route path="/login" element={<Login />} />
        </Routes></MemoryRouter>);
        fireEvent.change(screen.getByLabelText('Új e-mail cím'), { target: { value: 'new@example.test' } });
        fireEvent.change(screen.getByLabelText('Jelenlegi jelszó (megerősítéshez)'), { target: { value: 'old-password' } });
        fireEvent.click(screen.getByRole('button', { name: 'Ellenőrző kód küldése' }));
        const code = await screen.findByRole('textbox', { name: '6-jegyű ellenőrző kód (OTP)' });
        expect(code).toHaveAttribute('autocomplete', 'one-time-code');
        expect(code).toHaveFocus();
        fireEvent.change(code, { target: { value: '123456' } });
        fireEvent.click(screen.getByRole('button', { name: 'Kód megerősítése' }));
        await screen.findByRole('heading', { name: 'Bejelentkezés' });
        expect(auth.logout).toHaveBeenCalledTimes(1);
        vi.useFakeTimers();
        act(() => vi.advanceTimersByTime(30_000));
        expect(screen.getByRole('status')).toHaveTextContent('E-mail cím sikeresen frissítve! Jelentkezz be az új e-mail címeddel.');
    });

    it('retains a failed grade for retry and restores the same row after publication', async () => {
        assignmentApi.getAssignmentSessions.mockResolvedValue({ data: [{ sessionId: 'session', studentName: 'Diák', teacherScore: null,
            finalScore: 80, answers: [], finishedAt: '2026-10-18T12:00:00Z' }] });
        let rejectSave;
        assignmentApi.gradeSession.mockImplementationOnce(() => new Promise((_, reject) => { rejectSave = reject; })).mockResolvedValueOnce({});
        vi.spyOn(console, 'error').mockImplementation(() => {});
        render(<MemoryRouter initialEntries={['/assignment/task/submissions']}><Routes>
            <Route path="/assignment/:id/submissions" element={<AssignmentSubmissions />} />
        </Routes></MemoryRouter>);
        const origin = await screen.findByRole('button', { name: /^Értékelés: Diák/ });
        origin.focus(); fireEvent.click(origin);
        const dialog = await screen.findByRole('dialog');
        fireEvent.change(within(dialog).getByLabelText('Tanári Megjegyzés (Opcionális)'), { target: { value: 'Szép munka.' } });
        const publish = within(dialog).getByRole('button', { name: 'Eredmény Publikálása' });
        publish.focus(); fireEvent.click(publish); fireEvent.click(publish);
        expect(assignmentApi.gradeSession).toHaveBeenCalledTimes(1);
        expect(publish).toHaveFocus();
        await act(async () => rejectSave(new Error('offline')));
        expect(within(dialog).getByRole('alert')).toHaveTextContent('Nem sikerült elmenteni az értékelést.');
        expect(within(dialog).getByLabelText('Tanári Megjegyzés (Opcionális)')).toHaveValue('Szép munka.');
        fireEvent.click(publish);
        await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
        expect(origin).toHaveFocus();
        expect(origin).toHaveAccessibleName(/^Módosítás: Diák/);
        expect(assignmentApi.getAssignmentSessions).toHaveBeenCalledTimes(1);
    });
});
