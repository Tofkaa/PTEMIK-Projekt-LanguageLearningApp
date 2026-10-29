import { afterEach, expect, it } from 'vitest';
import { getScopedCounter, setScopedCounter, vocabularyViewedScope } from './viewedStore';
afterEach(() => localStorage.clear());
it('keeps dictionary viewed counters per account and course, including revisiting the same course', () => {
    const a={ userId:'one',activeCourseId:'en',activeCourseRevision:0 };
    setScopedCounter('seenDueVocabCount',vocabularyViewedScope(a),7);
    expect(getScopedCounter('seenDueVocabCount',vocabularyViewedScope({...a,activeCourseId:'de'}))).toBe(0);
    expect(getScopedCounter('seenDueVocabCount',vocabularyViewedScope({...a,userId:'two'}))).toBe(0);
    expect(getScopedCounter('seenDueVocabCount',vocabularyViewedScope({...a,activeCourseRevision:2}))).toBe(7);
});
