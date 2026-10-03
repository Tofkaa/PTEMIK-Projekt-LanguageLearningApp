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
    importLessons: (topicId, data) => api.post(`/admin/curriculum/topic/${topicId}/lesson/import`, data),
    importExercises: (lessonId, data) => api.post(`/admin/curriculum/lesson/${lessonId}/exercise/import`, data),
    getAllTopics: () => api.get('/admin/curriculum/topics'),
    toggleTopicStatus: (id, isActive) => api.put(`/admin/curriculum/topic/${id}/status?isActive=${isActive}`),
    toggleLessonStatus: (id, isActive) => api.put(`/admin/curriculum/lesson/${id}/status?isActive=${isActive}`),
    toggleExerciseStatus: (id, isActive) => api.put(`/admin/curriculum/exercise/${id}/status?isActive=${isActive}`),
    // --- KITÜNTETÉSEK ---
    importAchievements: (data) => api.post('/admin/achievements/import', data),
    getAllAchievements: () => api.get('/admin/achievements'),
    toggleAchievementStatus: (id, isActive) => api.put(`/admin/achievements/${id}/status?isActive=${isActive}`),
    // --- OSZTÁLYTERMEK ---
    getAllClassrooms: () => api.get('/admin/classrooms'),
    toggleClassroomStatus: (id, isActive) => api.put(`/admin/classrooms/${id}/status?isActive=${isActive}`)
};