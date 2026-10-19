import { act, cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import SmartWord from '../SmartWord';
import ClickableText from '../ClickableText';
import { vocabularyApi } from '../../services/vocabularyApi';

vi.mock('../../services/vocabularyApi', () => ({ vocabularyApi: { lookupWord: vi.fn() } }));
vi.mock('../../context/AuthContext', () => ({ useAuth: () => ({ vocabularyMap: {}, user: { activeCourseCode: 'en' } }) }));

beforeEach(() => vi.clearAllMocks());
afterEach(cleanup);

describe('accessible word translation', () => {
    it.each([
        ['alma = apple', 'hu-to-target'],
        ['apple = alma', undefined],
    ])('preserves preloaded dictionary hints for %s', async (hint, hintDirection) => {
        render(<ClickableText text="apple" lang="en" hint={hint} hintDirection={hintDirection} />);
        fireEvent.click(screen.getByRole('button', { name: 'apple' }));
        const dialog = await screen.findByRole('dialog', { name: 'Fordítás: apple' });
        expect(dialog).toHaveTextContent('alma');
        expect(vocabularyApi.lookupWord).not.toHaveBeenCalled();
    });
    it('opens a hint without a lookup and restores focus on Escape', async () => {
        render(<SmartWord word="Apple" lang="en" preloadedTranslation="alma" />);
        const trigger = screen.getByRole('button', { name: 'Apple' });
        expect(trigger).toHaveAttribute('aria-expanded', 'false');
        fireEvent.click(trigger);
        const dialog = await screen.findByRole('dialog', { name: 'Fordítás: Apple' });
        expect(trigger).toHaveAttribute('aria-controls', dialog.id);
        expect(dialog).toHaveTextContent('alma');
        await waitFor(() => expect(within(dialog).getByRole('button', { name: 'Bezárás' })).toHaveFocus());
        fireEvent.keyDown(within(dialog).getByRole('button'), { key: 'Escape' });
        expect(trigger).toHaveFocus();
        await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
        expect(trigger).toHaveAttribute('aria-expanded', 'false');
        expect(vocabularyApi.lookupWord).not.toHaveBeenCalled();
    });

    it('keeps the lookup/SRS callback, announces loading, and caches a successful lookup', async () => {
        let resolveLookup;
        vocabularyApi.lookupWord.mockReturnValue(new Promise(resolve => { resolveLookup = resolve; }));
        const onWordClick = vi.fn();
        render(<SmartWord word="Apple" queryWord="apple" source="LESSON" onWordClick={onWordClick} />);
        const trigger = screen.getByRole('button', { name: 'Apple' });
        fireEvent.click(trigger);
        expect(vocabularyApi.lookupWord).toHaveBeenCalledWith('apple', 'LESSON');
        expect(screen.getByRole('status')).toHaveTextContent('Keresés...');
        await act(async () => resolveLookup({ data: { translation: 'alma', srsLevel: 1, newAddition: true } }));
        expect(screen.getByRole('status')).toHaveTextContent('alma');
        expect(screen.getByRole('status')).toHaveTextContent('Új szótári bejegyzés');
        fireEvent.click(screen.getByRole('button', { name: 'Bezárás' }));
        await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
        fireEvent.click(trigger);
        expect(vocabularyApi.lookupWord).toHaveBeenCalledTimes(1);
        expect(onWordClick).toHaveBeenCalledTimes(2);
    });

    it('offers another attempt after a failed lookup', async () => {
        vocabularyApi.lookupWord.mockRejectedValueOnce(new Error('offline'))
            .mockResolvedValueOnce({ data: { translation: 'alma', srsLevel: 1 } });
        render(<SmartWord word="apple" />);
        const trigger = screen.getByRole('button', { name: 'apple' });
        fireEvent.click(trigger);
        await waitFor(() => expect(screen.getByRole('status')).toHaveTextContent('Fordítás nem elérhető.'));
        fireEvent.click(screen.getByRole('button', { name: 'Bezárás' }));
        await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
        fireEvent.click(trigger);
        await waitFor(() => expect(screen.getByRole('status')).toHaveTextContent('alma'));
        expect(vocabularyApi.lookupWord).toHaveBeenCalledTimes(2);
    });

    it('uses unique panel ids for repeated words', async () => {
        render(<><SmartWord word="apple" preloadedTranslation="alma" />
            <SmartWord word="apple" preloadedTranslation="alma" /></>);
        const triggers = screen.getAllByRole('button', { name: 'apple' });
        fireEvent.click(triggers[0]);
        const firstId = (await screen.findByRole('dialog')).id;
        fireEvent.click(screen.getByRole('button', { name: 'Bezárás' }));
        await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
        fireEvent.click(triggers[1]);
        expect((await screen.findByRole('dialog')).id).not.toBe(firstId);
    });

    it('renders plain text in test mode and makes no lookup', () => {
        render(<SmartWord word="apple" lang="en" disabled />);
        expect(screen.getByText('apple')).toHaveAttribute('lang', 'en');
        expect(screen.queryByRole('button')).not.toBeInTheDocument();
        expect(vocabularyApi.lookupWord).not.toHaveBeenCalled();
    });

    it('does not reuse a previous question\'s cached translation at the same token position', async () => {
        const view = render(<ClickableText text="apple" hint="apple=alma" />);
        fireEvent.click(screen.getByRole('button', { name: 'apple' }));
        expect(await screen.findByRole('dialog')).toHaveTextContent('alma');
        view.rerender(<ClickableText text="pear" hint="pear=körte" />);
        fireEvent.click(screen.getByRole('button', { name: 'pear' }));
        const dialog = await screen.findByRole('dialog', { name: 'Fordítás: pear' });
        expect(dialog).toHaveTextContent('körte');
        expect(dialog).not.toHaveTextContent('alma');
        expect(vocabularyApi.lookupWord).not.toHaveBeenCalled();
    });
});
