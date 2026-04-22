import api from './api';

export const adminApi = {
    // Tananyag
    importCurriculum: (data) => api.post('/admin/curriculum/import', data),
    deleteTopic: (id) => api.delete(`/admin/curriculum/topic/${id}`),
    deleteLesson: (id) => api.delete(`/admin/curriculum/lesson/${id}`),
    deleteExercise: (id) => api.delete(`/admin/curriculum/exercise/${id}`),
    
    // Felhasználókezelés
    getAllUsers: () => api.get('/admin/users'),
    updateUserRole: (userId, newRole) => api.put(`/admin/users/${userId}/role?newRole=${newRole}`),
    toggleUserStatus: (userId, isActive) => api.put(`/admin/users/${userId}/status?isActive=${isActive}`),
    
    // Kitüntetések
    importAchievements: (data) => api.post('/admin/achievements/import', data),
    deleteAchievement: (id) => api.delete(`/admin/achievements/${id}`),
    
    // Logok
    getSystemLogs: () => api.get('/admin/logs')
};