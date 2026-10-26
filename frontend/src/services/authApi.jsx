import api from './api';

export const authApi = {
    // Session-changing requests belong to the serialized AuthContext/registration flows.
    verifyEmail: (token) => api.get(`/auth/verify?token=${token}`),
    forgotPassword: (email) => api.post(`/auth/forgot-password?email=${encodeURIComponent(email)}`),
    resetPassword: (data) => api.post('/auth/reset-password', data),
    
    
};
