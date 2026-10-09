import api from './api';

export const userApi = {
    getProfile: () => api.get('/users/me'),
    
    changeActiveCourse: (courseCode) => api.patch(`/users/me/active-course?courseCode=${courseCode}`),
};