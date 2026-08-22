import React, { useState, useEffect } from 'react';
import { useParams, useNavigate, useLocation } from 'react-router-dom';
import { Container, Tab, Tabs, Card, Button, Badge, Row, Col, ListGroup, Modal, ProgressBar } from 'react-bootstrap';
import { classroomApi } from '../services/classroomApi';
import { assignmentApi } from '../services/assignmentApi';
import { useNotifications } from '../context/NotificationContext';
import { parseServerDate, formatToLocalDisplay } from '../utils/dateUtils';

/**
 * Dashboard component for students to view and interact with a specific classroom.
 * Handles the display of active/completed assignments, classmates, and personal statistics.
 * Integrates centralized timezone-safe date utilities and notification ping clearance.
 * 
 * @component
 */
const StudentClassroomDetail = () => {
    const { id: classroomId } = useParams();
    const navigate = useNavigate();
    const location = useLocation();
    
    const classroomName = location.state?.className || 'Osztályterem';

    // --- STATE MANAGEMENT ---
    const [assignments, setAssignments] = useState([]);
    const [classmates, setClassmates] = useState([]);
    const [isLoading, setIsLoading] = useState(true);

    const [studentSessions, setStudentSessions] = useState([]);
    const [isResultModalOpen, setIsResultModalOpen] = useState(false);
    const [selectedAssignmentTitle, setSelectedAssignmentTitle] = useState('');

    const [expandedPreviewId, setExpandedPreviewId] = useState(null);
    const [stats, setStats] = useState(null);

    const { notifications } = useNotifications();

    const [currentTime] = useState(() => Date.now());
    
    /**
     * Updates localStorage with viewed notification IDs and dispatches an event 
     * to update the notification badges globally.
     * 
     * @param {string} storageKey - The key under which the viewed IDs are stored.
     * @param {Array<string>} idsToMark - The array of specific IDs to mark as seen.
     */
    const markAsViewed = (storageKey, idsToMark) => {
        if (!idsToMark || idsToMark.length === 0) return;
        const stringIds = idsToMark.map(id => String(id));
        const seen = JSON.parse(localStorage.getItem(storageKey) || '[]');
        const updated = [...new Set([...seen, ...stringIds])];
        localStorage.setItem(storageKey, JSON.stringify(updated));
        
        window.dispatchEvent(new Event('local-storage-update'));
    };

    const viewedAssignments = JSON.parse(localStorage.getItem('viewedAssignments') || '[]');
    const viewedResults = JSON.parse(localStorage.getItem('viewedResults') || '[]');

    const unseenAssignmentsList = (notifications?.studentActiveAssignmentIds || []).filter(id => id.startsWith(classroomId) && !viewedAssignments.includes(id));
    const unseenResultsList = (notifications?.studentGradedSessionIds || []).filter(id => id.startsWith(classroomId) && !viewedResults.includes(id));

    const pingTrigger = (notifications?.studentActiveAssignmentIds?.length || 0) + (notifications?.studentGradedSessionIds?.length || 0);

    // Fetch data when component mounts or a relevant SSE ping arrives
    useEffect(() => {
        fetchClassroomData();
    // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [classroomId, pingTrigger]);

    // Automatically clear active assignment pings upon loading the assignments
    useEffect(() => {
        if (activeAssignments && activeAssignments.length > 0 && unseenAssignmentsList.length > 0) {
            markAsViewed('viewedAssignments', activeAssignments.map(a => `${classroomId}:${a.assignmentId}`));
        }
    // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [assignments]);
    
    /**
     * Fetches assignments, members, and personal statistics for the classroom.
     */
    const fetchClassroomData = async () => {
        setIsLoading(true);
        try {
            const [assignmentsRes, membersRes, statsRes] = await Promise.all([
                assignmentApi.getClassroomAssignments(classroomId),
                classroomApi.getMembers(classroomId, 'ACCEPTED'),
                assignmentApi.getStudentClassroomStatistics(classroomId)
            ]);
            setAssignments(assignmentsRes.data);
            setClassmates(membersRes.data);
            setStats(statsRes.data);
        } catch (error) {
            console.error("Hiba az adatok lekérésekor:", error);
        } finally {
            setIsLoading(false);
        }
    };

    // --- ASSIGNMENT FILTERING LOGIC ---

    const activeAssignments = assignments.filter(a => {
        if (a.completed) return false; 
        const availableUntil = parseServerDate(a.availableUntil);
        // Compare UTC millis safely
        if (availableUntil && availableUntil.getTime() <= currentTime) return false;
        return true;
    });

    const completedAssignments = assignments.filter(a => {
        if (a.completed) return true; 
        const availableUntil = parseServerDate(a.availableUntil);
        if (availableUntil && availableUntil.getTime() <= currentTime) return true; 
        return false;
    });

    // --- EVENT HANDLERS ---

    const handleViewResult = (assignment) => {
        const combinedId = `${classroomId}:${assignment.assignmentId}`;
        markAsViewed('viewedResults', [combinedId]);
        openStudentResultModal(assignment);
    };

    const openStudentResultModal = async (assignment) => {
        try {
            const res = await assignmentApi.getMyAssignmentSessions(assignment.assignmentId);
            setStudentSessions(res.data);
            setSelectedAssignmentTitle(assignment.title);
            setIsResultModalOpen(true);
        } catch (error) {
            console.error("Hiba az eredmények lekérésekor:", error);
        }
    };

    const togglePreview = (id) => {
        setExpandedPreviewId(prev => prev === id ? null : id);
    };

    // --- RENDER HELPERS ---

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
                
                {/* STUDENT ASSIGNMENTS TAB */}
                <Tab eventKey="assignments" title={<span className="fw-bold">📝 Feladatok</span>}>
                    
                    {/* ACTIVE AND COMPLETED ASSIGNMENTS */}
                   <Tabs defaultActiveKey="active" className="mt-3 mb-3 border-secondary" 
                          onSelect={(key) => {
                              if (key === 'active') {
                                  markAsViewed('viewedAssignments', activeAssignments.map(a => `${classroomId}:${a.assignmentId}`));
                              } else if (key === 'completed') {
                                  markAsViewed('viewedResults', completedAssignments.filter(a => a.hasGradedSession).map(a => `${classroomId}:${a.assignmentId}`));
                              }
                          }}>
                        
                        {/* 1. ACTIVE TODOS */}
                        <Tab eventKey="active" title={
                            <span className="fw-bold">
                                🔥 Aktív Teendők
                                {unseenAssignmentsList.length > 0 && (
                                    <Badge bg="danger" pill className="ms-2 shadow-sm animate-pulse">
                                        {unseenAssignmentsList.length}
                                    </Badge>
                                )}
                            </span>
                        }>
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
                                                        <div>
                                                            {a.attemptsUsed === 0 ? (
                                                                a.test ? (
                                                                    <Badge bg="danger" className="shadow-sm border border-light animate-pulse" style={{ letterSpacing: '1px' }}>🔴 ÚJ TESZT</Badge>
                                                                ) : (
                                                                    <Badge bg="info" text="dark" className="shadow-sm border border-dark animate-pulse">🔵 Új Gyakorló</Badge>
                                                                )
                                                            ) : (
                                                                a.test ? <Badge bg="danger">Teszt</Badge> : <Badge bg="success">Gyakorló</Badge>
                                                            )}
                                                        </div>
                                                    </div>
                                                    <Card.Text className="text-secondary flex-grow-1 small">{a.description}</Card.Text>
                                                    
                                                    <div className="mb-3 text-secondary bg-black bg-opacity-25 p-2 rounded" style={{ fontSize: '0.85rem' }}>
                                                        <div className="mb-1 text-info"><span className="fw-bold">📝 Próbálkozás:</span> {a.attemptsUsed} / {a.maxAttempts || '∞'}</div>
                                                        {a.availableFrom && (
                                                            <div className="mb-1"><span className="fw-bold">📅 Kezdés:</span> {formatToLocalDisplay(a.availableFrom)}</div>
                                                        )}
                                                        <div><span className="text-warning fw-bold">🕒 Határidő:</span> {formatToLocalDisplay(a.availableUntil)}</div>
                                                    </div>
                                                    
                                                    {(() => {
                                                        const availableFrom = parseServerDate(a.availableFrom);
                                                        const isFuture = availableFrom && availableFrom.getTime() > currentTime;
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

                        {/* 2. COMPLETED OR EXPIRED */}
                        <Tab eventKey="completed" title={
                            <span className="fw-bold">
                                ✅ Befejezett / Lejárt
                                {unseenResultsList.length > 0 && (
                                    <Badge bg="success" pill className="ms-2 shadow-sm animate-pulse">
                                        {unseenResultsList.length}
                                    </Badge>
                                )}
                            </span>
                        }>
                           <Row className="g-4 mt-1">
                                {completedAssignments.map(a => (
                                    <Col md={6} lg={4} key={a.assignmentId}>
                                        <Card className="h-100 bg-dark text-secondary border-secondary shadow-sm" style={{ opacity: 0.85 }}>
                                            <Card.Body className="d-flex flex-column">
                                                <div className="d-flex justify-content-between align-items-start mb-2">
                                                    <Card.Title className="fw-bold m-0 text-light">{a.title}</Card.Title>
                                                    
                                                    {unseenResultsList.includes(`${classroomId}:${a.assignmentId}`) ? (
                                                        <Badge bg="success" className="animate-pulse shadow-sm border border-light">✉️ Új Értékelés!</Badge>
                                                    ) : (
                                                        a.completed ? <Badge bg="secondary">Befejezve</Badge> : <Badge bg="secondary">Lejárt</Badge>
                                                    )}
                                                </div>
                                                
                                                {/* FIX: Utilizing Centralized formatToLocalDisplay */}
                                                <div className="mb-3 bg-black bg-opacity-25 p-2 rounded small">
                                                    <div className="mb-1 text-info"><span className="fw-bold">📝 Próbálkozás:</span> {a.attemptsUsed} / {a.maxAttempts || '∞'}</div>
                                                    {a.availableFrom && <div><span className="fw-bold">📅 Kezdés:</span> {formatToLocalDisplay(a.availableFrom)}</div>}
                                                    <div><span className="fw-bold">🕒 Határidő:</span> {formatToLocalDisplay(a.availableUntil)}</div>
                                                </div>
                                                
                                               <div className="mt-auto d-grid gap-2">
                                                    {a.hasGradedSession ? (
                                                        <Button 
                                                            variant="success" 
                                                            className="fw-bold"
                                                            onClick={() => handleViewResult(a)}
                                                        >
                                                            Eredmény megtekintése
                                                        </Button>
                                                    ) : (
                                                        <Button variant="outline-secondary" className="fw-bold" disabled>
                                                            Eredmény (Hamarosan)
                                                        </Button>
                                                    )}

                                                    {/* FIX: Timezone safe retry validation using parseServerDate */}
                                                    {(() => {
                                                        const availableUntil = parseServerDate(a.availableUntil);
                                                        const canStillWrite = (!availableUntil || availableUntil.getTime() > currentTime);
                                                        const hasAttemptsLeft = (!a.maxAttempts || a.attemptsUsed < a.maxAttempts);
                                                        
                                                        if (canStillWrite && hasAttemptsLeft) {
                                                            return (
                                                                <Button 
                                                                    variant="primary" 
                                                                    className="fw-bold"
                                                                    onClick={() => navigate(`/assignment/${a.assignmentId}/start`, { state: { assignmentDetails: a } })}
                                                                >
                                                                    Újraírás
                                                                </Button>
                                                            );
                                                        }
                                                        return null;
                                                    })()}
                                                </div>
                                            </Card.Body>
                                        </Card>
                                    </Col>
                                ))}
                            </Row>
                        </Tab>
                    </Tabs>
                </Tab>

                {/* CLASSMATES TAB */}
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

                {/* STATISTICS TAB */}
                <Tab eventKey="statistics" title={<span className="fw-bold">📊 Saját Statisztikám</span>}>
                    {stats && (
                        <div className="mt-4">
                            <Row className="g-4 mb-4 text-center">
                                <Col md={4}>
                                    <Card className="bg-dark border-info p-3 h-100 shadow-sm d-flex flex-column justify-content-center">
                                        <h6 className="text-secondary">Saját Átlagom</h6>
                                        <h2 className={`fw-bold m-0 ${stats.myAverage >= stats.classAverage ? 'text-success' : 'text-warning'}`}>
                                            {stats.myAverage}%
                                        </h2>
                                    </Card>
                                </Col>
                                <Col md={4}>
                                    <Card className="bg-dark border-secondary p-3 h-100 shadow-sm d-flex flex-column justify-content-center">
                                        <h6 className="text-secondary">Osztályátlag</h6>
                                        <h2 className="text-light fw-bold m-0">{stats.classAverage}%</h2>
                                    </Card>
                                </Col>
                                <Col md={4}>
                                    <Card className="bg-dark border-primary p-3 h-100 shadow-sm d-flex flex-column justify-content-center">
                                        <h6 className="text-secondary">Elvégzett feladatok</h6>
                                        <h2 className="text-primary fw-bold m-0">{stats.completedCount} / {stats.totalAssignments}</h2>
                                    </Card>
                                </Col>
                            </Row>

                            <Card className="bg-dark text-light border-secondary p-4 shadow-sm text-center">
                                <h5 className="text-info fw-bold mb-3">Haladási Állapot</h5>
                                <ProgressBar 
                                    now={stats.totalAssignments > 0 ? (stats.completedCount / stats.totalAssignments) * 100 : 0} 
                                    variant="success" 
                                    style={{ height: '20px' }} 
                                    className="border border-secondary bg-black"
                                />
                                <div className="mt-2 text-secondary small">
                                    {stats.totalAssignments - stats.completedCount > 0 
                                        ? `Még ${stats.totalAssignments - stats.completedCount} feladat vár rád!` 
                                        : "Minden feladattal végeztél ebben az osztályban! 🌟"}
                                </div>
                            </Card>
                        </div>
                    )}
                </Tab>
            </Tabs>

            {/* RESULTS PREVIEW MODAL */}
            <Modal show={isResultModalOpen} onHide={() => setIsResultModalOpen(false)} size="lg" centered contentClassName="bg-dark text-light border-secondary">
                <Modal.Header closeButton closeVariant="white" className="border-secondary">
                    <Modal.Title className="text-info fw-bold">Eredmények: {selectedAssignmentTitle}</Modal.Title>
                </Modal.Header>
                <Modal.Body className="p-4 custom-scrollbar" style={{ maxHeight: '70vh', overflowY: 'auto' }}>
                    {studentSessions.length === 0 ? (
                        <p className="text-muted">Nincs elérhető értékelés.</p>
                    ) : (
                        studentSessions.map((session, idx) => (
                            <div key={session.sessionId} className="mb-5 bg-black bg-opacity-25 p-3 rounded border border-secondary">
                                <div className="d-flex justify-content-between align-items-center mb-3 border-bottom border-secondary pb-2">
                                    <h5 className="fw-bold m-0 text-primary">{idx + 1}. Próbálkozás</h5>
                                    <div>
                                        <Badge bg="secondary" className="fs-6 me-2 shadow-sm">Gép: {session.finalScore}%</Badge>
                                        <Badge bg={session.teacherScore >= 50 ? "success" : "danger"} className="fs-6 shadow-sm">Tanár: {session.teacherScore}%</Badge>
                                    </div>
                                </div>
                                
                                {session.teacherComment && (
                                    <div className="mb-4 p-3 bg-darker rounded border border-info border-start border-4 shadow-sm">
                                        <div className="text-info small fw-bold mb-1">Tanári megjegyzés:</div>
                                        <div>{session.teacherComment}</div>
                                    </div>
                                )}

                                <div className="text-secondary fw-bold mb-3">Válaszaid:</div>
                                <ul className="list-unstyled">
                                    {session.answers?.map((ans, i) => {
                                        const previewId = `${session.sessionId}-${i}`;
                                        
                                        return (
                                            <li key={i} className="mb-3 p-3 bg-dark rounded border border-secondary shadow-sm">
                                                
                                                <div className="small text-info fw-bold mb-2 pb-1 border-bottom border-secondary d-flex justify-content-between align-items-center">
                                                    <div>{i + 1}. Kérdés: <span className="text-light fw-normal">{ans.question}</span></div>
                                                    
                                                    {ans.exercise && (
                                                        <Button variant="link" size="sm" className="text-info p-0 text-decoration-none" title="Feladat előnézete" onClick={() => togglePreview(previewId)}>
                                                            részletek👁️
                                                        </Button>
                                                    )}
                                                </div>

                                                {expandedPreviewId === previewId && ans.exercise && (
                                                    <div className="mt-1 mb-3 p-2 bg-black bg-opacity-25 rounded border border-info small text-light">
                                                        <strong>Típus:</strong> {ans.exercise.type}<br/>
                                                        {ans.exercise.content?.options && (
                                                            <div>
                                                                <strong>Opciók:</strong> {
                                                                    Array.isArray(ans.exercise.content.options) && typeof ans.exercise.content.options[0] === 'object' 
                                                                    ? ans.exercise.content.options.map(o => o.text).join(", ") 
                                                                    : ans.exercise.content.options.join(", ")
                                                                }
                                                            </div>
                                                        )}
                                                        {ans.exercise.content?.words && <div><strong>Szavak:</strong> {ans.exercise.content.words.join(", ")}</div>}
                                                        {ans.exercise.content?.correctTranslation && <div><strong>Helyes fordítás:</strong> {ans.exercise.content.correctTranslation}</div>}
                                                        {ans.exercise.content?.correctAnswer && <div><strong>Helyes válasz:</strong> {ans.exercise.content.correctAnswer}</div>}
                                                    </div>
                                                )}

                                                <div className="d-flex justify-content-between align-items-center flex-wrap gap-2">
                                                    <div>
                                                        <span className="text-secondary fw-bold">Válaszod: </span>
                                                        <span className="text-light">
                                                            {typeof ans.studentAnswer === 'object' ? JSON.stringify(ans.studentAnswer) : ans.studentAnswer}
                                                        </span>
                                                    </div>
                                                    <div className="d-flex gap-2">
                                                        {ans.correct ? <Badge bg="success">Helyes</Badge> : <Badge bg="danger">Hibás</Badge>}
                                                        {ans.retried && <Badge bg="warning" text="dark">Javítva</Badge>}
                                                    </div>
                                                </div>
                                            </li>
                                        );
                                    })}
                                </ul>
                            </div>
                        ))
                    )}
                </Modal.Body>
                <Modal.Footer className="border-secondary">
                    <Button variant="outline-light" onClick={() => setIsResultModalOpen(false)}>Bezárás</Button>
                </Modal.Footer>
            </Modal>

        </Container>
    );
};

export default StudentClassroomDetail;