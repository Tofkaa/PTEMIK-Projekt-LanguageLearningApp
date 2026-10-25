import { cleanup, fireEvent, render, screen, within } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import LessonMastery from '../LessonMastery';
import MasteryStars from '../MasteryStars';

afterEach(cleanup);
const lesson = { lessonId: 'fruit', title: 'Gyümölcsök', completed: true,
    availableDifficulties: ['EASY', 'MEDIUM'], completedDifficulties: ['EASY'], mastered: false };

describe('mastery replay', () => {
    it('synchronises star selection and the dropdown without starting a lesson', () => {
        const replay = vi.fn();
        render(<LessonMastery lesson={{ ...lesson, availableDifficulties: ['EASY', 'MEDIUM', 'HARD'] }} onReplay={replay} />);
        expect(screen.queryByRole('button', { name: /szint kiválasztása/ })).not.toBeInTheDocument();
        fireEvent.click(screen.getByRole('button', { name: 'Szintek és gyakorlás: Gyümölcsök' }));
        const select = screen.getByRole('combobox', { name: 'Ismétlés szintje: Gyümölcsök' });
        const hard = screen.getByRole('button', { name: 'Profi szint kiválasztása: Gyümölcsök, még nincs teljesítve' });
        const easy = screen.getByRole('button', { name: 'Kezdő szint kiválasztása: Gyümölcsök, teljesítve' });
        expect(hard).toHaveAttribute('type', 'button');
        fireEvent.click(hard);
        expect(select).toHaveValue('HARD'); expect(hard).toHaveAttribute('aria-pressed', 'true');
        expect(replay).not.toHaveBeenCalled();
        fireEvent.change(select, { target: { value: 'EASY' } });
        expect(easy).toHaveAttribute('aria-pressed', 'true'); expect(hard).toHaveAttribute('aria-pressed', 'false');
        fireEvent.click(hard); fireEvent.submit(select.closest('form'));
        expect(replay).toHaveBeenCalledWith('HARD');
    });

    it('does not make unavailable levels or uncompleted lessons selectable', () => {
        const select = vi.fn(); render(<MasteryStars lesson={lesson} onSelect={select} selectedLevel="MEDIUM" />);
        expect(screen.queryByRole('button', { name: /Profi szint kiválasztása/ })).not.toBeInTheDocument();
        cleanup();
        render(<LessonMastery lesson={{ ...lesson, completed: false }} onReplay={vi.fn()} />);
        fireEvent.click(screen.getByRole('button', { name: 'Szintek és gyakorlás: Gyümölcsök' }));
        expect(screen.queryByRole('button', { name: /szint kiválasztása/ })).not.toBeInTheDocument();
    });
    it('keeps three fixed star positions, distinguishes unavailable levels, and uses Hungarian labels', () => {
        render(<MasteryStars lesson={lesson} />);
        const levels = within(screen.getByRole('list', { name: 'Gyümölcsök: szintek teljesítése' })).getAllByRole('listitem');
        expect(levels).toHaveLength(3);
        expect(levels[0]).toHaveTextContent('Kezdő');
        expect(levels[0]).toHaveClass('mastery-star--earned');
        expect(levels[1]).toHaveTextContent('még nincs teljesítve');
        expect(levels[1]).not.toHaveClass('mastery-star--earned');
        expect(levels[2]).toHaveTextContent('Profi');
        expect(levels[2]).toHaveTextContent('nincs feladat');
        expect(levels[2]).toHaveClass('mastery-star--unavailable');
        expect(screen.queryByText(/EASY|MEDIUM|HARD/)).not.toBeInTheDocument();
    });

    it('fills the completed level in its own position, independently of completion order', () => {
        render(<MasteryStars lesson={{ ...lesson, availableDifficulties: ['EASY', 'MEDIUM', 'HARD'], completedDifficulties: ['HARD'] }} />);
        const levels = screen.getAllByRole('listitem');
        expect(levels[0]).not.toHaveClass('mastery-star--earned');
        expect(levels[1]).not.toHaveClass('mastery-star--earned');
        expect(levels[2]).toHaveClass('mastery-star--earned');
    });
    it('offers only available levels and defaults to a missing level without changing the profile', () => {
        const replay = vi.fn(); render(<LessonMastery lesson={lesson} onReplay={replay} />);
        fireEvent.click(screen.getByRole('button', { name: 'Szintek és gyakorlás: Gyümölcsök' }));
        const select = screen.getByRole('combobox', { name: 'Ismétlés szintje: Gyümölcsök' });
        expect(select).toHaveValue('MEDIUM');
        expect(within(select).getAllByRole('option')).toHaveLength(2);
        expect(select).toHaveAccessibleDescription('Válaszd ki, melyik elérhető szintet szeretnéd gyakorolni.');
        fireEvent.submit(select.closest('form'));
        expect(replay).toHaveBeenCalledWith('MEDIUM');
        fireEvent.change(select, { target: { value: 'EASY' } });
        fireEvent.submit(select.closest('form'));
        expect(replay).toHaveBeenLastCalledWith('EASY');
    });
    it('exposes meaningful progress and the mastery badge', () => {
        render(<LessonMastery lesson={{ ...lesson, completedDifficulties: ['EASY', 'MEDIUM'], mastered: true }} onReplay={vi.fn()} />);
        expect(screen.getByText('Leckemastery teljesítve.')).toBeInTheDocument();
        expect(screen.getByRole('progressbar')).toHaveAttribute('aria-valuetext', '2 elérhető szintből 2 teljesítve');
        expect(screen.getByRole('progressbar')).toHaveAttribute('aria-valuenow', '2');
    });
    it('requires an initial successful completion before offering level overrides', () => {
        render(<LessonMastery lesson={{ ...lesson, completed: false, completedDifficulties: [] }} onReplay={vi.fn()} />);
        fireEvent.click(screen.getByRole('button', { name: 'Szintek és gyakorlás: Gyümölcsök' }));
        expect(screen.queryByRole('combobox')).not.toBeInTheDocument();
        expect(screen.getByRole('progressbar')).toHaveAttribute('aria-valuenow', '0');
    });

    it('starts compact and expands each lesson independently without exposing hidden form controls', () => {
        render(<>
            <LessonMastery lesson={lesson} onReplay={vi.fn()} />
            <LessonMastery lesson={{ ...lesson, lessonId: 'family', title: 'Családtagok' }} onReplay={vi.fn()} />
        </>);
        const toggle = screen.getByRole('button', { name: 'Szintek és gyakorlás: Gyümölcsök' });
        expect(toggle).toHaveAttribute('aria-expanded', 'false');
        expect(screen.queryByRole('combobox')).not.toBeInTheDocument();
        toggle.focus(); fireEvent.click(toggle);
        expect(toggle).toHaveAttribute('aria-expanded', 'true');
        expect(screen.getByRole('combobox', { name: 'Ismétlés szintje: Gyümölcsök' })).toBeInTheDocument();
        expect(screen.queryByRole('combobox', { name: 'Ismétlés szintje: Családtagok' })).not.toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Szintek és gyakorlás: Családtagok' })).toHaveAttribute('aria-expanded', 'false');
        expect(document.getElementById(toggle.getAttribute('aria-controls'))).not.toHaveAttribute('hidden');
        fireEvent.click(toggle);
        expect(toggle).toHaveFocus();
        expect(screen.queryByRole('combobox')).not.toBeInTheDocument();
        expect(screen.queryByText(/Az első teljesítésért/)).not.toBeInTheDocument();
    });
});
