import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, expect, it, vi } from 'vitest';
import ClickableText from '../ClickableText';
import LanguageText from '../LanguageText';
import ExercisePreviewCard from '../ExercisePreviewCard';
import ImageChoiceExercise from '../exercises/ImageChoiceExercise';
import TranslationHint from '../TranslationHint';

vi.mock('../../context/AuthContext', () => ({ useAuth: () => ({ vocabularyMap: { apple: 4 }, user: { activeCourseCode: 'en' } }) }));
vi.mock('../../services/vocabularyApi', () => ({ vocabularyApi: { lookupWord: vi.fn() } }));
afterEach(cleanup);
const question = "How do you say 'Anya'?";
const parts = [{ text: 'How do you say ', lang: 'en' }, { text: "'Anya'", lang: 'hu' }, { text: '?', lang: 'en' }];

it('marks word languages without mixing Hungarian actions into foreign button names', () => {
    render(<ClickableText text="The apple is red." lang="en" />);
    expect(screen.getByText('The').closest('[lang]')).toHaveAttribute('lang', 'en');
    expect(screen.getByText('apple').closest('[lang]')).toHaveAttribute('lang', 'en');
    expect(screen.getByRole('button', { name: 'red' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'red' })).toHaveAttribute('lang', 'en');
    expect(screen.getByRole('button', { name: 'red' })).toHaveAccessibleName('red');
});

it('marks an explicitly Hungarian quotation without losing the original question', () => {
    const { container } = render(<ClickableText text={question} lang="en" languageParts={parts} disabled />);
    expect(container).toHaveTextContent(question);
    expect(screen.getByText("'Anya'")).toHaveAttribute('lang', 'hu');
    expect(screen.queryByRole('button')).not.toBeInTheDocument();
});

it('does not replace the question with inconsistent language metadata', () => {
    render(<LanguageText text="Original" lang="de" parts={[{ text: 'Wrong', lang: 'hu' }]} />);
    expect(screen.getByText('Original')).toHaveAttribute('lang', 'de');
    expect(screen.queryByText('Wrong')).not.toBeInTheDocument();
});

it('uses the same purpose description and option language in preview and image player', () => {
    const exercise = { type: 'IMAGE_CHOICE', imageUrl: '/test.png', content: {
        question: 'Válaszd ki a képen látható szó jelentését!', questionLang: 'hu', answerLang: 'de', options: ['Apfel', 'Birne'],
        imagePurpose: 'visual-assessment', imageAlt: 'Képfelismerési szókincsfeladat', imageAltLang: 'hu',
    } };
    render(<><ExercisePreviewCard exercise={exercise} /><ImageChoiceExercise exercise={exercise} onAnswer={vi.fn()} /></>);
    const images = screen.getAllByRole('img', { name: 'Képfelismerési szókincsfeladat' });
    expect(images).toHaveLength(2); images.forEach(img => expect(img).toHaveAttribute('lang', 'hu'));
    expect(screen.getByRole('radio', { name: 'Apfel' }).parentElement.querySelector('[lang]')).toHaveAttribute('lang', 'de');
    expect(screen.getAllByText('Apfel')[0].closest('[lang]')).toHaveAttribute('lang', 'de');
});

it('marks mixed question segments and correct answer in the teacher preview', () => {
    render(<ExercisePreviewCard exercise={{ content: { question, questionParts: parts, questionLang: 'en', answerLang: 'en' } }} serverCorrectAnswer="Mother" />);
    expect(screen.getByText("'Anya'")).toHaveAttribute('lang', 'hu');
    expect(screen.getByText('Mother')).toHaveAttribute('lang', 'en');
});

it('keeps Hungarian and target hint segments identical in player and preview', () => {
    const content = { hint: 'alma = apple, piros = red', hintDirection: 'hu-to-target', hintParts: [
        { text: 'alma', lang: 'hu' }, { text: ' = ', lang: 'hu' }, { text: 'apple', lang: 'en' },
        { text: ', ', lang: 'hu' }, { text: 'piros', lang: 'hu' }, { text: ' = ', lang: 'hu' }, { text: 'red', lang: 'en' },
    ] };
    render(<><TranslationHint content={content} /><ExercisePreviewCard exercise={{ content }} /></>);
    for (const text of ['apple', 'red']) screen.getAllByText(text).forEach(word => expect(word).toHaveAttribute('lang', 'en'));
    for (const text of ['alma', 'piros']) screen.getAllByText(text).forEach(word => expect(word).toHaveAttribute('lang', 'hu'));
});

it('preserves legacy or inconsistent hint text without inventing language segments', () => {
    const { container } = render(<TranslationHint content={{ hint: 'alma = apple', hintParts: [{ text: 'different', lang: 'de' }] }} />);
    expect(container).toHaveTextContent('alma = apple');
    expect(screen.queryByText('different')).not.toBeInTheDocument();
});
