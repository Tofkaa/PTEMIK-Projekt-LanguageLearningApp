import React, { useState, useEffect } from 'react';
import { classroomApi } from '../services/classroomApi';
import { Card, Button, Modal, Form, Row, Col, Badge } from 'react-bootstrap';
import { useNavigate } from 'react-router-dom';
import { useNotifications } from '../context/NotificationContext';

/**
 * Dashboard component for users with the TEACHER role.
 * Facilitates the creation and overview of managed classrooms.
 */
const TeacherClassrooms = () => {
    const [classrooms, setClassrooms] = useState([]);
    const [isModalOpen, setIsModalOpen] = useState(false);
    const [formData, setFormData] = useState({ name: '', description: '' });
    const [isLoading, setIsLoading] = useState(true);
    const navigate = useNavigate();
    const { notifications } = useNotifications();
    useEffect(() => {
        fetchClassrooms();
    }, []);

    const fetchClassrooms = async () => {
        try {
            setIsLoading(true);
            const response = await classroomApi.getTeacherClassrooms();
            setClassrooms(response.data);
        } catch (error) {
            console.error("Failed to fetch classrooms:", error);
        } finally {
            setIsLoading(false);
        }
    };

    const handleCreate = async (e) => {
        e.preventDefault();
        try {
            await classroomApi.createClassroom(formData);
            setIsModalOpen(false);
            setFormData({ name: '', description: '' });
            fetchClassrooms(); 
        } catch (error) {
            console.error("Failed to create classroom:", error);
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
                        const viewedTeacherPending = JSON.parse(localStorage.getItem('viewedTeacherPending') || '[]');
                        const viewedTeacherUngraded = JSON.parse(localStorage.getItem('viewedTeacherUngraded') || '[]');

                        const unseenPending = (notifications?.teacherPendingJoinRequestIds || []).filter(id => id.startsWith(room.classroomId) && !viewedTeacherPending.includes(id)).length;
                        const unseenUngraded = (notifications?.teacherUngradedSubmissionIds || []).filter(id => id.startsWith(room.classroomId) && !viewedTeacherUngraded.includes(id)).length;
                        const totalClassroomPings = unseenPending + unseenUngraded;

                        return (
                            <Col md={6} lg={4} key={room.classroomId}>
                                <Card className="h-100 bg-dark text-light border-secondary shadow-sm" style={{ cursor: 'pointer' }}
                                    onClick={() => navigate(`/classrooms/${room.classroomId}`, { state: { className: room.name, isOwner: true } })}>
                                    <Card.Body className="d-flex flex-column">
                                        <Card.Title className="fw-bold d-flex justify-content-between align-items-start">
                                            {room.name}
                                         
                                            {totalClassroomPings > 0 && (
                                                <Badge bg="danger" pill className="animate-pulse shadow-sm fs-6">
                                                    {totalClassroomPings}
                                                </Badge>
                                            )}
                                        </Card.Title>
                                        <Card.Text className="text-secondary flex-grow-1" style={{ fontSize: '0.9rem' }}>
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
                    )})
                )}
            </Row>

            {/* Dark Mode Modal for Creation */}
            <Modal show={isModalOpen} onHide={() => setIsModalOpen(false)} centered contentClassName="bg-dark text-light border-secondary">
                <Modal.Header closeButton className="border-secondary" closeVariant="white">
                    <Modal.Title className="fw-bold">Új osztály létrehozása</Modal.Title>
                </Modal.Header>
                <Form onSubmit={handleCreate}>
                    <Modal.Body>
                        <Form.Group className="mb-3">
                            <Form.Label className="text-secondary fw-bold">Osztály neve</Form.Label>
                            <Form.Control 
                                type="text" 
                                required
                                className="bg-dark text-light border-secondary"
                                value={formData.name}
                                onChange={(e) => setFormData({...formData, name: e.target.value})}
                            />
                        </Form.Group>
                        <Form.Group className="mb-3">
                            <Form.Label className="text-secondary fw-bold">Leírás</Form.Label>
                            <Form.Control 
                                as="textarea" 
                                rows={3}
                                className="bg-dark text-light border-secondary"
                                value={formData.description}
                                onChange={(e) => setFormData({...formData, description: e.target.value})}
                            />
                        </Form.Group>
                    </Modal.Body>
                    <Modal.Footer className="border-secondary">
                        <Button variant="outline-light" onClick={() => setIsModalOpen(false)}>
                            Mégse
                        </Button>
                        <Button variant="primary" type="submit" className="fw-bold">
                            Mentés
                        </Button>
                    </Modal.Footer>
                </Form>
            </Modal>
        </div>
    );
};

export default TeacherClassrooms;