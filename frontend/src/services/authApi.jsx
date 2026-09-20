import api from './api';

export const authApi = {
    login: (credentials) => api.post('/auth/login', credentials),
    register: (data) => api.post('/auth/register', data),
    logout: () => api.post('/auth/logout'),
    refreshToken: () => api.post('/auth/refresh'),
    
    verifyEmail: (token) => api.get(`/auth/verify?token=${token}`),
};