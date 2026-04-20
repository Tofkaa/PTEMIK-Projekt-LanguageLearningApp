import api from './api';

/**
 * API client for fetching educational content for teachers.
 */
export const lessonApi = {
  
    getAllLessons: () => api.get('/lessons/all-for-challenge'),
    getLessonExercises: (lessonId) => api.get(`/lessons/${lessonId}/exercises`)
};