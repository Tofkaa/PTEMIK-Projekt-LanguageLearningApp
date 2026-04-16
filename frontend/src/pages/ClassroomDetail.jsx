import React, { useState, useEffect } from 'react';
import { useParams, useNavigate, useLocation } from 'react-router-dom';
import { Container, Tab, Tabs, Card, Button, Badge, Row, Col, ListGroup } from 'react-bootstrap';
import { classroomApi } from '../services/classroomApi';

/**
 * Teacher Dashboard for managing a specific classroom.
 * Includes student moderation (accept/reject/kick) and tabs for assignments.
 */
const ClassroomDetail = () => {
    const { id: classroomId } = useParams();
    const navigate = useNavigate();
    const location = useLocation();
    
    // A kártyáról átadott osztálynév (ha van), amúgy generikus cím
    const classroomName = location.state?.className || 'Osztályterem Kezelése';

    const [pendingMembers, setPendingMembers] = useState([]);
    const [acceptedMembers, setAcceptedMembers] = useState([]);
    const [isLoading, setIsLoading] = useState(true);

    useEffect(() => {
        fetchAllMembers();
    }, [classroomId]);

    /**
     * Fetches both pending and accepted members in parallel.
     */
    const fetchAllMembers = async () => {
        setIsLoading(true);
        try {
            const [pendingRes, acceptedRes] = await Promise.all([
                classroomApi.getMembers(classroomId, 'PENDING'),
                classroomApi.getMembers(classroomId, 'ACCEPTED')
            ]);
            setPendingMembers(pendingRes.data);
            setAcceptedMembers(acceptedRes.data);
        } catch (error) {
            console.error("Hiba a tagok lekérésekor:", error);
        } finally {
            setIsLoading(false);
        }
    };

    /**
     * Handles accepting or rejecting a student's join request.
     */
    const handleModerate = async (studentId, approve) => {
        try {
            await classroomApi.moderateMember(classroomId, studentId, approve);
            fetchAllMembers(); // Lista frissítése
        } catch (error) {
            console.error("Hiba a moderálás során:", error);
        }
    };

    /**
     * Handles kicking an already accepted student.
     */
    const handleKick = async (studentId) => {
        if (window.confirm("Biztosan el akarod távolítani ezt a diákot az osztályból?")) {
            try {
                await classroomApi.kickMember(classroomId, studentId);
                fetchAllMembers(); // Lista frissítése
            } catch (error) {
                console.error("Hiba a diák eltávolításakor:", error);
            }
        }
    };

    if (isLoading) {
        return (
            <Container className="d-flex justify-content-center align-items-center" style={{ height: '50vh' }}>
                <div className="spinner-border text-primary" role="status">
                    <span className="visually-hidden">Betöltés...</span>
                </div>
            </Container>
        );
    }

    return (
        <Container className="py-4 text-light">
            <div className="d-flex justify-content-between align-items-center mb-4">
                <div>
                    <Button variant="outline-secondary" size="sm" onClick={() => navigate('/classrooms')} className="mb-2">
                        ← Vissza az osztályokhoz
                    </Button>
                    <h2 className="fw-bold m-0">{classroomName}</h2>
                </div>
            </div>

            <Tabs defaultActiveKey="members" className="mb-4 custom-dark-tabs">
                <Tab eventKey="members" title={<span className="fw-bold">👥 Tagság kezelése</span>}>
                    <Row className="g-4 mt-1">
                        {/* PENDING LIST */}
                        <Col md={6}>
                            <Card className="bg-dark text-light border-secondary shadow-sm h-100">
                                <Card.Header className="border-secondary bg-dark d-flex justify-content-between align-items-center">
                                    <h5 className="m-0 text-warning">Várakozó Kérelmek</h5>
                                    <Badge bg="warning" text="dark">{pendingMembers.length}</Badge>
                                </Card.Header>
                                <ListGroup variant="flush">
                                    {pendingMembers.length === 0 ? (
                                        <ListGroup.Item className="bg-dark text-muted border-secondary text-center py-4">
                                            Nincs új csatlakozási kérelem.
                                        </ListGroup.Item>
                                    ) : (
                                        pendingMembers.map(member => (
                                            <ListGroup.Item key={member.memberId} className="bg-dark text-light border-secondary d-flex justify-content-between align-items-center">
                                                <div>
                                                    <div className="fw-bold">{member.studentName}</div>
                                                    <div className="text-secondary" style={{ fontSize: '0.8rem' }}>{member.studentEmail}</div>
                                                </div>
                                                <div className="d-flex gap-2">
                                                    <Button variant="success" size="sm" onClick={() => handleModerate(member.userId, true)}>
                                                        ✓ Elfogad
                                                    </Button>
                                                    <Button variant="outline-danger" size="sm" onClick={() => handleModerate(member.userId, false)}>
                                                        ✕ Elutasít
                                                    </Button>
                                                </div>
                                            </ListGroup.Item>
                                        ))
                                    )}
                                </ListGroup>
                            </Card>
                        </Col>

                        {/* ACCEPTED LIST */}
                        <Col md={6}>
                            <Card className="bg-dark text-light border-secondary shadow-sm h-100">
                                <Card.Header className="border-secondary bg-dark d-flex justify-content-between align-items-center">
                                    <h5 className="m-0 text-info">Aktív Diákok</h5>
                                    <Badge bg="info" text="dark">{acceptedMembers.length}</Badge>
                                </Card.Header>
                                <ListGroup variant="flush">
                                    {acceptedMembers.length === 0 ? (
                                        <ListGroup.Item className="bg-dark text-muted border-secondary text-center py-4">
                                            Az osztály még üres.
                                        </ListGroup.Item>
                                    ) : (
                                        acceptedMembers.map(member => (
                                            <ListGroup.Item key={member.memberId} className="bg-dark text-light border-secondary d-flex justify-content-between align-items-center">
                                                <div>
                                                    <div className="fw-bold">{member.studentName} <span className="text-muted">#{member.userTag}</span></div>
                                                    <div className="text-secondary" style={{ fontSize: '0.8rem' }}>Csatlakozott: {new Date(member.joinedAt).toLocaleDateString()}</div>
                                                </div>
                                                <Button variant="outline-danger" size="sm" onClick={() => handleKick(member.userId)}>
                                                    Kirúgás
                                                </Button>
                                            </ListGroup.Item>
                                        ))
                                    )}
                                </ListGroup>
                            </Card>
                        </Col>
                    </Row>
                </Tab>

                <Tab eventKey="assignments" title={<span className="fw-bold">📝 Feladatok & Tesztek</span>}>
                    <Card className="bg-dark text-light border-secondary shadow-sm p-5 text-center mt-3">
                        <h4 className="text-muted">A Feladatkiosztó modul hamarosan érkezik...</h4>
                    </Card>
                </Tab>

                <Tab eventKey="stats" title={<span className="fw-bold">📊 Statisztikák</span>}>
                     <Card className="bg-dark text-light border-secondary shadow-sm p-5 text-center mt-3">
                        <h4 className="text-muted">A Statisztika modul hamarosan érkezik...</h4>
                    </Card>
                </Tab>
            </Tabs>
        </Container>
    );
};

export default ClassroomDetail;