import { describe, expect, it } from 'vitest';
import { getMasteryRecommendation } from '../../utils/mastery';

const lesson = (id, topic, completed) => ({ lessonId: id, topicName: topic, completed: true,
    difficulty: 'MEDIUM', availableDifficulties: ['EASY', 'MEDIUM', 'HARD'], completedDifficulties: completed });

describe('mastery recommendation', () => {
    it('prefers a nearly finished topic and picks its missing level', () => {
        const next = getMasteryRecommendation([
            lesson('far', 'Big topic', ['EASY']), lesson('near', 'Small topic', ['EASY', 'MEDIUM']),
        ]);
        expect(next.lesson.lessonId).toBe('near'); expect(next.difficulty).toBe('HARD');
    });
    it('uses the profile level if it is missing, then preserves path order on ties', () => {
        const next = getMasteryRecommendation([lesson('first', 'Topic', ['EASY']), lesson('second', 'Topic', ['EASY'])]);
        expect(next.lesson.lessonId).toBe('first'); expect(next.difficulty).toBe('MEDIUM');
    });
    it('never recommends empty, already mastered, or not yet passed lessons for a manual replay', () => {
        expect(getMasteryRecommendation([
            lesson('done', 'Topic', ['EASY', 'MEDIUM', 'HARD']),
            { ...lesson('new', 'Topic', []), completed: false },
            { ...lesson('empty', 'Topic', []), availableDifficulties: [] },
        ])).toBeNull();
    });
});
