import React, { useState, useEffect } from 'react';
import { useParams, useNavigate, useLocation } from 'react-router-dom';
import { Container, Tab, Tabs, Card, Button, Badge, Row, Col, ListGroup } from 'react-bootstrap';
import { classroomApi } from '../services/classroomApi';
import { assignmentApi } from '../services/assignmentApi';

const StudentClassroomDetail = () => {
    const { id: classroomId } = useParams();
    const navigate = useNavigate();
    const location = useLocation();
    
    const classroomName = location.state?.className || 'Osztályterem';

    const [assignments, setAssignments] = useState([]);
    const [classmates, setClassmates] = useState([]);
    const [isLoading, setIsLoading] = useState(true);

    useEffect(() => {
        fetchClassroomData();
    }, [classroomId]);

    const fetchClassroomData = async () => {
        setIsLoading(true);
        try {
            const [assignmentsRes, membersRes] = await Promise.all([
                assignmentApi.getClassroomAssignments(classroomId),
                classroomApi.getMembers(classroomId, 'ACCEPTED')
            ]);
            setAssignments(assignmentsRes.data);
            setClassmates(membersRes.data);
        } catch (error) {
            console.error("Hiba az adatok lekérésekor:", error);
        } finally {
            setIsLoading(false);
        }
    };

    const formatDeadline = (dateString) => {
        if (!dateString) return "Nincs határidő";
        const date = new Date(dateString);
        return date.toLocaleString('hu-HU', { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' });
    };

    if (isLoading) {
        return (
            <Container className="d-flex justify-content-center align-items-center" style={{ height: '50vh' }}>
                <div className="spinner-border text-primary" role="status"></div>
            </Container>
        );
    }

    return (
        <Container className="py-4 text-light">
            <div className="mb-4">
                <Button variant="outline-secondary" size="sm" onClick={() => navigate('/classrooms')} className="mb-2">
                    ← Vissza az osztályokhoz
                </Button>
                <h2 className="fw-bold m-0 text-primary">{classroomName}</h2>
            </div>

            <Tabs defaultActiveKey="assignments" className="mb-4 custom-dark-tabs">
                
                {/* DIÁK FELADATOK FÜL */}
                <Tab eventKey="assignments" title={<span className="fw-bold">📝 Feladatok</span>}>
                    <Row className="g-4 mt-2">
                        {assignments.length === 0 ? (
                            <Col>
                                <div className="p-5 text-center border border-secondary rounded bg-dark text-muted">
                                    Nincs jelenleg aktív feladat ebben az osztályban.
                                </div>
                            </Col>
                        ) : (
                            assignments.map(a => (
                                <Col md={6} lg={4} key={a.assignmentId}>
                                    <Card className="h-100 bg-dark text-light border-secondary shadow-sm">
                                        <Card.Body className="d-flex flex-column">
                                            <div className="d-flex justify-content-between align-items-start mb-2">
                                                <Card.Title className="fw-bold m-0">{a.title}</Card.Title>
                                                {a.test ? <Badge bg="danger">Teszt</Badge> : <Badge bg="success">Gyakorló</Badge>}
                                            </div>
                                            <Card.Text className="text-secondary flex-grow-1" style={{ fontSize: '0.9rem' }}>
                                                {a.description}
                                            </Card.Text>
                                            
                                            <div className="mb-3 text-secondary" style={{ fontSize: '0.85rem' }}>
                                                <div><strong>Időkorlát:</strong> {a.timeLimitMinutes ? `${a.timeLimitMinutes} perc` : 'Nincs'}</div>
                                                <div className="text-warning"><strong>Határidő:</strong> {formatDeadline(a.availableUntil)}</div>
                                            </div>

                                            <Button 
                                                variant="primary" 
                                                className="w-100 fw-bold"
                                                onClick={() => navigate(`/assignment/${a.assignmentId}/start`, { state: { assignmentDetails: a } })}
                                            >
                                                Indítás
                                            </Button>
                                        </Card.Body>
                                    </Card>
                                </Col>
                            ))
                        )}
                    </Row>
                </Tab>

                {/* OSZTÁLYTÁRSAK FÜL */}
                <Tab eventKey="classmates" title={<span className="fw-bold">👥 Osztálytársak</span>}>
                    <Card className="bg-dark text-light border-secondary mt-3">
                        <Card.Header className="border-secondary bg-dark">
                            <h5 className="m-0 text-info">Résztvevők ({classmates.length} fő)</h5>
                        </Card.Header>
                        <ListGroup variant="flush">
                            {classmates.map(member => (
                                <ListGroup.Item key={member.memberId} className="bg-dark text-light border-secondary">
                                    <span className="fw-bold">{member.studentName || member.name || member.username}</span> 
                                    <span className="text-secondary ms-2">#{member.userTag}</span>
                                </ListGroup.Item>
                            ))}
                        </ListGroup>
                    </Card>
                </Tab>
            </Tabs>
        </Container>
    );
};

export default StudentClassroomDetail;