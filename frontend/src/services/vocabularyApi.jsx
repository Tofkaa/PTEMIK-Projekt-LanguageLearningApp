import api from './api';

/**
 * Vocabulary and SRS management
 */
export const vocabularyApi = {


    lookupWord: (word, source = 'DICTIONARY_TOOLTIP', config) =>
        api.post('/vocabulary/lookup', { word, source, courseId: config?.courseId }, config),


    recordPractice: (vocabularyId, isCorrect, courseId) =>
        api.post(`/vocabulary/${vocabularyId}/practice`, { isCorrect, courseId }),


    getDueVocabulary: courseId =>
        api.get('/vocabulary/due', { params: { courseId } }),

    getVocabularyMap: courseId =>
        api.get('/vocabulary/learned', { params: { courseId } })
};
