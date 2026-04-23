import api from './api';

export const adminApi = {
    // --- FELHASZNÁLÓK ---
    getAllUsers: () => api.get('/admin/users'),
    updateUserRole: (userId, newRole) => api.put(`/admin/users/${userId}/role?newRole=${newRole}`),
    toggleUserStatus: (userId, isActive) => api.put(`/admin/users/${userId}/status?isActive=${isActive}`),

    // --- RENDSZERNAPLÓ ---
    getSystemLogs: () => api.get('/admin/logs'),

    // --- TANANYAG CMS ---
    importCurriculum: (data) => api.post('/admin/curriculum/import', data),
    getAllTopics: () => api.get('/admin/curriculum/topics'),
    deleteTopic: (id) => api.delete(`/admin/curriculum/topic/${id}`),
    deleteLesson: (id) => api.delete(`/admin/curriculum/lesson/${id}`),
    deleteExercise: (id) => api.delete(`/admin/curriculum/exercise/${id}`),

    // --- KITÜNTETÉSEK ---
    importAchievements: (data) => api.post('/admin/achievements/import', data),
    getAllAchievements: () => api.get('/admin/achievements'),
    deleteAchievement: (id) => api.delete(`/admin/achievements/${id}`),

    // --- OSZTÁLYTERMEK ---
    getAllClassrooms: () => api.get('/admin/classrooms'),
    toggleClassroomStatus: (id, isActive) => api.put(`/admin/classrooms/${id}/status?isActive=${isActive}`)
};