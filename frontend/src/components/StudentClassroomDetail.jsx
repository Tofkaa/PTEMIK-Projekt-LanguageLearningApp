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

    const parseDate = (d) => {
        if (!d) return null;
        if (Array.isArray(d)) {
           
            return new Date(d[0], d[1] - 1, d[2], d[3] || 0, d[4] || 0, d[5] || 0);
        }
        return new Date(d); 
    };

    const formatDeadline = (dateData) => {
        const date = parseDate(dateData);
        if (!date) return "Nincs határidő";
        return date.toLocaleString('hu-HU', { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' });
    };

    // FELADATOK SZÉTVÁLOGATÁSA (Már az új parseDate-tel)
    const now = new Date();

    const activeAssignments = assignments.filter(a => {
        if (a.completed) return false; 
        const availableUntil = parseDate(a.availableUntil);
        if (availableUntil && availableUntil <= now) return false;
        return true;
    });

   const completedAssignments = assignments.filter(a => {
        if (a.completed) return true; 
        const availableUntil = parseDate(a.availableUntil);
        if (availableUntil && availableUntil <= now) return true; 
        return false;
    });

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
                    
                    {/* BELSŐ FÜLEK AZ AKTÍV ÉS BEFEJEZETT FELADATOKNAK */}
                    <Tabs defaultActiveKey="active" className="mt-3 mb-3 border-secondary">
                        
                       {/* 1. AKTÍV TEENDŐK */}
                        <Tab eventKey="active" title={<span className="fw-bold">🔥 Aktív Teendők</span>}>
                            <Row className="g-4 mt-1">
                                {activeAssignments.length === 0 ? (
                                    <Col><div className="p-5 text-center border border-secondary rounded bg-dark text-light">Nincs aktív feladatod. 🎉</div></Col>
                                ) : (
                                    activeAssignments.map(a => (
                                        <Col md={6} lg={4} key={a.assignmentId}>
                                            <Card className="h-100 bg-dark text-light border-info shadow-lg hover-border-primary transition-all">
                                                <Card.Body className="d-flex flex-column">
                                                    <div className="d-flex justify-content-between align-items-start mb-2">
                                                        <Card.Title className="fw-bold m-0">{a.title}</Card.Title>
                                                        {a.test ? <Badge bg="danger">Teszt</Badge> : <Badge bg="success">Gyakorló</Badge>}
                                                    </div>
                                                    <Card.Text className="text-secondary flex-grow-1 small">{a.description}</Card.Text>
                                                    
                                                    {/* JAVÍTOTT INFÓ PANEL: Próbálkozások és dátumok kijelzése */}
                                                    <div className="mb-3 text-secondary bg-black bg-opacity-25 p-2 rounded" style={{ fontSize: '0.85rem' }}>
                                                        <div className="mb-1 text-info"><span className="fw-bold">📝 Próbálkozás:</span> {a.attemptsUsed} / {a.maxAttempts || '∞'}</div>
                                                        {a.availableFrom && (
                                                            <div className="mb-1"><span className="fw-bold">📅 Kezdés:</span> {formatDeadline(a.availableFrom)}</div>
                                                        )}
                                                        <div><span className="text-warning fw-bold">🕒 Határidő:</span> {formatDeadline(a.availableUntil)}</div>
                                                    </div>
                                                    
                                                    {(() => {
                                                        const availableFrom = parseDate(a.availableFrom);
                                                        const isFuture = availableFrom && availableFrom > now;
                                                        return (
                                                            <Button 
                                                                variant={isFuture ? "secondary" : "primary"} 
                                                                className="w-100 fw-bold shadow-sm" 
                                                                disabled={isFuture}
                                                                onClick={() => navigate(`/assignment/${a.assignmentId}/start`, { state: { assignmentDetails: a } })}
                                                            >
                                                                {isFuture ? 'Még nem elérhető' : 'Indítás'}
                                                            </Button>
                                                        );
                                                    })()}
                                                </Card.Body>
                                            </Card>
                                        </Col>
                                    ))
                                )}
                            </Row>
                        </Tab>

                        {/* 2. BEFEJEZETT VAGY LEJÁRT */}
                        <Tab eventKey="completed" title={<span className="fw-bold">✅ Befejezett / Lejárt</span>}>
                            <Row className="g-4 mt-1">
                                {completedAssignments.map(a => (
                                    <Col md={6} lg={4} key={a.assignmentId}>
                                        <Card className="h-100 bg-dark text-secondary border-secondary shadow-sm" style={{ opacity: 0.85 }}>
                                            <Card.Body className="d-flex flex-column">
                                                <div className="d-flex justify-content-between align-items-start mb-2">
                                                    <Card.Title className="fw-bold m-0 text-light">{a.title}</Card.Title>
                                                    {a.completed ? <Badge bg="secondary">Befejezve</Badge> : <Badge bg="secondary">Lejárt</Badge>}
                                                </div>
                                                
                                                <div className="mb-3 bg-black bg-opacity-25 p-2 rounded small">
                                                    <div className="mb-1 text-info"><span className="fw-bold">📝 Próbálkozás:</span> {a.attemptsUsed} / {a.maxAttempts || '∞'}</div>
                                                    {a.availableFrom && <div><span className="fw-bold">📅 Kezdés:</span> {formatDeadline(a.availableFrom)}</div>}
                                                    <div><span className="fw-bold">🕒 Határidő:</span> {formatDeadline(a.availableUntil)}</div>
                                                </div>
                                                
                                                <div className="mt-auto d-grid gap-2">
                                                    <Button variant="outline-secondary" className="fw-bold" disabled>Eredmény (Hamarosan)</Button>
                                                    
                                                    {/* ÚJRAÍRÁS: Csak ha van még lehetőség és nincs lejárat */}
                                                    {(!a.availableUntil || parseDate(a.availableUntil) > now) && 
                                                     (!a.maxAttempts || a.attemptsUsed < a.maxAttempts) && (
                                                        <Button 
                                                            variant="primary" 
                                                            className="fw-bold"
                                                            onClick={() => navigate(`/assignment/${a.assignmentId}/start`, { state: { assignmentDetails: a } })}
                                                        >
                                                            Újraírás
                                                        </Button>
                                                    )}
                                                </div>
                                            </Card.Body>
                                        </Card>
                                    </Col>
                                ))}
                            </Row>
                        </Tab>
                    </Tabs>
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