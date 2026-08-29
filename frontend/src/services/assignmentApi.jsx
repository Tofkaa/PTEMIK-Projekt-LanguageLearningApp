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
    submitAssignment: (sessionId, answers) => api.post(`/assignments/sessions/${sessionId}/submit`, { answers }),
    deleteAssignment: (assignmentId) => api.delete(`/assignments/${assignmentId}`),
    getAssignmentSessions: (assignmentId) => api.get(`/assignments/${assignmentId}/sessions`),
    gradeSession: (sessionId, data) => api.post(`/assignments/sessions/${sessionId}/grade`, data),
    getMyAssignmentSessions: (assignmentId) => api.get(`/assignments/${assignmentId}/my-sessions`),
    getClassroomStatistics: (classroomId) => api.get(`/assignments/classroom/${classroomId}/statistics`),
    getStudentClassroomStatistics: (classroomId) => api.get(`/assignments/classroom/${classroomId}/my-statistics`),
    getAdvancedClassroomStatistics: (classroomId) => api.get(`/assignments/classroom/${classroomId}/advanced-statistics`),
    
};