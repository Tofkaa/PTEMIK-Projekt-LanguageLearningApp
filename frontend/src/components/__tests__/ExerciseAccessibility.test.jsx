import { useState } from 'react';
import { cleanup, fireEvent, render, screen, within } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import MultipleChoiceExercise from '../exercises/MultipleChoiceExercise';
import ImageChoiceExercise from '../exercises/ImageChoiceExercise';
import WordBankExercise from '../exercises/WordBankExercise';

afterEach(cleanup);

function ChoiceExample({ image = false }) {
    const [answer, setAnswer] = useState('');
    const data = { options: ['Apple', 'Pear'], answerLang: 'en' };
    return image
        ? <ImageChoiceExercise exercise={{ content: data }} currentAnswer={answer} onAnswer={setAnswer} />
        : <MultipleChoiceExercise data={data} currentAnswer={answer} onAnswer={setAnswer} />;
}

function WordBankExample({ options = ['I', 'like', 'apples'], disabled = false }) {
    const [answer, setAnswer] = useState('');
    return <WordBankExercise data={{ options, answerLang: 'en' }} currentAnswer={answer} onAnswer={setAnswer} disabled={disabled} />;
}

describe('accessible exercise controls', () => {
    it.each([false, true])('exposes one selected radio answer (image=%s)', image => {
        render(<ChoiceExample image={image} />);
        expect(screen.getByRole('group', { name: 'Válaszlehetőségek' })).toBeInTheDocument();
        const apple = screen.getByRole('radio', { name: 'Apple' });
        const pear = screen.getByRole('radio', { name: 'Pear' });
        expect(apple).toHaveAttribute('lang', 'en');
        expect(apple).not.toBeChecked();
        fireEvent.click(apple);
        expect(apple).toBeChecked();
        fireEvent.click(pear);
        expect(pear).toBeChecked();
        expect(apple).not.toBeChecked();
        expect(apple.name).toBe(pear.name);
    });

    it('keeps separate exercise radio groups independent', () => {
        render(<><ChoiceExample /><ChoiceExample /></>);
        const groups = screen.getAllByRole('group');
        const first = within(groups[0]).getByRole('radio', { name: 'Apple' });
        const second = within(groups[1]).getByRole('radio', { name: 'Pear' });
        fireEvent.click(first);
        fireEvent.click(second);
        expect(first).toBeChecked();
        expect(second).toBeChecked();
        expect(first.name).not.toBe(second.name);
    });

    it.each([MultipleChoiceExercise, ImageChoiceExercise])('locks selected answers when input is disabled', component => {
        const Component = component;
        const onAnswer = vi.fn();
        render(<Component data={{ options: ['Apple'] }} exercise={{ content: { options: ['Apple'] } }}
            currentAnswer="Apple" onAnswer={onAnswer} disabled />);
        const answer = screen.getByRole('radio');
        expect(answer).toBeDisabled();
        expect(answer).toBeChecked();
        fireEvent.click(answer);
        expect(onAnswer).not.toHaveBeenCalled();
    });

    it('adds and removes words while keeping focus on an available control', () => {
        render(<WordBankExample />);
        fireEvent.click(screen.getByRole('button', { name: 'I' }));
        expect(screen.getByRole('button', { name: 'like' })).toHaveFocus();
        expect(screen.getByRole('button', { name: 'like' })).toHaveAttribute('lang', 'en');
        expect(screen.getByRole('button', { name: 'I' })).toHaveAttribute('lang', 'en');
        expect(screen.getByText(/Enter vagy Szóköz hozzáadja/)).toHaveAttribute('lang', 'hu');
        expect(screen.getByRole('status')).toHaveTextContent('1 szó az összeállított válaszban.');
        const sentence = screen.getByText('I', { selector: 'p.word-bank-sentence' });
        expect(sentence).toHaveAttribute('lang', 'en');
        expect(sentence).toHaveAttribute('tabindex', '0');
        sentence.focus();
        expect(sentence).toHaveFocus();
        expect(screen.getByRole('status')).toHaveAttribute('lang', 'hu');
        expect(screen.getByRole('status')).toHaveAttribute('aria-atomic', 'true');
        fireEvent.click(screen.getByRole('button', { name: 'like' }));
        fireEvent.click(screen.getByRole('button', { name: 'apples' }));
        expect(screen.getByRole('button', { name: 'apples' })).toHaveFocus();
        fireEvent.click(screen.getByRole('button', { name: 'like' }));
        expect(screen.getByText('I apples', { selector: 'p.word-bank-sentence' })).toHaveAttribute('lang', 'en');
        expect(screen.getByRole('button', { name: 'apples' })).toHaveFocus();
        expect(screen.getByRole('button', { name: 'like' })).toBeInTheDocument();
    });

    it('preserves duplicate words and focus when the second identical token is chosen first', () => {
        render(<WordBankExample options={['very', 'very']} />);
        fireEvent.click(screen.getAllByRole('button', { name: 'very' })[1]);
        expect(within(screen.getByRole('group', { name: 'Választható szavak' })).getByRole('button', { name: 'very' })).toHaveFocus();
        fireEvent.click(within(screen.getByRole('group', { name: 'Választható szavak' })).getByRole('button', { name: 'very' }));
        expect(screen.getByRole('status')).toHaveTextContent('2 szó az összeállított válaszban.');
        expect(screen.getByText('very very', { selector: 'p.word-bank-sentence' })).toHaveAttribute('lang', 'en');
        expect(within(screen.getByRole('group', { name: 'Választható szavak' })).queryByRole('button', { name: 'very' })).not.toBeInTheDocument();
        fireEvent.click(within(screen.getByRole('group', { name: 'Összeállított válasz' })).getAllByRole('button', { name: 'very' })[0]);
        fireEvent.click(within(screen.getByRole('group', { name: 'Összeállított válasz' })).getAllByRole('button', { name: 'very' })[0]);
        expect(screen.getAllByRole('button', { name: 'very' })).toHaveLength(2);
        expect(screen.getByRole('status')).toHaveTextContent('Az összeállított válasz üres.');
        expect(screen.getAllByRole('button', { name: 'very' })[0]).toHaveFocus();
    });

    it('disables every word-bank action during checking', () => {
        render(<WordBankExample disabled />);
        screen.getAllByRole('button').forEach(button => expect(button).toBeDisabled());
        fireEvent.click(screen.getByRole('button', { name: 'I' }));
        expect(screen.getByRole('status')).toHaveTextContent('Az összeállított válasz üres.');
    });
});
