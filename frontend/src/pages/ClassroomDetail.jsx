import React from 'react';
import { useLocation } from 'react-router-dom';
import TeacherClassroomDetail from '../components/TeacherClassroomDetail';
import StudentClassroomDetail from '../components/StudentClassroomDetail';

/**
 * Wrapper component that acts as a router for the classroom detail view.
 * Renders the Teacher dashboard or Student dashboard based on the navigation context.
 */
const ClassroomDetail = () => {
    const location = useLocation();

    // Megnézzük a state-ből, hogy a felhasználó tulajdonosként (isOwner: true) érkezett-e.
    // Ha valamiért nincs state (pl. valaki csak bemásolta az URL-t), biztonsági okokból diákként kezeljük.
    const isOwner = location.state?.isOwner === true;

    return isOwner ? <TeacherClassroomDetail /> : <StudentClassroomDetail />;
};

export default ClassroomDetail;