import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import FlashcardPractice from '../../pages/FlashcardPractice';
import VocabularyPractice from '../../pages/VocabularyPractice';
import SmartWord from '../SmartWord';
import { VocabularyCourseProvider } from '../../context/VocabularyCourseContext';
import ClickableText from '../ClickableText';
import api from '../../services/api';
import { vocabularyApi } from '../../services/vocabularyApi';

const state = vi.hoisted(() => ({ user: { userId: 'one', activeCourseCode: 'en', activeCourseId: 'course-en' }, refresh: vi.fn() }));
vi.mock('../../context/AuthContext', () => ({ useAuth: () => ({ user: state.user }) }));
vi.mock('../../context/NotificationContext', () => ({ useNotifications: () => ({ refreshNotifications: state.refresh }) }));
vi.mock('../../services/api', () => ({ default: { get: vi.fn(), post: vi.fn() } }));
vi.mock('../../services/vocabularyApi', () => ({ vocabularyApi: { lookupWord: vi.fn() } }));
let requests;
const exercises = word => [{ type: 'TRANSLATION', question: `Fordítsd: ${word}`, correctAnswer: 'alma', targetWord: word }];
beforeEach(() => {
    vi.clearAllMocks(); requests = [];
    state.user = { userId: 'one', activeCourseCode: 'en', activeCourseId: 'course-en' };
    api.get.mockImplementation((url, config) => new Promise(resolve => requests.push({ url, ...config, resolve })));
    api.post.mockImplementation((url, body, config) => url === '/vocabulary/practice/start'
        ? new Promise(resolve => requests.push({ url, ...config, resolve }))
        : Promise.resolve({ data: { answer: body.answer, correct: true, correctAnswer: 'alma' } }));
});
afterEach(cleanup);

it.each([
    [FlashcardPractice, word => [{ word, translation: 'alma' }]],
    [VocabularyPractice, word => ({ attemptId: 'practice', exercises: exercises(word) })],
])('discards a replaced practice load even if its adapter ignores abort', async (Component, data) => {
    const view = render(<MemoryRouter><Component /></MemoryRouter>);
    await waitFor(() => expect(requests).toHaveLength(1));
    state.user = { ...state.user, activeCourseCode: 'de', activeCourseId: 'course-de' };
    view.rerender(<MemoryRouter><Component /></MemoryRouter>);
    expect(requests[0].signal.aborted).toBe(true);
    await act(() => requests[1].resolve({ data: data('Apfel') }));
    await act(() => requests[0].resolve({ data: data('apple') }));
    expect(screen.getByText(/Apfel/)).toBeInTheDocument();
    expect(screen.queryByText(/apple/)).not.toBeInTheDocument();
});

it('resets revealed flashcards when another account uses the same course', async () => {
    const view = render(<MemoryRouter><FlashcardPractice /></MemoryRouter>);
    await act(() => requests[0].resolve({ data: [{ word: 'apple', translation: 'alma' }] }));
    fireEvent.click(screen.getByRole('button', { name: 'Megnézem a választ' }));
    state.user = { ...state.user, userId: 'two' };
    view.rerender(<MemoryRouter><FlashcardPractice /></MemoryRouter>);
    expect(screen.queryByText('apple')).not.toBeInTheDocument();
    await act(() => requests[1].resolve({ data: [{ word: 'pear', translation: 'körte' }] }));
    expect(screen.getByRole('button', { name: 'Megnézem a választ' })).toBeInTheDocument();
});

it('aborts a superseded submission and ignores its late result and notification refresh', async () => {
    let finish, signal;
    api.post.mockImplementation((url, body, config) => {
        if (url === '/vocabulary/practice/start') return new Promise(resolve => requests.push({ url, ...config, resolve }));
        if (url === '/vocabulary/practice/check') return Promise.resolve({ data: { answer: body.answer, correct: true, correctAnswer: 'alma' } });
        signal = config.signal;
        return new Promise(resolve => { finish = resolve; });
    });
    const view = render(<MemoryRouter><VocabularyPractice /></MemoryRouter>);
    await act(() => requests[0].resolve({ data: { attemptId: 'practice', exercises: exercises('apple') } }));
    fireEvent.change(screen.getByRole('textbox', { name: 'Válaszod' }), { target: { value: 'alma' } });
    fireEvent.click(screen.getByRole('button', { name: 'Ellenőrzés' }));
    fireEvent.click(await screen.findByRole('button', { name: 'Befejezés és mentés' }));
    await waitFor(() => expect(finish).toBeTypeOf('function'));
    state.user = { ...state.user, activeCourseCode: 'de', activeCourseId: 'course-de' };
    view.rerender(<MemoryRouter><VocabularyPractice /></MemoryRouter>);
    expect(signal.aborted).toBe(true);
    await act(() => finish({ data: { xpEarned: 10 } }));
    expect(screen.queryByText('Dinamikus gyakorlás teljesítve!')).not.toBeInTheDocument();
    expect(state.refresh).not.toHaveBeenCalled();
});

it.each(['query', 'course'])('isolates a pending translation after a %s change', async kind => {
    const lookups = [];
    vocabularyApi.lookupWord.mockImplementation((word, source, config) => new Promise(resolve => lookups.push({ word, ...config, resolve })));
    const view = render(<SmartWord word="Test" queryWord="first" />);
    fireEvent.click(screen.getByRole('button', { name: 'Test' }));
    if (kind === 'course') state.user = { ...state.user, activeCourseCode: 'de', activeCourseId: 'course-de' };
    view.rerender(<SmartWord word="Test" queryWord={kind === 'query' ? 'second' : 'first'} />);
    expect(lookups[0].signal.aborted).toBe(true);
    fireEvent.click(screen.getByRole('button', { name: 'Test' }));
    await act(() => lookups[1].resolve({ data: { translation: 'új fordítás', srsLevel: 1 } }));
    await act(() => lookups[0].resolve({ data: { translation: 'régi fordítás', srsLevel: 1 } }));
    expect(screen.getByRole('dialog')).toHaveTextContent('új fordítás');
    expect(screen.getByRole('dialog')).not.toHaveTextContent('régi fordítás');
});

it('aborts an old course answer check and ignores its late feedback', async () => {
    let finish, signal;
    api.post.mockImplementation((url, body, config) => {
        if (url === '/vocabulary/practice/start') return new Promise(resolve => requests.push({ url, ...config, resolve }));
        signal = config.signal;
        return new Promise(resolve => { finish = resolve; });
    });
    const view = render(<MemoryRouter><VocabularyPractice /></MemoryRouter>);
    await act(() => requests[0].resolve({ data: { attemptId: 'practice', exercises: [{ ...exercises('apple')[0], exerciseId: 'one' }] } }));
    fireEvent.change(screen.getByRole('textbox'), { target: { value: 'alma' } });
    fireEvent.click(screen.getByRole('button', { name: 'Ellenőrzés' }));
    await waitFor(() => expect(finish).toBeTypeOf('function'));
    state.user = { ...state.user, activeCourseCode: 'de', activeCourseId: 'course-de' };
    view.rerender(<MemoryRouter><VocabularyPractice /></MemoryRouter>);
    expect(signal.aborted).toBe(true);
    await act(() => finish({ data: { answer: 'alma', correct: true, correctAnswer: 'alma' } }));
    expect(screen.queryByText(/Tökéletes!/)).not.toBeInTheDocument();
    const starts = api.post.mock.calls.filter(([url]) => url === '/vocabulary/practice/start');
    expect(starts.at(-1)[1]).toEqual({ courseId: 'course-de' });
});

it('keeps a fixed lesson translation in its original course after a global course switch', async () => {
    const lookups = [];
    vocabularyApi.lookupWord.mockImplementation((word, source, config) => new Promise(resolve => lookups.push({ word, ...config, resolve })));
    const content = <VocabularyCourseProvider courseId="course-en" courseCode="en"><SmartWord word="apple" /></VocabularyCourseProvider>;
    const view = render(content);fireEvent.click(screen.getByRole('button', { name: 'apple' }));
    expect(lookups[0].courseId).toBe('course-en');
    state.user = { ...state.user, activeCourseId: 'course-de', activeCourseCode: 'de', activeCourseRevision: 1 };
    view.rerender(<VocabularyCourseProvider courseId="course-en" courseCode="en"><SmartWord word="apple" /></VocabularyCourseProvider>);
    expect(lookups[0].signal.aborted).toBe(false);
    await act(() => lookups[0].resolve({ data: { translation: 'alma', srsLevel: 1 } }));
    expect(screen.getByRole('dialog')).toHaveTextContent('alma');
});
it('does not apply the active dictionary known flags to a different fixed lesson course', () => {
    state.user = { ...state.user, activeCourseId: 'course-de', activeCourseCode: 'de' };
    render(<VocabularyCourseProvider courseId="course-en" courseCode="en"><ClickableText text="apple" /></VocabularyCourseProvider>);
    expect(screen.getByRole('button', { name: 'apple' })).toHaveAttribute('lang', 'en');
});
it('replaces practice state after an ABA course switch with a newer revision', async () => {
    state.user.activeCourseRevision = 0;
    const view=render(<MemoryRouter><FlashcardPractice /></MemoryRouter>);
    await act(() => requests[0].resolve({ data: [{ word: 'apple', translation: 'alma' }] }));
    state.user = { ...state.user, activeCourseRevision: 2 };
    view.rerender(<MemoryRouter><FlashcardPractice /></MemoryRouter>);
    expect(screen.queryByText('apple')).not.toBeInTheDocument();
    expect(requests[1].params.courseId).toBe('course-en');
});
