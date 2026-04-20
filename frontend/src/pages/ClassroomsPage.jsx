import React from 'react';
import { useAuth } from '../context/AuthContext';
import TeacherClassrooms from '../components/TeacherClassrooms';
import StudentClassrooms from '../components/StudentClassrooms';
import { Container } from 'react-bootstrap'; // Ha bootstrapet használsz a layoutra

/**
 * Main routing component for the Classrooms module.
 * Evaluates the authenticated user's role and renders the corresponding dashboard.
 */
const ClassroomsPage = () => {
    const { user } = useAuth();

    if (!user) {
        return (
            <div className="d-flex justify-content-center align-items-center" style={{ height: '200px' }}>
                <div className="spinner-border text-primary" role="status">
                    <span className="visually-hidden">Betöltés...</span>
                </div>
            </div>
        );
    }

    // Biztonságos ellenőrzés: elfogadja a 'TEACHER' és a 'ROLE_TEACHER' formátumot is
    const isTeacher = user.role === 'TEACHER' || user.role === 'ROLE_TEACHER';

    return (
        <Container className="py-4 text-light">
            <h1 className="mb-4 fw-bold">Osztálytermek</h1>
            
            {isTeacher ? (
                <TeacherClassrooms />
            ) : (
                <StudentClassrooms />
            )}
        </Container>
    );
};

export default ClassroomsPage;