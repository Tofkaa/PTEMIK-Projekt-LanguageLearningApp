import api from './api';

export const authApi = {
    login: (credentials) => api.post('/auth/login', credentials),
    register: (data) => api.post('/auth/register', data),
    logout: () => api.post('/auth/logout'),
    refreshToken: () => api.post('/auth/refresh'),
    
    verifyEmail: (token) => api.get(`/auth/verify?token=${token}`),
    forgotPassword: (email) => api.post(`/auth/forgot-password?email=${encodeURIComponent(email)}`),
    resetPassword: (data) => api.post('/auth/reset-password', data),
    
};