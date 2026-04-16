import api from './api';

/**
 * Service handling all HTTP requests for the Classroom module.
 * Relies on the configured axios instance for automatic token injection.
 */
export const classroomApi = {
    getTeacherClassrooms: () => api.get('/classrooms/teacher'),
    
    createClassroom: (data) => api.post('/classrooms', data),
    
    getStudentClassrooms: () => api.get('/classrooms/student'),
    
    joinClassroom: (inviteCode) => api.post('/classrooms/join', { inviteCode })
};