import api from './api';

export const adminApi = {
    // Tananyag
    importCurriculum: (data) => api.post('/admin/curriculum/import', data),
    
    // Felhasználókezelés
    getAllUsers: () => api.get('/admin/users'),
    updateUserRole: (userId, newRole) => api.put(`/admin/users/${userId}/role?newRole=${newRole}`),
    toggleUserStatus: (userId, isActive) => api.put(`/admin/users/${userId}/status?isActive=${isActive}`),
    
    // Logok
    getSystemLogs: () => api.get('/admin/logs')
};