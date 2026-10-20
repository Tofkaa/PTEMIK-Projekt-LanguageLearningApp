import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { Button, Container, Spinner } from 'react-bootstrap';
import TeacherClassroomDetail from '../components/TeacherClassroomDetail';
import StudentClassroomDetail from '../components/StudentClassroomDetail';
import LearningFeedback from '../components/LearningFeedback';
import { classroomApi } from '../services/classroomApi';
import { useAuth } from '../context/AuthContext';

/**
 * Resolves ownership and classroom metadata from the authenticated user's server lists.
 */
const ClassroomDetail = () => {
    const { id } = useParams();
    const { user } = useAuth();
    const [result, setResult] = useState(null);
    const [retry, setRetry] = useState(0);
    const userId = user?.userId;
    const role = user?.role?.replace('ROLE_', '');
    const requestKey = `${userId}:${role}:${id}:${retry}`;

    useEffect(() => {
        if (!userId) return;
        let active = true;
        const resolveClassroom = async () => {
            try {
                let classroom;
                let isOwner = false;
                if (role === 'TEACHER') {
                    const response = await classroomApi.getTeacherClassrooms();
                    classroom = response.data.find(room => room.classroomId === id);
                    isOwner = !!classroom;
                }
                if (!classroom) {
                    const response = await classroomApi.getStudentClassrooms();
                    classroom = response.data.find(room => room.classroomId === id);
                }
                if (active) setResult({ key: requestKey, classroom, isOwner });
            } catch {
                if (active) setResult({ key: requestKey, error: 'Az osztályterem adatai nem tölthetők be. Próbáld újra.' });
            }
        };
        resolveClassroom();
        return () => { active = false; };
    }, [id, userId, role, requestKey]);

    if (!userId || result?.key !== requestKey) {
        return <Container className="py-5"><div role="status"><Spinner size="sm" aria-hidden="true" /> Osztályterem betöltése…</div></Container>;
    }
    if (result.error || !result.classroom) {
        return <Container className="py-5">
            <h1 className="h3">Osztályterem</h1>
            <LearningFeedback error={result.error || 'Az osztályterem nem található a saját osztályaid között.'} />
            {result.error && <Button onClick={() => setRetry(value => value + 1)} className="me-3">Újrapróbálás</Button>}
            <Link to="/classrooms">Vissza az osztálytermekhez</Link>
        </Container>;
    }
    return result.isOwner
        ? <TeacherClassroomDetail key={`${userId}:${id}`} classroom={result.classroom} />
        : <StudentClassroomDetail key={`${userId}:${id}`} classroom={result.classroom} />;
};

export default ClassroomDetail;
