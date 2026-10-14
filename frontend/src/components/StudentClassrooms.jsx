import { useState, useEffect, useCallback } from 'react';
import { classroomApi } from '../services/classroomApi';
import { Card, Button, Form, Row, Col, Badge, InputGroup } from 'react-bootstrap';
import { useNavigate } from 'react-router-dom';
import { useNotifications } from '../context/NotificationContext';
import { useAuth } from '../context/AuthContext';
import { getUnseenIds } from '../utils/viewedStore';

const AVAILABLE_COURSES = {
    'en': { name: 'Angol', flag: '🇬🇧' },
    'es': { name: 'Spanyol', flag: '🇪🇸' },
    'de': { name: 'Német', flag: '🇩🇪' }
};

/**
 * Dashboard component for users with the STUDENT role.
 * Displays enrolled classrooms and provides functionality to join new ones via an invite code.
 */
const StudentClassrooms = () => {
    const { user } = useAuth();
    const [classrooms, setClassrooms] = useState([]);
    const [inviteCode, setInviteCode] = useState('');
    const [statusMessage, setStatusMessage] = useState({ text: '', type: '' });
    const [isLoading, setIsLoading] = useState(true);
    const navigate = useNavigate();
    const { notifications } = useNotifications(); 

    const fetchClassrooms = useCallback(async (silent = false) => {
        try {
            if (!silent) setIsLoading(true);
            const response = await classroomApi.getStudentClassrooms();
            setClassrooms(response.data);
        } catch (error) {
            console.error("Failed to fetch classrooms:", error);
        } finally {
            if (!silent) setIsLoading(false);
        }
    }, []);

    useEffect(() => {
        fetchClassrooms(false);
    }, [fetchClassrooms]);

    const pingTrigger = JSON.stringify([
        notifications?.studentActiveAssignmentIds,
        notifications?.studentGradedSessionIds
    ]);

    useEffect(() => {
        if (!isLoading) {
            fetchClassrooms(true);
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [pingTrigger]);

    const handleJoin = async (e) => {
        e.preventDefault();
        setStatusMessage({ text: '', type: '' });
        
        try {
            await classroomApi.joinClassroom(inviteCode);
            setStatusMessage({ 
                text: 'Csatlakozási kérelem elküldve! A tanár jóváhagyására vár.', 
                type: 'success' 
            });
            setInviteCode('');
        } catch (error) {
            setStatusMessage({ 
                text: error.response?.data?.message || 'Érvénytelen kód, vagy már csatlakoztál.', 
                type: 'error' 
            });
        }
    };

    if (isLoading) {
        return (
            <div className="d-flex justify-content-center my-5">
                <div className="spinner-border text-primary" role="status">
                    <span className="visually-hidden">Betöltés...</span>
                </div>
            </div>
        );
    }

    const allUnseenAssignments = getUnseenIds('viewedAssignments', user?.userId, notifications?.studentActiveAssignmentIds);
    const allUnseenResults = getUnseenIds('viewedResults', user?.userId, notifications?.studentGradedSessionIds);

    return (
        <div>
            {/* Join Panel mimicking the Community tab's design */}
            <Card className="bg-dark border-secondary shadow-sm mb-5">
                <Card.Body className="p-4">
                    <h5 className="fw-bold text-light mb-2">Csatlakozás új osztályhoz</h5>
                    <p className="text-secondary mb-3" style={{ fontSize: '0.9rem' }}>
                        Add meg a tanárodtól kapott 8 karakteres meghívókódot (pl. A7B-9X2).
                    </p>
                    
                    <Form onSubmit={handleJoin}>
                        <InputGroup className="max-w-md" style={{ maxWidth: '500px' }}>
                            <Form.Control
                                type="text"
                                placeholder="Meghívókód..."
                                required
                                maxLength={8}
                                className="bg-dark text-light border-secondary text-uppercase fw-bold"
                                value={inviteCode}
                                onChange={(e) => setInviteCode(e.target.value.toUpperCase())}
                            />
                            <Button variant="primary" type="submit" className="fw-bold px-4">
                                Kérelem Küldése
                            </Button>
                        </InputGroup>
                    </Form>
                    
                    {statusMessage.text && (
                        <div className={`mt-3 fw-bold ${statusMessage.type === 'error' ? 'text-danger' : 'text-success'}`}>
                            {statusMessage.text}
                        </div>
                    )}
                </Card.Body>
            </Card>

            <h4 className="fw-bold mb-4 text-light">Saját osztályaim</h4>
            <Row className="g-4">
                {classrooms.length === 0 ? (
                    <Col>
                        <div className="p-5 text-center border border-secondary rounded bg-dark text-light">
                            Még nem vagy tagja egyetlen osztálynak sem.
                        </div>
                    </Col>
                ) : (
                    classrooms.map(room => {
                        const prefix = `${room.classroomId}:`;
                        const unseenAssignments = allUnseenAssignments.filter(id => id.startsWith(prefix)).length;
                        const unseenResults = allUnseenResults.filter(id => id.startsWith(prefix)).length;
                        const totalClassroomPings = unseenAssignments + unseenResults;

                        const courseData = AVAILABLE_COURSES[room.courseCode] || { name: 'Ismeretlen', flag: '🏳️' };

                        return (
                            <Col md={6} lg={4} key={room.classroomId}>
                                <Card 
                                    className="h-100 bg-dark text-light border-secondary shadow-sm"
                                    style={{ cursor: 'pointer' }}
                                    onClick={() => navigate(`/classrooms/${room.classroomId}`, { state: { className: room.name, isOwner: false } })}
                                >
                                    <Card.Body className="d-flex flex-column">
                                        <Card.Title className="fw-bold text-primary d-flex justify-content-between align-items-start">
                                            <span>
                                                <span className="me-2" title={`Kurzus: ${courseData.name}`}>{courseData.flag}</span>
                                                {room.name}
                                            </span>
                                            {totalClassroomPings > 0 && (
                                                <Badge bg="danger" pill className="animate-pulse shadow-sm fs-6">
                                                    {totalClassroomPings}
                                                </Badge>
                                            )}
                                        </Card.Title>
                                        <Card.Text className="text-secondary flex-grow-1 mt-2" style={{ fontSize: '0.9rem' }}>
                                            {room.description}
                                        </Card.Text>
                                        <div className="mt-3 pt-3 border-top border-secondary">
                                            <span className="text-secondary" style={{ fontSize: '0.85rem' }}>Tanár: </span>
                                            <span className="fw-bold text-light">{room.teacherName}</span>
                                        </div>
                                    </Card.Body>
                                </Card>
                            </Col>
                        );
                    })
                )}
            </Row>
        </div>
    );
};

export default StudentClassrooms;