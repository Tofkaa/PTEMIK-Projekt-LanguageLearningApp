import api from './api';

export const userApi = {
    getProfile: () => api.get('/users/me'),
    
    changeActiveCourse: (courseCode, expectedRevision, intentId) => api.patch('/users/me/active-course', null, {
        params: { courseCode, expectedRevision, intentId }, timeout: 15000,
    }),
};
