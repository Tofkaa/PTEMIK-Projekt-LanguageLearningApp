import { LEVEL_NAMES, MASTERY_LEVELS } from '../utils/mastery';
import './MasteryStars.css';

export default function MasteryStars({ lesson, compact = false, selectedLevel, onSelect }) {
    const available = lesson.availableDifficulties || [];
    const completed = lesson.completedDifficulties || [];
    return <ul className={`mastery-stars${compact ? ' mastery-stars--compact' : ''}`}
        aria-label={`${lesson.title}: szintek teljesítése`}>
        {MASTERY_LEVELS.map(level => {
            const exists = available.includes(level);
            const earned = exists && completed.includes(level);
            const status = !exists ? 'nincs feladat' : earned ? 'teljesítve' : 'még nincs teljesítve';
            const selectable = !compact && exists && typeof onSelect === 'function';
            const content = <>
                <svg aria-hidden="true" focusable="false" viewBox="0 0 24 24" className="mastery-star-icon">
                    <path d="m12 2.7 2.87 5.82 6.43.93-4.65 4.53 1.1 6.4L12 17.36l-5.75 3.02 1.1-6.4L2.7 9.45l6.43-.93Z" />
                </svg>
                <span className={compact ? 'visually-hidden' : 'mastery-star-label'}>{LEVEL_NAMES[level]}{compact ? ': ' : ''}</span>
                <span className={compact ? 'visually-hidden' : 'mastery-star-status'}>
                    <span aria-hidden="true">{earned ? '✓ ' : ''}</span>{status}
                </span>
            </>;
            return <li key={level} className={`mastery-star${earned ? ' mastery-star--earned' : ''}${!exists ? ' mastery-star--unavailable' : ''}${selectable ? ' mastery-star--interactive' : ''}`}>
                {selectable ? <button type="button" className="mastery-star-choice"
                    aria-label={`${LEVEL_NAMES[level]} szint kiválasztása: ${lesson.title}, ${status}`}
                    aria-pressed={selectedLevel === level} onClick={() => onSelect(level)}>
                    {content}
                </button> : content}
            </li>;
        })}
    </ul>;
}
