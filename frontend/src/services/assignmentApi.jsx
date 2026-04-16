import api from './api';

/**
 * API client for managing classroom assignments.
 */
export const assignmentApi = {
    createAssignment: (classroomId, data) => api.post(`/assignments/classroom/${classroomId}`, data),
    getClassroomAssignments: (classroomId) => api.get(`/assignments/classroom/${classroomId}`)
};