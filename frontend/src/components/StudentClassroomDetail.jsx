import { safeError } from '../utils/safeError';
import React, { useState, useEffect, useCallback } from 'react';
import { useParams, useNavigate, useLocation } from 'react-router-dom';
import { Container, Tab, Tabs, Card, Button, Badge, Row, Col, ListGroup, Modal, ProgressBar } from 'react-bootstrap';
import { classroomApi } from '../services/classroomApi';
import { assignmentApi } from '../services/assignmentApi';
import { useNotifications } from '../context/NotificationContext';
import { useAuth } from '../context/AuthContext';
import { getUnseenIds, markAsViewed } from '../utils/viewedStore';
import { parseServerDate, formatToLocalDisplay } from '../utils/dateUtils';
import { useCurrentTime } from '../hooks/useCurrentTime';
import ExercisePreviewCard from '../components/ExercisePreviewCard';

const StudentClassroomDetail = ({ classroom }) => {
    const { id: classroomId } = useParams();
    const navigate = useNavigate();
    const location = useLocation();
    const { user } = useAuth();
    const { notifications } = useNotifications();
    
    const classroomName = classroom?.name || location.state?.className || 'Osztályterem';

    const [assignments, setAssignments] = useState([]);
    const [classmates, setClassmates] = useState([]);
    const [isLoading, setIsLoading] = useState(true);

    const [activeMainTab, setActiveMainTab] = useState('assignments');
    const [activeSubTab, setActiveSubTab] = useState('active');

    const [studentSessions, setStudentSessions] = useState([]);
    const [isResultModalOpen, setIsResultModalOpen] = useState(false);
    const [selectedAssignmentTitle, setSelectedAssignmentTitle] = useState('');

    const [expandedPreviewId, setExpandedPreviewId] = useState(null);
    const [stats, setStats] = useState(null);
    const currentTime = useCurrentTime();

    // Az adott osztályteremhez tartozó olvasatlan elemek kiszűrése
    const classroomActiveIds = (notifications?.studentActiveAssignmentIds || []).filter(id => id.startsWith(`${classroomId}:`));
    const classroomGradedIds = (notifications?.studentGradedSessionIds || []).filter(id => id.startsWith(`${classroomId}:`));

    const unseenAssignmentsList = getUnseenIds('viewedAssignments', user?.userId, classroomActiveIds);
    const unseenResultsList = getUnseenIds('viewedResults', user?.userId, classroomGradedIds);

    // Pontos pingTrigger: ha bármelyik ID kicserélődik vagy új session érkezik, azonnal észleli
    const pingTrigger = JSON.stringify([classroomActiveIds, classroomGradedIds]);

    const fetchClassroomData = useCallback(async (silent = false) => {
        if (!silent) setIsLoading(true);
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
            console.error("Hiba az adatok lekérésekor:", safeError(error));
        } finally {
            if (!silent) setIsLoading(false);
        }
    }, [classroomId]);

    // Első betöltéskor spinnerrel, ping érkezésekor csendben (silent = true) frissítünk
    useEffect(() => {
        fetchClassroomData(false);
    }, [fetchClassroomData]);

    useEffect(() => {
        if (!isLoading) {
            fetchClassroomData(true);
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [pingTrigger]);

    // Olvasottság automatikus jelölése az éppen nyitott fül alapján
    useEffect(() => {
        if (isLoading || activeMainTab !== 'assignments') return;

        if (activeSubTab === 'active' && unseenAssignmentsList.length > 0) {
            markAsViewed('viewedAssignments', user?.userId, unseenAssignmentsList);
        } else if (activeSubTab === 'completed' && unseenResultsList.length > 0) {
            markAsViewed('viewedResults', user?.userId, unseenResultsList);
        }
    }, [isLoading, activeMainTab, activeSubTab, unseenAssignmentsList, unseenResultsList, user?.userId]);

    const activeAssignments = assignments.filter(a => {
        if (a.completed && !a.ongoingSessionId) return false;
        const availableUntil = parseServerDate(a.availableUntil);
        if (availableUntil && availableUntil.getTime() <= currentTime && !(a.ongoingSessionId && a.allowLateSubmission)) return false;
        return true;
    });

    const completedAssignments = assignments.filter(a => {
        const until = parseServerDate(a.availableUntil);
        if (a.ongoingSessionId && (!until || until.getTime() > currentTime || a.allowLateSubmission)) return false;
        if (a.completed) return true; 
        const availableUntil = parseServerDate(a.availableUntil);
        if (availableUntil && availableUntil.getTime() <= currentTime) return true; 
        return false;
    });

    const hasUnseenResultForAssignment = (assignmentId) => {
        const prefix = `${classroomId}:${assignmentId}:`;
        return unseenResultsList.some(id => id.startsWith(prefix));
    };

    const handleViewResult = (assignment) => {
        const prefix = `${classroomId}:${assignment.assignmentId}:`;
        const matchingIds = unseenResultsList.filter(id => id.startsWith(prefix));
        markAsViewed('viewedResults', user?.userId, matchingIds);
        openStudentResultModal(assignment);
    };

    const openStudentResultModal = async (assignment) => {
        try {
            const res = await assignmentApi.getMyAssignmentSessions(assignment.assignmentId);
            setStudentSessions(res.data);
            setSelectedAssignmentTitle(assignment.title);
            setIsResultModalOpen(true);
        } catch (error) {
            console.error("Hiba az eredmények lekérésekor:", safeError(error));
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
                <h1 className="h2 fw-bold m-0 app-accent-text">{classroomName}</h1>
            </div>

           <Tabs id="student-classroom-tabs" aria-label="Osztálytermi funkciók" activeKey={activeMainTab} onSelect={(k) => setActiveMainTab(k)} className="mb-4 custom-dark-tabs">
                {/* STUDENT ASSIGNMENTS TAB */}
                <Tab eventKey="assignments" title={<span className="fw-bold">📝 Feladatok</span>}>
                    
                    {/* ACTIVE AND COMPLETED ASSIGNMENTS */}
                 <Tabs id="student-assignment-tabs" aria-label="Feladatok állapota" activeKey={activeSubTab} onSelect={(k) => setActiveSubTab(k)} className="mt-3 mb-3 border-secondary">
                        
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
                                                    <div className="d-flex flex-wrap gap-2 justify-content-between align-items-start mb-2">
                                                        <Card.Title as="h2" className="h5 fw-bold m-0">{a.title}</Card.Title>
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
                                                                aria-label={`${isFuture ? 'Még nem elérhető' : a.ongoingSessionId ? 'Folytatás' : 'Indítás'}: ${a.title}`}
                                                                variant={isFuture ? "secondary" : "primary"} 
                                                                className="w-100 fw-bold shadow-sm" 
                                                                disabled={isFuture}
                                                                onClick={() => a.ongoingSessionId ? navigate(`/assignment/session/${a.ongoingSessionId}/play`)
                                                                    : navigate(`/assignment/${a.assignmentId}/start`, { state: { assignmentDetails: a } })}
                                                            >
                                                                {isFuture ? 'Még nem elérhető' : a.ongoingSessionId ? 'Folytatás' : 'Indítás'}
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
                                        <Card className="h-100 bg-dark text-secondary border-secondary shadow-sm">
                                            <Card.Body className="d-flex flex-column">
                                                <div className="d-flex flex-wrap gap-2 justify-content-between align-items-start mb-2">
                                                    <Card.Title as="h2" className="h5 fw-bold m-0 text-light">{a.title}</Card.Title>
                                                    
                                                   {hasUnseenResultForAssignment(a.assignmentId) ? (
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
                                                            aria-label={`Eredmény megtekintése: ${a.title}`}
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
                                                                    aria-label={`Újraírás: ${a.title}`}
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
            <Modal show={isResultModalOpen} onHide={() => setIsResultModalOpen(false)} aria-labelledby="assignment-results-title" size="lg" centered contentClassName="bg-dark text-light border-secondary">
                <Modal.Header closeButton closeLabel="Ablak bezárása" className="border-secondary">
                    <Modal.Title id="assignment-results-title" className="app-accent-text fw-bold">Eredmények: {selectedAssignmentTitle}</Modal.Title>
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
                                                        <Button aria-label={`részletek: ${idx + 1}. próbálkozás, ${i + 1}. kérdés`} aria-expanded={expandedPreviewId === previewId} variant="link" size="sm" className="text-info p-0 text-decoration-none" onClick={() => togglePreview(previewId)}>
                                                            részletek<span aria-hidden="true">👁️</span>
                                                        </Button>
                                                    )}
                                                </div>

                                                {expandedPreviewId === previewId && ans.exercise && (
                                                    <div className="mt-2 mb-3">
                                                        <ExercisePreviewCard 
                                                            exercise={ans.exercise} 
                                                            serverCorrectAnswer={ans.serverCorrectAnswer} 
                                                        />
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
                    <Button variant="outline-secondary" onClick={() => setIsResultModalOpen(false)}>Bezárás</Button>
                </Modal.Footer>
            </Modal>

        </Container>
    );
};

export default StudentClassroomDetail;
