import api from './api';

/**
 * Vocabulary and SRS management
 */
export const vocabularyApi = {
    

    lookupWord: (word, source = 'DICTIONARY_TOOLTIP') => 
        api.post('/vocabulary/lookup', { word, source }),

  
    recordPractice: (vocabularyId, isCorrect) => 
        api.post(`/vocabulary/${vocabularyId}/practice`, { isCorrect }),


    getDueVocabulary: () => 
        api.get('/vocabulary/due'),

    getVocabularyMap: () => 
        api.get('/vocabulary/learned')
};