export const MASTERY_LEVELS = ['EASY', 'MEDIUM', 'HARD'];
export const LEVEL_NAMES = { EASY: 'Kezdő', MEDIUM: 'Haladó', HARD: 'Profi' };

export function getMasteryRecommendation(lessons) {
    const missing = lesson => MASTERY_LEVELS.filter(level => lesson.availableDifficulties?.includes(level)
        && !lesson.completedDifficulties?.includes(level));
    const candidates = lessons.filter(lesson => lesson.completed && missing(lesson).length > 0);
    // Finish the closest topic, then the closest lesson; keep the learning path's order for ties.
    const topicRemaining = lesson => lessons.filter(item => item.topicName === lesson.topicName)
        .reduce((count, item) => count + missing(item).length, 0);
    const next = candidates.toSorted((a, b) => topicRemaining(a) - topicRemaining(b)
        || missing(a).length - missing(b).length)[0];
    if (!next) return null;
    const levels = missing(next);
    return { lesson: next, difficulty: levels.includes(next.difficulty) ? next.difficulty : levels[0],
        remaining: levels.length };
}
