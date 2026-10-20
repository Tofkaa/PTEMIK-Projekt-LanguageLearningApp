import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import LessonPlayer from '../../pages/LessonPlayer';
import AssignmentPlayer from '../../pages/AssignmentPlayer';
import VocabularyPractice from '../../pages/VocabularyPractice';
import api from '../../services/api';
import { assignmentApi } from '../../services/assignmentApi';

vi.mock('../../services/api', () => ({ default: { get: vi.fn(), post: vi.fn() } }));
vi.mock('../../services/assignmentApi', () => ({ assignmentApi: { submitAssignment: vi.fn() } }));
const auth = vi.hoisted(() => ({ user: { xp: 0, streak: 0 }, updateProfile: vi.fn(), vocabularyMap: {} }));
vi.mock('../../context/AuthContext', () => ({
    useAuth: () => auth,
}));
vi.mock('../../context/NotificationContext', () => ({
    useNotifications: () => ({ refreshNotifications: vi.fn() }),
}));

const questions = [
    { exerciseId: 'one', type: 'TRANSLATION', content: { question: 'Fordítsd le: alma', questionLang: 'hu', answerLang: 'en' } },
    { exerciseId: 'two', type: 'TRANSLATION', content: { question: 'Fordítsd le: körte', questionLang: 'hu', answerLang: 'en' } },
];
const practice = [
    { exerciseId: 'one', type: 'TRANSLATION', question: 'Fordítsd le: apple', targetWord: 'apple', correctAnswer: 'alma' },
    { exerciseId: 'two', type: 'TRANSLATION', question: 'Fordítsd le: pear', targetWord: 'pear', correctAnswer: 'körte' },
];
const lessonResult = { passed: true, totalQuestionsCount: 2, correctAnswersCount: 2, xpEarned: 20, mistakes: [], feedback: 'Szép munka!' };

beforeEach(() => {
    vi.resetAllMocks();
    api.get.mockImplementation(async url => ({ data: url.startsWith('/lessons/') ? questions
        : url === '/vocabulary/practice/generate' ? practice : { xp: 20, streak: 1 } }));
    api.post.mockImplementation(async url => ({ data: url.endsWith('/check')
        ? { correct: true, feedbackMessage: 'Helyes válasz.' } : lessonResult }));
    assignmentApi.submitAssignment.mockResolvedValue({ data: {} });
});
afterEach(() => {
    cleanup();
    vi.useRealTimers();
    vi.restoreAllMocks();
});

async function openLesson() {
    render(<MemoryRouter initialEntries={['/lesson/example']}>
        <Routes><Route path="/lesson/:id" element={<LessonPlayer />} /></Routes>
    </MemoryRouter>);
    await screen.findByRole('textbox', { name: 'Válaszod' });
    await waitFor(() => expect(screen.getByRole('heading', { level: 1 })).toHaveFocus());
}

function openAssignment({ feedback = false, items = questions, dictionary = false } = {}) {
    render(<MemoryRouter initialEntries={[{ pathname: '/assignment/session/example/play', state: {
        sessionData: { exercises: items, classroomId: 'classroom' },
        assignmentDetails: { hasFeedback: feedback, allowRetries: false, test: true, allowDictionary: dictionary },
    } }]}>
        <Routes><Route path="/assignment/session/:sessionId/play" element={<AssignmentPlayer />} /></Routes>
    </MemoryRouter>);
}

async function openPractice() {
    render(<MemoryRouter><VocabularyPractice /></MemoryRouter>);
    await screen.findByRole('textbox', { name: 'Válaszod' });
}

function typeAnswer(text) {
    const input = screen.getByRole('textbox', { name: 'Válaszod' });
    input.focus();
    fireEvent.change(input, { target: { value: text } });
    return input;
}

function activate(name) {
    const button = screen.getByRole('button', { name, exact: true });
    button.focus();
    fireEvent.click(button);
    return button;
}

describe('lesson focus and feedback', () => {
    it('focuses the Hungarian task type before a separately focusable foreign question', async () => {
        api.get.mockResolvedValueOnce({ data: [{ exerciseId: 'choice', type: 'MULTIPLE_CHOICE', content: {
            question: 'What color is a ripe banana?', questionLang: 'en', answerLang: 'en', options: ['Yellow', 'Red'],
        } }] });
        render(<MemoryRouter initialEntries={['/lesson/example']}>
            <Routes><Route path="/lesson/:id" element={<LessonPlayer />} /></Routes>
        </MemoryRouter>);
        const heading = await screen.findByRole('heading', { level: 1, name: /^Válaszd ki a helyes opciót/ });
        expect(heading).toHaveTextContent(/^Válaszd ki a helyes opciót\. 1\. feladat, összesen 1\./);
        await waitFor(() => expect(heading).toHaveFocus());
        expect(heading).toHaveAttribute('lang', 'hu');
        const question = screen.getByRole('heading', { level: 2 });
        expect(question).toHaveAttribute('lang', 'en');
        expect(question).toHaveAttribute('tabindex', '0');
        expect(question).toHaveTextContent('What');
        question.focus();
        expect(question).toHaveFocus();
        expect(heading).not.toHaveAttribute('aria-describedby');
    });
    it('keeps the edit value and focus stable while the elapsed timer updates', async () => {
        await openLesson();
        const input = typeAnswer('I like apples.');
        vi.useFakeTimers();
        act(() => vi.advanceTimersByTime(5000));
        expect(input).toHaveFocus();
        expect(input).toHaveValue('I like apples.');
        expect(screen.queryByRole('status')).not.toBeInTheDocument();
        expect(screen.getByRole('timer')).toHaveAttribute('aria-live', 'off');
        expect(api.post).not.toHaveBeenCalled();
    });
    it('renders explicit bilingual hint segments in the lesson', async () => {
        api.get.mockResolvedValueOnce({ data: [{ ...questions[0], content: { ...questions[0].content,
            hint: 'alma = apple', hintDirection: 'hu-to-target', hintParts: [
                { text: 'alma = ', lang: 'hu' }, { text: 'apple', lang: 'en' },
            ],
        } }] });
        await openLesson();
        const instruction = screen.getByRole('heading', { level: 1 });
        expect(instruction).toHaveFocus();
        const question = screen.getByRole('heading', { level: 2, name: 'Fordítsd le: alma' });
        expect(question).toHaveAttribute('tabindex', '0');
        expect(instruction).toHaveAccessibleName(/^Fordítás.*1\. feladat, összesen 1\./);
        const hint = screen.getByRole('note', { name: 'Tipp a fordításhoz' });
        expect(hint).toHaveAttribute('tabindex', '0');
        expect(hint).toHaveAccessibleDescription(/alma\s*=\s*apple/);
        const answer = screen.getByRole('textbox', { name: 'Válaszod' });
        expect(answer).toHaveAttribute('lang', 'en');
        expect(screen.getByText('Válaszod', { exact: true })).toHaveAttribute('lang', 'hu');
        expect(answer).not.toHaveAttribute('placeholder');
        expect(screen.getByText('apple')).toHaveAttribute('lang', 'en');
        expect(screen.getByText('alma =')).toHaveAttribute('lang', 'hu');
    });
    it('retains question segments and answer languages in the final mistake review', async () => {
        const question = "How do you say 'Anya'?";
        api.get.mockResolvedValueOnce({ data: [{ exerciseId: 'mixed', type: 'TRANSLATION', content: {
            question, questionLang: 'en', answerLang: 'en',
            questionParts: [{ text: 'How do you say ', lang: 'en' }, { text: "'Anya'", lang: 'hu' }, { text: '?', lang: 'en' }],
        } }] });
        api.post.mockImplementation(async (url, payload) => ({ data: url.endsWith('/check')
            ? { correct: payload.answer === 'Mother', feedbackMessage: payload.answer === 'Mother' ? 'Helyes válasz.' : 'Helytelen válasz.' }
            : { ...lessonResult, passed: false, correctAnswersCount: 0, totalQuestionsCount: 1, mistakes: [
                { exerciseId: 'mixed', question, submittedAnswer: 'Father', correctAnswer: 'Mother' },
                { exerciseId: 'mixed', question, submittedAnswer: '', correctAnswer: 'Mother' },
            ] } }));
        await openLesson();
        typeAnswer('Father');
        activate('Ellenőrzés');
        await screen.findByRole('button', { name: 'Tovább' });
        activate('Tovább');
        typeAnswer('Mother');
        activate('Ellenőrzés');
        await screen.findByRole('button', { name: 'Befejezés és Értékelés' });
        activate('Befejezés és Értékelés');
        const resultHeading = await screen.findByRole('heading', { name: 'Gyakorolj még egy kicsit!' });
        await waitFor(() => expect(resultHeading).toHaveFocus());
        screen.getAllByText("'Anya'").forEach(part => expect(part).toHaveAttribute('lang', 'hu'));
        expect(screen.getByText('Father')).toHaveAttribute('lang', 'en');
        screen.getAllByText('Mother').forEach(answer => expect(answer).toHaveAttribute('lang', 'en'));
        expect(screen.getByText('(Üresen hagyva)')).toHaveAttribute('lang', 'hu');
    });
    it('keeps feedback focus, then focuses each new question and the result', async () => {
        await openLesson();
        expect(screen.getByRole('heading', { level: 1 })).toHaveFocus();
        expect(screen.getByRole('progressbar', { name: 'Lecke előrehaladása' })).toHaveAttribute('aria-valuenow', '0');
        typeAnswer('apple');
        const action = activate('Ellenőrzés');
        await waitFor(() => expect(screen.getByRole('status')).toHaveTextContent('Helyes válasz.'));
        expect(action).toHaveFocus();
        expect(screen.getByRole('textbox')).toBeDisabled();
        activate('Tovább');
        expect(screen.getByRole('heading', { level: 1 })).toHaveFocus();
        expect(screen.getByText('Fordítsd le: körte')).toBeInTheDocument();
        expect(screen.getByRole('textbox')).toHaveValue('');
        typeAnswer('pear');
        activate('Ellenőrzés');
        await screen.findByRole('button', { name: 'Befejezés és Értékelés' });
        activate('Befejezés és Értékelés');
        const result = await screen.findByRole('heading', { name: 'Sikeres Lecke!' });
        await waitFor(() => expect(result).toHaveFocus());
        await waitFor(() => expect(auth.updateProfile).toHaveBeenCalledWith({ xp: 20, streak: 1 }));
        expect(screen.getByText('2 próbálkozásból 2 helyes válasz')).toBeInTheDocument();
    });

    it('keeps focus and prevents duplicate requests while checking', async () => {
        await openLesson();
        let finish;
        api.post.mockReturnValueOnce(new Promise(resolve => { finish = resolve; }));
        typeAnswer('apple');
        const action = activate('Ellenőrzés');
        expect(action).toHaveFocus();
        expect(action).toHaveAttribute('aria-disabled', 'true');
        expect(screen.getByRole('textbox')).toBeDisabled();
        fireEvent.click(action);
        expect(api.post).toHaveBeenCalledTimes(1);
        await act(async () => finish({ data: { almostCorrect: true, feedbackMessage: 'Javítsd a gépelést.' } }));
        expect(screen.getByRole('status')).toHaveTextContent('Javítsd a gépelést.');
        expect(screen.getByRole('textbox')).toBeEnabled();
        expect(action).toHaveFocus();
    });

    it('keeps the answer and current question after a check failure', async () => {
        vi.spyOn(console, 'error').mockImplementation(() => {});
        await openLesson();
        api.post.mockRejectedValueOnce(new Error('offline'));
        typeAnswer('apple');
        activate('Ellenőrzés');
        await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent('A válaszod megmaradt'));
        expect(screen.getByRole('textbox')).toHaveValue('apple');
        expect(screen.getByRole('textbox')).toBeEnabled();
        expect(screen.getByText('Fordítsd le: alma')).toBeInTheDocument();
    });

    it('offers retry with the same collected answers after a save failure', async () => {
        vi.spyOn(console, 'error').mockImplementation(() => {});
        api.get.mockResolvedValueOnce({ data: [questions[0]] });
        await openLesson();
        typeAnswer('apple');
        activate('Ellenőrzés');
        await screen.findByRole('button', { name: 'Befejezés és Értékelés' });
        api.post.mockRejectedValueOnce(new Error('offline'));
        activate('Befejezés és Értékelés');
        const errorHeading = await screen.findByRole('heading', { name: 'Nem sikerült beküldeni a válaszokat.' });
        await waitFor(() => expect(errorHeading).toHaveFocus());
        activate('Mentés újrapróbálása');
        await screen.findByRole('heading', { name: 'Sikeres Lecke!' });
        const submissions = api.post.mock.calls.filter(([url]) => url.endsWith('/submit'));
        expect(submissions).toHaveLength(2);
        expect(submissions[1][1].answers).toEqual(submissions[0][1].answers);
    });
});

describe('assignment flow', () => {
    it.each([true, false])('respects teacher dictionary policy for bilingual hints: %s', dictionary => {
        openAssignment({ dictionary, items: [{ ...questions[0], content: { ...questions[0].content,
            hint: 'alma = apple', hintDirection: 'hu-to-target', hintParts: [
                { text: 'alma = ', lang: 'hu' }, { text: 'apple', lang: 'en' },
            ],
        } }] });
        if (dictionary) expect(screen.getByText('apple')).toHaveAttribute('lang', 'en');
        else expect(screen.queryByText('apple')).not.toBeInTheDocument();
    });
    it('focuses the next question and summary without revealing test feedback', async () => {
        openAssignment();
        expect(screen.getByRole('heading', { level: 1 })).toHaveFocus();
        expect(screen.getByRole('progressbar', { name: 'Feladatsor előrehaladása' })).toBeInTheDocument();
        typeAnswer('apple');
        activate('Tovább');
        expect(screen.getByRole('heading', { level: 1 })).toHaveFocus();
        expect(screen.getByText('Fordítsd le: körte')).toBeInTheDocument();
        expect(screen.queryByRole('status')).not.toBeInTheDocument();
        typeAnswer('pear');
        activate('Teszt Beküldése');
        const summary = await screen.findByRole('heading', { name: 'Sikeres Beküldés!' });
        expect(summary).toHaveFocus();
        expect(api.post).not.toHaveBeenCalled();
        expect(assignmentApi.submitAssignment.mock.calls[0][1].map(item => item.answer)).toEqual(['apple', 'pear']);
    });

    it('announces practice feedback without moving focus', async () => {
        openAssignment({ feedback: true });
        typeAnswer('apple');
        const action = activate('Ellenőrzés');
        await waitFor(() => expect(screen.getByRole('status')).toHaveTextContent('Helyes válasz.'));
        expect(action).toHaveFocus();
        activate('Tovább');
        expect(screen.getByRole('heading', { level: 1 })).toHaveFocus();
    });

    it('retries a failed submission without losing or duplicating answers', async () => {
        vi.spyOn(console, 'error').mockImplementation(() => {});
        assignmentApi.submitAssignment.mockRejectedValueOnce(new Error('offline'));
        openAssignment({ items: [questions[0]] });
        typeAnswer('apple');
        activate('Teszt Beküldése');
        const retry = await screen.findByRole('button', { name: 'Beadás újrapróbálása' });
        expect(retry).toHaveFocus();
        activate('Beadás újrapróbálása');
        await screen.findByRole('heading', { name: 'Sikeres Beküldés!' });
        expect(assignmentApi.submitAssignment).toHaveBeenCalledTimes(2);
        expect(assignmentApi.submitAssignment.mock.calls[1][1]).toEqual(assignmentApi.submitAssignment.mock.calls[0][1]);
    });
});

describe('self-paced vocabulary practice', () => {
    it('retains feedback until Next is chosen, then focuses the new question', async () => {
        await openPractice();
        expect(screen.getByRole('progressbar', { name: 'Szókincsgyakorlás előrehaladása' })).toBeInTheDocument();
        const input = typeAnswer('alma');
        fireEvent.keyDown(input, { key: 'Enter' });
        expect(screen.getByRole('status')).toHaveTextContent('Tökéletes!');
        expect(screen.getByRole('button', { name: 'Következő feladat' })).toHaveFocus();
        vi.useFakeTimers();
        act(() => vi.advanceTimersByTime(10_000));
        expect(screen.getByRole('textbox')).toHaveValue('alma');
        expect(api.post).not.toHaveBeenCalled();
        vi.useRealTimers();
        activate('Következő feladat');
        expect(screen.getByRole('heading', { level: 1 })).toHaveFocus();
        expect(screen.getByRole('textbox')).toHaveValue('');
        expect(screen.getByRole('heading', { level: 1 })).toHaveAccessibleName(/^Fordítás\. 2\. feladat, összesen 2\..*pear/);
        expect(screen.getByText('2. feladat, összesen 2.', { exact: true })).toHaveAttribute('lang', 'hu');
    });

    it('keeps feedback and retries failed saving without recording another answer', async () => {
        vi.spyOn(console, 'error').mockImplementation(() => {});
        api.get.mockResolvedValueOnce({ data: [practice[0]] });
        await openPractice();
        typeAnswer('alma');
        activate('Ellenőrzés');
        expect(api.post).not.toHaveBeenCalled();
        api.post.mockRejectedValueOnce(new Error('offline')).mockResolvedValueOnce({ data: { correctCount: 1, totalQuestions: 1, earnedXp: 10, newStreak: 1 } });
        activate('Befejezés és mentés');
        await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent('A válaszaid megmaradtak'));
        expect(screen.getByRole('status')).toHaveTextContent('Tökéletes!');
        activate('Mentés újrapróbálása');
        const result = await screen.findByRole('heading', { name: 'Dinamikus gyakorlás teljesítve!' });
        expect(result).toHaveFocus();
        expect(screen.getByText('1 feladatból 1 helyes válasz')).toBeInTheDocument();
        expect(api.post).toHaveBeenCalledTimes(2);
        expect(api.post.mock.calls[1][1].results).toEqual([{ targetWord: 'apple', correct: true }]);
    });
});
