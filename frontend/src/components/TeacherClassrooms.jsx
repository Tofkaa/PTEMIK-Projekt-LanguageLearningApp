import { safeError } from '../utils/safeError';
import  { useState, useEffect, useCallback } from 'react';
import { classroomApi } from '../services/classroomApi';
import { Card, Button, Modal, Form, Row, Col, Badge } from 'react-bootstrap';
import { Link } from 'react-router-dom';
import { useNotifications } from '../context/NotificationContext';
import { useAuth } from '../context/AuthContext';
import { getUnseenIds } from '../utils/viewedStore';

const AVAILABLE_COURSES = {
    'en': { name: 'Angol', flag: '🇬🇧' },
    'es': { name: 'Spanyol', flag: '🇪🇸' },
    'de': { name: 'Német', flag: '🇩🇪' }
};

/**
 * Dashboard component for users with the TEACHER role.
 * Facilitates the creation and overview of managed classrooms.
 */
const TeacherClassrooms = () => {
    const { user } = useAuth();
    const [classrooms, setClassrooms] = useState([]);
    const [isModalOpen, setIsModalOpen] = useState(false);
    const [formData, setFormData] = useState({ name: '', description: '', courseCode: 'en' });
    const [isLoading, setIsLoading] = useState(true);
    const { notifications } = useNotifications();

    const fetchClassrooms = useCallback(async (silent = false) => {
        try {
            if (!silent) setIsLoading(true);
            const response = await classroomApi.getTeacherClassrooms();
            setClassrooms(response.data);
        } catch (error) {
            console.error("Failed to fetch classrooms:", safeError(error));
        } finally {
            if (!silent) setIsLoading(false);
        }
    }, []);

    useEffect(() => {
        fetchClassrooms(false);
    }, [fetchClassrooms]);

    const pingTrigger = JSON.stringify([
        notifications?.teacherPendingJoinRequestIds,
        notifications?.teacherUngradedSubmissionIds
    ]);

    useEffect(() => {
        if (!isLoading) {
            fetchClassrooms(true);
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [pingTrigger]);

    const handleCreate = async (e) => {
        e.preventDefault();
        try {
            await classroomApi.createClassroom(formData);
            setIsModalOpen(false);
            setFormData({ name: '', description: '', courseCode: 'en' });
            fetchClassrooms(true);
        } catch (error) {
            console.error("Failed to create classroom:", safeError(error));
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

    const allUnseenPending = getUnseenIds('viewedTeacherPending', user?.userId, notifications?.teacherPendingJoinRequestIds);
    const allUnseenUngraded = getUnseenIds('viewedTeacherUngraded', user?.userId, notifications?.teacherUngradedSubmissionIds);

    return (
        <div>
            <div className="mb-4">
                <Button variant="primary" className="fw-bold px-4" onClick={() => setIsModalOpen(true)}>
                    + Új osztály létrehozása
                </Button>
            </div>

            <Row className="g-4">
                {classrooms.length === 0 ? (
                    <Col>
                        <div className="p-5 text-center border border-secondary rounded bg-dark text-light">
                            Még nem hoztál létre egyetlen osztálytermet sem.
                        </div>
                    </Col>
                ) : (
                    classrooms.map(room => {
                        const prefix = `${room.classroomId}:`;
                        const unseenPending = allUnseenPending.filter(id => id.startsWith(prefix)).length;
                        const unseenUngraded = allUnseenUngraded.filter(id => id.startsWith(prefix)).length;
                        const totalClassroomPings = unseenPending + unseenUngraded;
                        
                        const courseData = AVAILABLE_COURSES[room.courseCode] || { name: 'Ismeretlen', flag: '🏳️' };

                        return (
                            <Col md={6} lg={4} key={room.classroomId}>
                                <Card className="h-100 bg-dark text-light border-secondary shadow-sm">
                                    <Card.Body className="d-flex flex-column">
                                        <Card.Title className="fw-bold d-flex justify-content-between align-items-start">
                                            <span>
                                                <span className="me-2" title={`Kurzus: ${courseData.name}`}>{courseData.flag}</span>
                                                <Link className="stretched-link app-accent-link" to={`/classrooms/${room.classroomId}`}
                                                    state={{ className: room.name, isOwner: true, courseCode: room.courseCode }}>{room.name}</Link>
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
                                        <div className="mt-3 pt-3 border-top border-secondary d-flex justify-content-between align-items-center">
                                            <div>
                                                <span className="text-secondary me-2" style={{ fontSize: '0.85rem' }}>Kód:</span>
                                                <Badge bg="warning" text="dark" className="fw-bold px-2 py-1">
                                                    {room.inviteCode}
                                                </Badge>
                                            </div>
                                            <Badge bg="info" text="dark">
                                                {room.activeMemberCount} diák
                                            </Badge>
                                        </div>
                                    </Card.Body>
                                </Card>
                            </Col>
                        );
                    })
                )}
            </Row>

            {/* Dark Mode Modal for Creation */}
            <Modal show={isModalOpen} onHide={() => setIsModalOpen(false)} aria-labelledby="classroom-create-title" centered contentClassName="bg-dark text-light border-secondary">
                <Modal.Header closeButton closeLabel="Ablak bezárása" className="border-secondary">
                    <Modal.Title id="classroom-create-title" className="fw-bold">Új osztály létrehozása</Modal.Title>
                </Modal.Header>
                <Form onSubmit={handleCreate}>
                    <Modal.Body>
                        <Form.Group className="mb-3" controlId="new-classroom-name">
                            <Form.Label className="text-secondary fw-bold">Osztály neve</Form.Label>
                            <Form.Control 
                                type="text" 
                                required
                                className="bg-dark text-light border-secondary"
                                value={formData.name}
                                onChange={(e) => setFormData({...formData, name: e.target.value})}
                            />
                        </Form.Group>
                        <Form.Group className="mb-3" controlId="new-classroom-description">
                            <Form.Label className="text-secondary fw-bold">Leírás</Form.Label>
                            <Form.Control 
                                as="textarea" 
                                rows={3}
                                className="bg-dark text-light border-secondary"
                                value={formData.description}
                                onChange={(e) => setFormData({...formData, description: e.target.value})}
                            />
                        </Form.Group>
                        
                        {/* --- Choose Course --- */}
                        <Form.Group className="mb-3" controlId="new-classroom-course">
                            <Form.Label className="app-accent-text fw-bold">Tanított Nyelv (Kurzus)</Form.Label>
                            <Form.Select 
                                required
                                className="bg-dark text-light border-info"
                                value={formData.courseCode}
                                onChange={(e) => setFormData({...formData, courseCode: e.target.value})}
                            >
                                {Object.entries(AVAILABLE_COURSES).map(([code, data]) => (
                                    <option key={code} value={code}>
                                        {data.flag} {data.name}
                                    </option>
                                ))}
                            </Form.Select>
                            <Form.Text className="text-muted small">
                                Csak az ehhez a nyelvhez tartozó tananyagok lesznek elérhetőek az osztály számára!
                            </Form.Text>
                        </Form.Group>

                    </Modal.Body>
                    <Modal.Footer className="border-secondary">
                        <Button variant="outline-secondary" onClick={() => setIsModalOpen(false)}>
                            Mégse
                        </Button>
                        <Button variant="primary" type="submit" className="fw-bold">
                            Létrehozás
                        </Button>
                    </Modal.Footer>
                </Form>
            </Modal>
        </div>
    );
};

export default TeacherClassrooms;
