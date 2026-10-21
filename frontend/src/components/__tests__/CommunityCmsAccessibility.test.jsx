import { act, cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import CurriculumManager from '../admin/CurriculumManager';
import FriendSearch from '../FriendSearch';
import FriendList from '../FriendList';
import PendingRequests from '../PendingRequests';
import AccessibleCode from '../AccessibleCode';
import { adminApi } from '../../services/adminApi';
import api from '../../services/api';

vi.mock('../../services/adminApi', () => ({ adminApi: {
    getAllTopics: vi.fn(), toggleTopicStatus: vi.fn(), toggleLessonStatus: vi.fn(),
    toggleExerciseStatus: vi.fn(), importCurriculum: vi.fn(), importLessons: vi.fn(), importExercises: vi.fn(),
} }));
vi.mock('../../services/api', () => ({ default: { get: vi.fn(), post: vi.fn(), delete: vi.fn() } }));
vi.mock('../../context/AuthContext', () => ({ useAuth: () => ({ user: { name: 'Én', userTag: '1', activeCourseCode: 'en' } }) }));
vi.mock('../../context/NotificationContext', () => ({ useNotifications: () => ({ notifications: {}, refreshNotifications: vi.fn() }) }));

const topic = { topicId: 'topic', topicName: 'Alapszavak', courseCode: 'en', active: true, lessons: [
    { lessonId: 'lesson', title: 'Gyümölcsök', orderIndex: 1, active: true, exercises: [
        { exerciseId: 'exercise', type: 'MULTIPLE_CHOICE', difficulty: 'EASY', active: true,
            content: { question: 'Melyik az alma?', questionLang: 'hu' } },
    ] },
] };
const request = { friendshipId: 'request', senderName: 'Tesztelő', senderTag: '42', sentAt: '2026-10-18T10:00:00Z' };
const friend = { friendshipId: 'friendship', friendId: 'friend', name: 'Tesztelő', userTag: '42' };

beforeEach(() => {
    vi.resetAllMocks();
    adminApi.getAllTopics.mockResolvedValue({ data: structuredClone([topic]) });
    api.get.mockImplementation(url => Promise.resolve({ data: url.includes('pending') ? [request]
        : url.includes('accepted') ? [friend] : [{ lessonId: 'lesson', title: 'Gyümölcsök', courseCode: 'en' }] }));
});
afterEach(() => { cleanup(); vi.restoreAllMocks(); });

describe('CMS controls', () => {
    it('preserves the current import mode and course when the select reports no valid option', async () => {
        render(<CurriculumManager />);
        const course = await screen.findByRole('combobox', { name: 'Nyelv (Kurzus)' });
        const level = screen.getByRole('combobox', { name: 'Mit szeretnél importálni?' });
        fireEvent.change(course, { target: { value: 'es' } });
        level.focus();
        fireEvent.change(level, { target: { value: '' } });
        expect(level).toHaveValue('FULL_TOPIC');
        expect(screen.getByRole('combobox', { name: 'Nyelv (Kurzus)' })).toHaveValue('es');
        expect(level).toHaveFocus();
        fireEvent.change(level, { target: { value: 'FULL_TOPIC' } });
        expect(screen.getByRole('combobox', { name: 'Nyelv (Kurzus)' })).toHaveValue('es');
        fireEvent.change(level, { target: { value: 'LESSON' } });
        fireEvent.change(screen.getByRole('combobox', { name: 'Cél Témakör' }), { target: { value: 'topic' } });
        fireEvent.change(level, { target: { value: 'LESSON' } });
        expect(screen.getByRole('combobox', { name: 'Cél Témakör' })).toHaveValue('topic');
    });

    it('separates question language from action names and distinguishes identical questions by position', async () => {
        const data = structuredClone(topic);
        const exercise = data.lessons[0].exercises[0];
        exercise.content = { question: 'Which animal can fly?', questionLang: 'en', answerLang: 'en' };
        data.lessons[0].exercises.push({ ...exercise, exerciseId: 'another-exercise' });
        adminApi.getAllTopics.mockResolvedValue({ data: [data] });
        render(<CurriculumManager />);
        fireEvent.click(await screen.findByRole('button', { name: 'Alapszavak', exact: true }));
        for (const text of screen.getAllByText('Which animal can fly?')) expect(text).toHaveAttribute('lang', 'en');
        for (const index of [1, 2]) {
            expect(screen.getByRole('button', { name: `Megtekintés: Gyümölcsök, ${index}. feladat` })).toBeInTheDocument();
            expect(screen.getByRole('button', { name: `Felfüggesztés: Gyümölcsök, ${index}. feladat` })).toBeInTheDocument();
        }
    });

    it('separates the accordion toggle from its status action and labels every import field', async () => {
        const { container } = render(<CurriculumManager />);
        const toggle = await screen.findByRole('button', { name: 'Alapszavak', exact: true });
        expect(container.querySelector('button button')).toBeNull();
        expect(screen.getByRole('button', { name: 'Témakör Felfüggesztése: Alapszavak' })).not.toBe(toggle);
        fireEvent.click(toggle);
        expect(toggle).toHaveAttribute('aria-expanded', 'true');
        expect(adminApi.toggleTopicStatus).not.toHaveBeenCalled();
        expect(screen.getByRole('button', { name: 'Felfüggesztés: Gyümölcsök' })).toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Megtekintés: Gyümölcsök, 1. feladat' })).toBeInTheDocument();
        expect(screen.getByLabelText('Tananyag JSON-fájlja')).toHaveAccessibleDescription(/TopicImportRequest/);
        expect(screen.getByRole('combobox', { name: 'Nyelv (Kurzus)' })).toHaveValue('en');
        expect(screen.getByRole('heading', { name: 'Tananyag JSON Importálása', level: 3 })).toBeInTheDocument();
        expect(screen.getByRole('heading', { name: 'Meglévő Tananyagok (Kezelés)', level: 3 })).toBeInTheDocument();
        expect(screen.getByRole('heading', { name: /Angol Kurzus/, level: 4 })).toBeInTheDocument();
        expect(screen.getByRole('heading', { name: 'Alapszavak', level: 5 })).toBeInTheDocument();
        expect(screen.getByRole('heading', { name: /Gyümölcsök \(Sorszám:/, level: 6 })).toBeInTheDocument();
        fireEvent.change(screen.getByRole('combobox', { name: 'Mit szeretnél importálni?' }), { target: { value: 'EXERCISE' } });
        fireEvent.change(screen.getByRole('combobox', { name: 'Cél Témakör' }), { target: { value: 'topic' } });
        expect(screen.getByRole('combobox', { name: 'Cél Lecke' })).toBeEnabled();
    });

    it('keeps focus during a status update, prevents duplicates and announces the result', async () => {
        vi.spyOn(window, 'confirm').mockReturnValue(true);
        let resolveUpdate;
        adminApi.toggleTopicStatus.mockImplementation(() => new Promise(resolve => { resolveUpdate = resolve; }));
        render(<CurriculumManager />);
        const button = await screen.findByRole('button', { name: 'Témakör Felfüggesztése: Alapszavak' });
        button.focus();
        fireEvent.click(button);
        fireEvent.click(button);
        expect(adminApi.toggleTopicStatus).toHaveBeenCalledTimes(1);
        expect(button).toHaveFocus();
        await act(async () => resolveUpdate({}));
        expect(screen.getByRole('status')).toHaveTextContent('Témakör státusza frissítve!');
        expect(button).toHaveAccessibleName('Témakör Visszaállítása: Alapszavak');
        expect(button).toHaveFocus();
    });

    it('opens a named preview and returns focus when it is closed', async () => {
        render(<CurriculumManager />);
        fireEvent.click(await screen.findByRole('button', { name: 'Alapszavak', exact: true }));
        const preview = screen.getByRole('button', { name: /Megtekintés: Gyümölcsök/ });
        preview.focus();
        fireEvent.click(preview);
        const dialog = await screen.findByRole('dialog', { name: 'Feleletválasztás – Előnézet' });
        expect(within(dialog).getByRole('heading', { name: 'Feleletválasztás – Előnézet' })).toBeInTheDocument();
        fireEvent.click(within(dialog).getByRole('button', { name: 'Előnézet bezárása' }));
        await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
        expect(preview).toHaveFocus();
    });

    it.each(['en', 'de'])('provides a separate %s focus point for the answer row under a Hungarian preview title', async answerLang => {
        const data = structuredClone(topic);
        data.lessons[0].exercises[0].content = {
            question: 'Melyik állat repül?', questionLang: 'hu', answerLang,
            options: answerLang === 'en' ? ['Dog', 'Bird', 'Fish'] : ['Hund', 'Vogel', 'Fisch'],
        };
        adminApi.getAllTopics.mockResolvedValue({ data: [data] });
        render(<CurriculumManager />);
        fireEvent.click(await screen.findByRole('button', { name: 'Alapszavak', exact: true }));
        fireEvent.click(screen.getByRole('button', { name: 'Megtekintés: Gyümölcsök, 1. feladat' }));
        const dialog = await screen.findByRole('dialog', { name: 'Feleletválasztás – Előnézet' });
        const options = data.lessons[0].exercises[0].content.options;
        const row = within(dialog).getByRole('group', { name: options.join(', ') });
        expect(row).toHaveAttribute('lang', answerLang);
        expect(row).toHaveAttribute('tabindex', '0');
        row.focus();
        expect(row).toHaveFocus();
        for (const option of options) expect(within(row).getByText(option)).toHaveAttribute('lang', answerLang);
        expect(within(dialog).getByRole('heading', { name: 'Feleletválasztás – Előnézet' }).closest('[lang]')).not.toHaveAttribute('lang', answerLang);
    });

    it('announces an import validation error and retains the file for retry', async () => {
        adminApi.importCurriculum.mockRejectedValue({ response: { data: { message: 'A képhez magyar célleírás szükséges.' } } });
        render(<CurriculumManager />);
        await screen.findByRole('button', { name: 'Alapszavak', exact: true });
        const file = new File(['[]'], 'topic.json', { type: 'application/json' });
        fireEvent.change(screen.getByLabelText('Tananyag JSON-fájlja'), { target: { files: [file] } });
        const submit = screen.getByRole('button', { name: 'Kiválasztott JSON Importálása' });
        submit.focus();
        fireEvent.click(submit);
        await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent('A képhez magyar célleírás szükséges.'));
        expect(submit).toBeEnabled();
        expect(submit).toHaveFocus();
        fireEvent.click(submit);
        await waitFor(() => expect(adminApi.importCurriculum).toHaveBeenCalledTimes(2));
    });

    it('announces successful import without dropping focus when the selected file is cleared', async () => {
        adminApi.importCurriculum.mockResolvedValue({});
        render(<CurriculumManager />);
        await screen.findByRole('button', { name: 'Alapszavak', exact: true });
        const file = new File(['[{"topicName":"Új témakör"}]'], 'topic.json', { type: 'application/json' });
        fireEvent.change(screen.getByLabelText('Tananyag JSON-fájlja'), { target: { files: [file] } });
        const submit = screen.getByRole('button', { name: 'Kiválasztott JSON Importálása' });
        submit.focus();
        fireEvent.click(submit);
        await waitFor(() => expect(screen.getByRole('status')).toHaveTextContent('Tananyag sikeresen importálva!'));
        expect(adminApi.importCurriculum).toHaveBeenCalledExactlyOnceWith([{ topicName: 'Új témakör', courseCode: 'en' }]);
        expect(submit).toHaveFocus();
        expect(submit).toHaveAttribute('aria-disabled', 'true');
        fireEvent.click(submit);
        expect(adminApi.importCurriculum).toHaveBeenCalledTimes(1);
    });
});

describe('friend search and request feedback', () => {
    it('describes the sample code character by character instead of treating X as a Roman numeral', () => {
        render(<FriendSearch />);
        const field = screen.getByRole('textbox', { name: 'Név és azonosító vagy barátkód' });
        expect(field).toHaveAccessibleDescription(/Karakterenként: a, hét, bé, kötőjel, kilenc, iksz, kettő/);
        expect(screen.getByText('A7B-9X2')).toHaveAttribute('aria-hidden', 'true');
    });

    it.each([
        ['DL7-WG', 'dé, el, hét, kötőjel, dupla vé, gé'],
        ['IV-12', 'i, vé, kötőjel, egy, kettő'],
    ])('spells the displayed identifier %s with an unambiguous accessible alternative', (code, spoken) => {
        render(<div role="group" aria-labelledby="test-spoken-code"><span id="test-spoken-code"><AccessibleCode code={code} /></span></div>);
        expect(screen.getByRole('group')).toHaveAccessibleName(`Karakterenként: ${spoken}`);
        expect(screen.getByText(code)).toHaveAttribute('aria-hidden', 'true');
    });

    it('labels the identifier, retains focus during sending and blocks duplicate requests', async () => {
        let resolveRequest;
        api.post.mockImplementation(() => new Promise(resolve => { resolveRequest = resolve; }));
        render(<FriendSearch />);
        const input = screen.getByRole('textbox', { name: 'Név és azonosító vagy barátkód' });
        expect(input).toHaveAccessibleDescription(/Username#1234/);
        fireEvent.change(input, { target: { value: '  Tesztelő#42  ' } });
        const submit = screen.getByRole('button', { name: 'Kérelem Küldése' });
        submit.focus();
        fireEvent.click(submit);
        fireEvent.click(submit);
        expect(api.post).toHaveBeenCalledExactlyOnceWith('/friendships/request', { targetIdentifier: 'Tesztelő#42' });
        expect(input).toHaveAttribute('readonly');
        expect(submit).toHaveFocus();
        await act(async () => resolveRequest({ data: { message: 'Kérelem elküldve.' } }));
        expect(screen.getByRole('status')).toHaveTextContent('Kérelem elküldve.');
        expect(input).toHaveValue('');
        expect(submit).toHaveFocus();
    });

    it('announces failure without discarding the entered identifier', async () => {
        api.post.mockRejectedValue({ response: { data: { message: 'Nem található ilyen felhasználó.' } } });
        render(<FriendSearch />);
        const input = screen.getByRole('textbox', { name: 'Név és azonosító vagy barátkód' });
        fireEvent.change(input, { target: { value: 'Tesztelő#42' } });
        fireEvent.click(screen.getByRole('button', { name: 'Kérelem Küldése' }));
        await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent('Nem található ilyen felhasználó.'));
        expect(input).toHaveValue('Tesztelő#42');
        expect(input).not.toHaveAttribute('readonly');
    });

    it('names request actions and moves focus to the list heading after removing the focused card', async () => {
        let resolveAction;
        api.post.mockImplementation(() => new Promise(resolve => { resolveAction = resolve; }));
        render(<PendingRequests />);
        const accept = await screen.findByRole('button', { name: 'Elfogad: Tesztelő#42' });
        expect(screen.getByRole('heading', { name: 'Tesztelő #42', level: 3 })).toBeInTheDocument();
        accept.focus();
        fireEvent.click(accept);
        fireEvent.click(screen.getByRole('button', { name: 'Elutasít: Tesztelő#42' }));
        expect(api.post).toHaveBeenCalledTimes(1);
        expect(accept).toHaveFocus();
        await act(async () => resolveAction({ data: { message: 'Elfogadva.' } }));
        expect(screen.getByRole('heading', { name: 'Beérkező kérelmek (0)' })).toHaveFocus();
        expect(screen.getByRole('status')).toHaveTextContent('Elfogadva.');
    });

    it('retains the focused action and the request when processing fails', async () => {
        api.post.mockRejectedValue({ response: { data: { message: 'Próbáld újra később.' } } });
        render(<PendingRequests />);
        const reject = await screen.findByRole('button', { name: 'Elutasít: Tesztelő#42' });
        reject.focus();
        fireEvent.click(reject);
        await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent('Próbáld újra később.'));
        expect(reject).toHaveFocus();
        expect(reject).toHaveAttribute('aria-disabled', 'false');
    });

    it('does not steal focus when the user moves away while a request is being processed', async () => {
        let resolveAction;
        api.post.mockImplementation(() => new Promise(resolve => { resolveAction = resolve; }));
        render(<><button>Másik művelet</button><PendingRequests /></>);
        const accept = await screen.findByRole('button', { name: 'Elfogad: Tesztelő#42' });
        accept.focus();
        fireEvent.click(accept);
        const elsewhere = screen.getByRole('button', { name: 'Másik művelet' });
        elsewhere.focus();
        await act(async () => resolveAction({ data: {} }));
        expect(elsewhere).toHaveFocus();
        expect(screen.getByRole('heading', { name: 'Beérkező kérelmek (0)' })).toBeInTheDocument();
    });
});

describe('friend list and challenge form', () => {
    it('names friend actions, labels all challenge fields and restores focus after closing', async () => {
        render(<MemoryRouter><FriendList /></MemoryRouter>);
        const open = await screen.findByRole('button', { name: 'Kihívás: Tesztelő#42' });
        expect(screen.getByRole('heading', { name: 'Tesztelő', level: 3 })).toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Barát törlése: Tesztelő#42' })).toBeInTheDocument();
        open.focus();
        fireEvent.click(open);
        const dialog = await screen.findByRole('dialog', { name: 'Kihívod: Tesztelő' });
        for (const label of ['1. Milyen nyelven szeretnél párbajozni?', '2. Milyen nehézségi szinten párbajozzatok?',
            '3. Melyik leckéből hívod ki?', '4. Meddig érvényes a kihívás?']) {
            expect(within(dialog).getByRole('combobox', { name: label })).toBeInTheDocument();
        }
        await waitFor(() => expect(within(dialog).getByRole('combobox', { name: /3. Melyik/ })).toHaveValue('lesson'));
        fireEvent.click(within(dialog).getByRole('button', { name: 'Kihívás bezárása' }));
        await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
        expect(open).toHaveFocus();
        expect(api.post).not.toHaveBeenCalled();
    });

    it('keeps the challenge submit focused, blocks duplicate creation and announces errors', async () => {
        let rejectChallenge;
        api.post.mockImplementation(() => new Promise((_, reject) => { rejectChallenge = reject; }));
        render(<MemoryRouter><FriendList /></MemoryRouter>);
        fireEvent.click(await screen.findByRole('button', { name: 'Kihívás: Tesztelő#42' }));
        const submit = await screen.findByRole('button', { name: 'Játék Indítása!' });
        await waitFor(() => expect(submit).toBeEnabled());
        submit.focus();
        fireEvent.click(submit);
        fireEvent.click(submit);
        expect(api.post).toHaveBeenCalledTimes(1);
        expect(submit).toHaveFocus();
        fireEvent.click(screen.getByRole('button', { name: 'Kihívás bezárása' }));
        expect(screen.getByRole('dialog')).toBeInTheDocument();
        await act(async () => rejectChallenge({ response: { data: { message: 'Most nem indítható kihívás.' } } }));
        expect(within(screen.getByRole('dialog')).getByRole('alert')).toHaveTextContent('Most nem indítható kihívás.');
        expect(submit).toHaveFocus();
    });

    it('shows a remove error in the friend list even when the challenge modal is closed', async () => {
        vi.spyOn(window, 'confirm').mockReturnValue(true);
        vi.spyOn(console, 'error').mockImplementation(() => {});
        api.delete.mockRejectedValue(new Error('offline'));
        render(<MemoryRouter><FriendList /></MemoryRouter>);
        fireEvent.click(await screen.findByRole('button', { name: 'Barát törlése: Tesztelő#42' }));
        await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent('Nem sikerült törölni a barátot. Próbáld újra!'));
        expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Kihívás: Tesztelő#42' })).toBeInTheDocument();
    });

    it('blocks duplicate removal and preserves a meaningful focus target after the final friend disappears', async () => {
        vi.spyOn(window, 'confirm').mockReturnValue(true);
        let resolveRemoval;
        api.delete.mockImplementation(() => new Promise(resolve => { resolveRemoval = resolve; }));
        render(<MemoryRouter><FriendList /></MemoryRouter>);
        const remove = await screen.findByRole('button', { name: 'Barát törlése: Tesztelő#42' });
        remove.focus();
        fireEvent.click(remove);
        fireEvent.click(remove);
        expect(api.delete).toHaveBeenCalledExactlyOnceWith('/friendships/friendship');
        expect(remove).toHaveFocus();
        await act(async () => resolveRemoval({}));
        expect(screen.getByRole('heading', { name: 'Még nincsenek barátaid a listán.' })).toHaveFocus();
    });
});
