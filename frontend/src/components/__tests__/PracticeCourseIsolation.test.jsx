import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import FlashcardPractice from '../../pages/FlashcardPractice';
import VocabularyPractice from '../../pages/VocabularyPractice';
import SmartWord from '../SmartWord';
import api from '../../services/api';
import { vocabularyApi } from '../../services/vocabularyApi';

const state = vi.hoisted(() => ({ user: { userId: 'one', activeCourseCode: 'en' }, refresh: vi.fn() }));
vi.mock('../../context/AuthContext', () => ({ useAuth: () => ({ user: state.user }) }));
vi.mock('../../context/NotificationContext', () => ({ useNotifications: () => ({ refreshNotifications: state.refresh }) }));
vi.mock('../../services/api', () => ({ default: { get: vi.fn(), post: vi.fn() } }));
vi.mock('../../services/vocabularyApi', () => ({ vocabularyApi: { lookupWord: vi.fn() } }));
let requests;
const exercises = word => [{ type: 'TRANSLATION', question: `Fordítsd: ${word}`, correctAnswer: 'alma', targetWord: word }];
beforeEach(() => {
    vi.clearAllMocks(); requests = [];
    state.user = { userId: 'one', activeCourseCode: 'en' };
    api.get.mockImplementation((url, config) => new Promise(resolve => requests.push({ url, ...config, resolve })));
});
afterEach(cleanup);

it.each([
    [FlashcardPractice, word => [{ word, translation: 'alma' }]],
    [VocabularyPractice, exercises],
])('discards a replaced practice load even if its adapter ignores abort', async (Component, data) => {
    const view = render(<MemoryRouter><Component /></MemoryRouter>);
    await waitFor(() => expect(requests).toHaveLength(1));
    state.user = { ...state.user, activeCourseCode: 'de' };
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
    api.post.mockImplementation((_, _body, config) => {
        signal = config.signal;
        return new Promise(resolve => { finish = resolve; });
    });
    const view = render(<MemoryRouter><VocabularyPractice /></MemoryRouter>);
    await act(() => requests[0].resolve({ data: exercises('apple') }));
    fireEvent.change(screen.getByRole('textbox', { name: 'Válaszod' }), { target: { value: 'alma' } });
    fireEvent.click(screen.getByRole('button', { name: 'Ellenőrzés' }));
    fireEvent.click(screen.getByRole('button', { name: 'Befejezés és mentés' }));
    await waitFor(() => expect(finish).toBeTypeOf('function'));
    state.user = { ...state.user, activeCourseCode: 'de' };
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
    if (kind === 'course') state.user = { ...state.user, activeCourseCode: 'de' };
    view.rerender(<SmartWord word="Test" queryWord={kind === 'query' ? 'second' : 'first'} />);
    expect(lookups[0].signal.aborted).toBe(true);
    fireEvent.click(screen.getByRole('button', { name: 'Test' }));
    await act(() => lookups[1].resolve({ data: { translation: 'új fordítás', srsLevel: 1 } }));
    await act(() => lookups[0].resolve({ data: { translation: 'régi fordítás', srsLevel: 1 } }));
    expect(screen.getByRole('dialog')).toHaveTextContent('új fordítás');
    expect(screen.getByRole('dialog')).not.toHaveTextContent('régi fordítás');
});

