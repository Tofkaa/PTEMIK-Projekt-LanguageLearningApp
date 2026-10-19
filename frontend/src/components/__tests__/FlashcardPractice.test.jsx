import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import FlashcardPractice from '../../pages/FlashcardPractice';
import api from '../../services/api';

vi.mock('../../services/api', () => ({ default: { get: vi.fn() } }));
vi.mock('../../context/AuthContext', () => ({ useAuth: () => ({ user: { activeCourseCode: 'en' } }) }));

const words = [{ word: 'apple', translation: 'alma' }, { word: 'pear', translation: 'körte' }];
beforeEach(() => {
    vi.resetAllMocks();
    vi.spyOn(Math, 'random').mockReturnValue(0.5);
    api.get.mockResolvedValue({ data: words });
});
afterEach(() => { cleanup(); vi.restoreAllMocks(); });

async function openCards() {
    render(<MemoryRouter initialEntries={['/cards']}><Routes>
        <Route path="/cards" element={<FlashcardPractice />} />
        <Route path="/hub" element={<h1>Szótár</h1>} />
    </Routes></MemoryRouter>);
    await screen.findByRole('button', { name: 'Megnézem a választ' });
    await waitFor(() => expect(screen.getByRole('heading', { level: 1 })).toHaveFocus());
}

describe('flashcard learning flow', () => {
    it.each(['Enter', ' '])('reveals and advances using %s without skipping a card on key repeat', async key => {
        await openCards();
        const face = screen.getByRole('button', { name: 'apple', exact: true });
        expect(screen.getByRole('heading', { level: 1 }).compareDocumentPosition(face) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
        face.focus();
        fireEvent.keyDown(face, { key });
        const next = screen.getByRole('button', { name: 'Következő kártya ➔' });
        await waitFor(() => expect(next).toHaveFocus());
        fireEvent.keyDown(next, { key, repeat: true });
        expect(screen.getByText('apple')).toBeInTheDocument();
        fireEvent.keyDown(next, { key });
        expect(screen.getByRole('heading', { level: 1 })).toHaveFocus();
        expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent('pear');
        fireEvent.keyDown(screen.getByRole('button', { name: 'Megnézem a választ' }), { key });
        const finish = screen.getByRole('button', { name: 'Gyakorlás befejezése' });
        fireEvent.keyDown(finish, { key });
        expect(screen.getByRole('heading', { name: 'Szótár' })).toBeInTheDocument();
    });
    it('reveals the meaning with a native button, announces it, and keeps the next action focused', async () => {
        await openCards();
        expect(screen.getByRole('progressbar', { name: 'Szókártyás gyakorlás előrehaladása' })).toHaveAttribute('aria-valuenow', '0');
        expect(screen.getByRole('status')).toBeEmptyDOMElement();
        const face = screen.getByRole('button', { name: 'apple' });
        expect(face).toHaveAttribute('aria-expanded', 'false');
        fireEvent.click(face);
        expect(face).toHaveAttribute('aria-expanded', 'true');
        expect(face).toBeDisabled();
        expect(screen.getByRole('status')).toHaveTextContent('alma');
        expect(screen.getByRole('button', { name: 'Következő kártya ➔' })).toHaveFocus();
        expect(screen.getByText('apple')).toHaveAttribute('lang', 'en');
        expect(screen.getByRole('heading', { name: 'alma' })).toHaveAttribute('lang', 'hu');
    });

    it('also reveals via the lower action and focuses the next unrevealed card', async () => {
        await openCards();
        fireEvent.click(screen.getByRole('button', { name: 'Megnézem a választ' }));
        const next = screen.getByRole('button', { name: 'Következő kártya ➔' });
        next.focus();
        fireEvent.click(next);
        expect(screen.getByRole('heading', { level: 1 })).toHaveFocus();
        expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent('pear');
        expect(screen.getByRole('status')).toBeEmptyDOMElement();
        expect(screen.getByRole('button', { name: 'pear' })).toBeEnabled();
        expect(screen.getByRole('progressbar')).toHaveAttribute('aria-valuenow', '50');
        fireEvent.click(screen.getByRole('button', { name: 'Megnézem a választ' }));
        fireEvent.click(screen.getByRole('button', { name: 'Gyakorlás befejezése' }));
        expect(screen.getByRole('heading', { name: 'Szótár' })).toBeInTheDocument();
    });

    it.each(['empty', 'error'])('focuses the %s state and provides a working exit', async state => {
        if (state === 'error') {
            vi.spyOn(console, 'error').mockImplementation(() => {});
            api.get.mockRejectedValue(new Error('offline'));
        } else api.get.mockResolvedValue({ data: [] });
        render(<MemoryRouter><FlashcardPractice /></MemoryRouter>);
        const heading = await screen.findByRole('heading', { name: state === 'error'
            ? 'Nem sikerült betölteni a szótárat.' : 'Nincsenek még szavaid a gyakorláshoz!' });
        await waitFor(() => expect(heading).toHaveFocus());
        expect(screen.getByRole('button', { name: 'Vissza a Szótárba' })).toBeEnabled();
    });
});
