import api from './api';

/**
 * API client for managing classroom assignments.
 */
export const assignmentApi = {

    createAssignment: (classroomId, data) => api.post(`/assignments/classroom/${classroomId}`, data),
    getClassroomAssignments: (classroomId) => api.get(`/assignments/classroom/${classroomId}`),
    getStudentActiveAssignments: () => api.get('/assignments/active'),
    getAssignmentDetails: (assignmentId) => api.get(`/assignments/${assignmentId}`),
    startAssignment: (assignmentId) => api.post(`/assignments/${assignmentId}/start`),
    submitAssignment: (sessionId, answers, revision) => api.post(`/assignments/sessions/${sessionId}/submit`, { answers, revision }, { skipGlobalError: true }),
    getSession: sessionId => api.get(`/assignments/sessions/${sessionId}`, { skipGlobalError: true }),
    saveDraft: (sessionId, draft) => api.put(`/assignments/sessions/${sessionId}/draft`, draft, { skipGlobalError: true }),
    deleteAssignment: (assignmentId) => api.delete(`/assignments/${assignmentId}`),
    getAssignmentSessions: (assignmentId) => api.get(`/assignments/${assignmentId}/sessions`),
    gradeSession: (sessionId, data) => api.post(`/assignments/sessions/${sessionId}/grade`, data, { timeout: 15000, skipGlobalError: true }),
    getAccommodations: (assignmentId) => api.get(`/assignments/${assignmentId}/accommodations`),
    updateAccommodation: (assignmentId, userId, data) => api.put(`/assignments/${assignmentId}/accommodations/${userId}`, data),
    getSessionTiming: (sessionId) => api.get(`/assignments/sessions/${sessionId}/timing`, { skipGlobalError: true }),
    getMyAssignmentSessions: (assignmentId) => api.get(`/assignments/${assignmentId}/my-sessions`),
    getClassroomStatistics: (classroomId) => api.get(`/assignments/classroom/${classroomId}/statistics`),
    getStudentClassroomStatistics: (classroomId) => api.get(`/assignments/classroom/${classroomId}/my-statistics`),
    getAdvancedClassroomStatistics: (classroomId) => api.get(`/assignments/classroom/${classroomId}/advanced-statistics`),
    
};
