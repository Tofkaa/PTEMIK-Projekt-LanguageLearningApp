import React, { useState, useEffect } from 'react';
import { Card, Button, Row, Col, Badge, ListGroup } from 'react-bootstrap';
import { assignmentApi } from '../services/assignmentApi';
import { useNavigate } from 'react-router-dom';

/**
 * Component for students to view their pending and active assignments.
 * Displays deadlines, time limits, and the entry point for the test engine.
 */
const StudentAssignmentList = () => {
    const [activeAssignments, setActiveAssignments] = useState([]);
    const [isLoading, setIsLoading] = useState(true);
    const navigate = useNavigate();

    useEffect(() => {
        fetchActiveAssignments();
    }, []);

    /**
     * Fetches all assignments across all joined classrooms that are currently available.
     */
    const fetchActiveAssignments = async () => {
        try {
            setIsLoading(true);
            const res = await assignmentApi.getStudentActiveAssignments();
            setActiveAssignments(res.data);
        } catch (error) {
            console.error("Failed to fetch active assignments:", error);
        } finally {
            setIsLoading(false);
        }
    };

    /**
     * Formats the deadline date to a readable Hungarian format.
     */
    const formatDeadline = (dateString) => {
        if (!dateString) return "Nincs határidő";
        const date = new Date(dateString);
        return date.toLocaleString('hu-HU', { 
            month: 'long', day: 'numeric', hour: '2-digit', minute: '2-digit' 
        });
    };

    if (isLoading) return <div className="text-secondary">Feladatok betöltése...</div>;

    return (
        <div className="mt-4">
            <h4 className="fw-bold text-light mb-4">Aktuális Teendők</h4>
            <Row className="g-4">
                {activeAssignments.length === 0 ? (
                    <Col>
                        <div className="p-4 text-center border border-secondary rounded bg-dark text-light">
                            Jelenleg nincs megoldandó feladatod. Szép munka! 🌟
                        </div>
                    </Col>
                ) : (
                    activeAssignments.map(a => (
                        <Col md={6} key={a.assignmentId}>
                            <Card className="h-100 bg-dark text-light border-secondary shadow-sm hover-border-primary transition">
                                <Card.Body className="d-flex flex-column">
                                    <div className="d-flex justify-content-between align-items-start mb-3">
                                        <div>
                                            <h5 className="fw-bold m-0">{a.title}</h5>
                                            <small className="text-secondary">{a.description}</small>
                                        </div>
                                        {a.test ? (
                                            <Badge bg="danger" className="px-2 py-1">TESZT</Badge>
                                        ) : (
                                            <Badge bg="success" className="px-2 py-1">GYAKORLÓ</Badge>
                                        )}
                                    </div>

                                    <ListGroup variant="flush" className="bg-transparent border-top border-secondary pt-2 flex-grow-1">
                                        <ListGroup.Item className="bg-transparent text-light border-0 p-1 d-flex align-items-center" style={{ fontSize: '0.9rem' }}>
                                            <span className="text-secondary me-2">⏳ Időkeret:</span>
                                            <span className="fw-bold">{a.timeLimitMinutes ? `${a.timeLimitMinutes} perc` : 'Nincs korlátozva'}</span>
                                        </ListGroup.Item>
                                        <ListGroup.Item className="bg-transparent text-light border-0 p-1 d-flex align-items-center" style={{ fontSize: '0.9rem' }}>
                                            <span className="text-secondary me-2">🕒 Határidő:</span>
                                            <span className="fw-bold text-warning">{formatDeadline(a.availableUntil)}</span>
                                        </ListGroup.Item>
                                        <ListGroup.Item className="bg-transparent text-light border-0 p-1 d-flex align-items-center" style={{ fontSize: '0.9rem' }}>
                                            <span className="text-secondary me-2">📚 Feladatok:</span>
                                            <span>{a.exerciseCount} db kérdés</span>
                                        </ListGroup.Item>
                                    </ListGroup>

                                    <div className="mt-3">
                                        <Button 
                                            variant="primary" 
                                            className="w-100 fw-bold"
                                            onClick={() => navigate(`/assignment/${a.assignmentId}/start`)}
                                        >
                                            {a.test ? 'Teszt Megkezdése' : 'Gyakorlás Indítása'}
                                        </Button>
                                    </div>
                                </Card.Body>
                            </Card>
                        </Col>
                    ))
                )}
            </Row>
        </div>
    );
};

export default StudentAssignmentList;