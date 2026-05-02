import React, { useState, useEffect, useCallback } from 'react';
import { useParams, useNavigate, useLocation } from 'react-router-dom';
import { Container, Tab, Tabs, Card, Button, Badge, Row, Col, ListGroup, Modal, Form, Spinner, Table, ProgressBar } from 'react-bootstrap';
import { classroomApi } from '../services/classroomApi';
import { assignmentApi } from '../services/assignmentApi';
import { lessonApi } from '../services/lessonApi';
import { useNotifications } from '../context/NotificationContext';

const TeacherClassroomDetail = () => {
    const { id: classroomId } = useParams();
    const navigate = useNavigate();
    const location = useLocation();
    
    const classroomName = location.state?.className || 'Osztályterem Kezelése';

    const [pendingMembers, setPendingMembers] = useState([]);
    const [acceptedMembers, setAcceptedMembers] = useState([]);
    const [assignments, setAssignments] = useState([]);
    const [isAssignmentModalOpen, setIsAssignmentModalOpen] = useState(false);
    
    const [lessons, setLessons] = useState([]);
    const [selectedLessonId, setSelectedLessonId] = useState('');
    const [availableExercises, setAvailableExercises] = useState([]);
    
    const [selectedExercisesData, setSelectedExercisesData] = useState([]);
    const [assignmentMode, setAssignmentMode] = useState('TEST'); 

    const [stats, setStats] = useState(null);

    const { notifications } = useNotifications();

    const [assignmentForm, setAssignmentForm] = useState({
        title: '',
        description: '',
        hasFeedback: false,
        isTest: false,
        isRandomized: true,
        allowRetries: false,
        maxAttempts: '',
        timeLimitMinutes: '',
        availableFrom: '',
        availableUntil: '',
        exerciseIds: []
    });

    const [previewExerciseId, setPreviewExerciseId] = useState(null);
    const [isSubmitting, setIsSubmitting] = useState(false);
    const [isLoading, setIsLoading] = useState(true);


    const parseDate = (d) => {
        if (!d) return null;
        if (Array.isArray(d)) {
            return new Date(d[0], d[1] - 1, d[2], d[3] || 0, d[4] || 0, d[5] || 0);
        }
        return new Date(d);
    };

    const formatTime = (dateData) => {
        const date = parseDate(dateData);
        if (!date) return "Nincs megadva";
        return date.toLocaleString('hu-HU', { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' });
    };

    const fetchDashboardData = useCallback(async () => {
        setIsLoading(true);
        try {
            const [pendingRes, acceptedRes, assignmentsRes] = await Promise.all([
                classroomApi.getMembers(classroomId, 'PENDING'),
                classroomApi.getMembers(classroomId, 'ACCEPTED'),
                assignmentApi.getClassroomAssignments(classroomId)
            ]);
            setPendingMembers(pendingRes.data);
            setAcceptedMembers(acceptedRes.data);
            setAssignments(assignmentsRes.data);

            const statsRes = await assignmentApi.getClassroomStatistics(classroomId);
            setStats(statsRes.data);

        } catch (error) {
            console.error("Hiba az adatok lekérésekor:", error);
        } finally {
            setIsLoading(false);
        }
    }, [classroomId]);

    const pingTrigger = (notifications?.teacherUngradedSubmissions || 0) + (notifications?.teacherPendingJoinRequests || 0);

    useEffect(() => {
        fetchDashboardData();
    }, [fetchDashboardData, pingTrigger]);

    // A memória kezelő
    const viewedTeacherPending = JSON.parse(localStorage.getItem('viewedTeacherPending') || '[]');
    const viewedTeacherUngraded = JSON.parse(localStorage.getItem('viewedTeacherUngraded') || '[]');

    const markAsViewed = (storageKey, idsToMark) => {
        if (!idsToMark || idsToMark.length === 0) return;
        const stringIds = idsToMark.map(id => String(id));
        const seen = JSON.parse(localStorage.getItem(storageKey) || '[]');
        const updated = [...new Set([...seen, ...stringIds])];
        localStorage.setItem(storageKey, JSON.stringify(updated));
        window.dispatchEvent(new Event('local-storage-update'));
    };

    // Kiszámoljuk, mik az aktuális teremből jövő, olvasatlan pingek
    const unseenPendingList = (notifications?.teacherPendingJoinRequestIds || []).filter(id => id.startsWith(classroomId) && !viewedTeacherPending.includes(id));
    const unseenUngradedList = (notifications?.teacherUngradedSubmissionIds || []).filter(id => id.startsWith(classroomId) && !viewedTeacherUngraded.includes(id));

    // Amikor kattint az eredményekre, levesszük a pinget:
    const handleViewSubmissions = (assignment) => {
        markAsViewed('viewedTeacherUngraded', [`${classroomId}:${assignment.assignmentId}`]);
        navigate(`/assignment/${assignment.assignmentId}/submissions`, { state: { assignmentTitle: assignment.title, classroomName: classroomName } });
    };

    // --- MEMBER MANAGEMENT ---
    const handleModerate = async (studentId, approve) => {
        try { await classroomApi.moderateMember(classroomId, studentId, approve); fetchDashboardData(); }
        catch (error) { console.error("Hiba a moderálás során:", error); }
    };

    const handleKick = async (studentId) => {
        if (window.confirm("Biztosan el akarod távolítani ezt a diákot az osztályból?")) {
            try { await classroomApi.kickMember(classroomId, studentId); fetchDashboardData(); }
            catch (error) { console.error("Hiba a diák eltávolításakor:", error); }
        }
    };

    // --- ASSIGNMENT CREATION LOGIC ---
    
    const openAssignmentModal = async () => {
        try {
            const res = await lessonApi.getAllLessons();
            setLessons(res.data);
            setIsAssignmentModalOpen(true);
        } catch (error) {
            console.error("Hiba a leckék betöltésekor:", error);
        }
    };

    const handleLessonChange = async (e) => {
        const lessonId = e.target.value;
        setSelectedLessonId(lessonId);
        if (lessonId) {
            try {
                const res = await lessonApi.getLessonExercises(lessonId);
                setAvailableExercises(res.data);
            } catch (error) { console.error("Hiba a feladatok betöltésekor:", error); }
        } else {
            setAvailableExercises([]);
        }
    };

    const handleDeleteAssignment = async (assignmentId) => {
        if (window.confirm("Biztosan törölni szeretnéd ezt a feladatot? Figyelem: Ha már vannak diákok, akik elkezdték vagy beküldték, az ő eredményeik is véglegesen elvesznek!")) {
            try {
                await assignmentApi.deleteAssignment(assignmentId);
                fetchDashboardData();
            } catch (error) {
                console.error("Hiba a feladat törlésekor:", error);
                alert("Nem sikerült törölni a feladatot.");
            }
        }
    };

    /**
     * Toggles the selection of a specific exercise for the assignment.
     */
    const toggleExerciseSelection = (exercise) => {
        const exerciseId = exercise.exerciseId;
        const isSelected = assignmentForm.exerciseIds.includes(exerciseId);

        if (isSelected) {
            setAssignmentForm(prev => ({ ...prev, exerciseIds: prev.exerciseIds.filter(id => id !== exerciseId) }));
            setSelectedExercisesData(prev => prev.filter(ex => ex.id !== exerciseId));
        } else {
            setAssignmentForm(prev => ({ ...prev, exerciseIds: [...prev.exerciseIds, exerciseId] }));
            setSelectedExercisesData(prev => [...prev, { id: exerciseId, title: exercise.content?.question || exercise.type }]);
        }
    };

    /**
     * Selects all exercises in a lesson, without 
     * deleting previos selections from other lessons.
     */
    const handleSelectAllExercises = () => {
        const newIds = [];
        const newData = [];

        availableExercises.forEach(ex => {
            if (!assignmentForm.exerciseIds.includes(ex.exerciseId)) {
                newIds.push(ex.exerciseId);
                newData.push({ id: ex.exerciseId, title: ex.content?.question || ex.type });
            }
        });

        if (newIds.length > 0) {
            setAssignmentForm(prev => ({ ...prev, exerciseIds: [...prev.exerciseIds, ...newIds] }));
            setSelectedExercisesData(prev => [...prev, ...newData]);
        }
    };

  
    const removeFromSummary = (id) => {
        setAssignmentForm(prev => ({ ...prev, exerciseIds: prev.exerciseIds.filter(exId => exId !== id) }));
        setSelectedExercisesData(prev => prev.filter(ex => ex.id !== id));
    };

    const togglePreview = (exerciseId) => {
        setPreviewExerciseId(prev => prev === exerciseId ? null : exerciseId);
    };

    const handleAssignmentSubmit = async (e) => {
        e.preventDefault();
        if (isSubmitting) return; 
        setIsSubmitting(true);

        const formatLocalTime = (dateStr) => {
            if (!dateStr) return null;
            return dateStr.length === 16 ? dateStr + ':00' : dateStr;
        };

        const payload = {
            title: assignmentForm.title,
            description: assignmentForm.description,
            isTest: assignmentMode === 'TEST',
            hasFeedback: assignmentMode === 'TEST' ? assignmentForm.hasFeedback : true,
            randomized: assignmentForm.isRandomized,
            allowRetries: assignmentMode === 'TEST' ? assignmentForm.allowRetries : true,
            maxAttempts: assignmentMode === 'TEST' && assignmentForm.maxAttempts ? parseInt(assignmentForm.maxAttempts) : null,
            timeLimitMinutes: assignmentForm.timeLimitMinutes ? parseInt(assignmentForm.timeLimitMinutes) : null,
            
            availableFrom: formatLocalTime(assignmentForm.availableFrom),
            availableUntil: formatLocalTime(assignmentForm.availableUntil),
            
            exerciseIds: assignmentForm.exerciseIds
        };

       try {
            await assignmentApi.createAssignment(classroomId, payload);
            setIsAssignmentModalOpen(false);
            setAssignmentForm({
                title: '', description: '', hasFeedback: false, isTest: false, isRandomized: true,
                allowRetries: false, maxAttempts: '', timeLimitMinutes: '', availableFrom: '', availableUntil: '', exerciseIds: []
            });
            setSelectedExercisesData([]);
            setAssignmentMode('TEST');
            setSelectedLessonId('');
            setAvailableExercises([]);
            fetchDashboardData(); 
        } catch (error) {
            console.error("Hiba a feladat kiosztásakor:", error);
            alert(error.response?.data?.message || "Hiba történt a mentés során.");
        } finally {
            setIsSubmitting(false); 
        }
    };

    if (isLoading) {
        return <Container className="d-flex justify-content-center align-items-center" style={{ height: '50vh' }}><div className="spinner-border text-primary"></div></Container>;
    }

    const now = new Date();
    
    // 1. Aktív: Már elkezdődött (vagy nincs kezdete) ÉS még nincs vége (vagy nincs vége)
    const activeAssignments = assignments.filter(a => {
        const from = parseDate(a.availableFrom);
        const until = parseDate(a.availableUntil);
        return (!from || from <= now) && (!until || until > now);
    });

    // 2. Ütemezett: Még nem kezdődött el
    const scheduledAssignments = assignments.filter(a => {
        const from = parseDate(a.availableFrom);
        return from && from > now;
    });

    // 3. Lejárt: Már elmúlt a határideje
    const expiredAssignments = assignments.filter(a => {
        const until = parseDate(a.availableUntil);
        return until && until <= now;
    });

    // 1. Segédfüggvény a biztonságos (kisbetű/nagybetű független) egyezéshez
    const hasUngradedSubmission = (assignmentId) => {
        const targetId = `${classroomId}:${assignmentId}`.toLowerCase();
        return unseenUngradedList.some(id => id.toLowerCase() === targetId);
    };

    // 2. Kiszámoljuk, melyik belső fülön mennyi új beadás van, hogy oda is tegyünk pöttyöt!
    const activeUngradedCount = activeAssignments.filter(a => hasUngradedSubmission(a.assignmentId)).length;
    const scheduledUngradedCount = scheduledAssignments.filter(a => hasUngradedSubmission(a.assignmentId)).length;
    const expiredUngradedCount = expiredAssignments.filter(a => hasUngradedSubmission(a.assignmentId)).length;

    const renderAssignmentCard = (a, status) => (
        <Col md={6} lg={4} key={a.assignmentId}>
            <Card className={`h-100 bg-dark text-light border-secondary shadow-sm ${status === 'expired' ? 'opacity-75' : ''}`}>
                <Card.Body className="d-flex flex-column">
                    <div className="d-flex justify-content-between align-items-start mb-2">
                        <Card.Title className="fw-bold text-primary m-0">{a.title}</Card.Title>
                        <div>
                            {/* A BIZTOS PING ELLENŐRZÉS */}
                            {hasUngradedSubmission(a.assignmentId) && (
                                <Badge bg="danger" className="animate-pulse shadow-sm me-2 border border-light">🔴 ÚJ BEKÜLDÉS</Badge>
                            )}
                            {status === 'scheduled' && <Badge bg="warning" text="dark" className="me-2">Ütemezett</Badge>}
                            {a.test ? <Badge bg="danger">Teszt</Badge> : <Badge bg="success">Gyakorló</Badge>}
                        </div>
                    </div>
                    
                    <Card.Text className="text-secondary mb-3 small flex-grow-1">{a.description}</Card.Text>

                    {/* IDŐPONTOK MEGJELENÍTÉSE */}
                    <div className="mb-3 p-2 bg-black bg-opacity-25 rounded border border-secondary" style={{ fontSize: '0.8rem' }}>
                        <div className="d-flex justify-content-between mb-1">
                            <span className="text-secondary">📅 Elérhető:</span>
                            <span className="text-info fw-bold">{formatTime(a.availableFrom)}</span>
                        </div>
                        <div className="d-flex justify-content-between">
                            <span className="text-secondary">🕒 Határidő:</span>
                            <span className={`fw-bold ${status === 'expired' ? 'text-danger' : 'text-warning'}`}>
                                {formatTime(a.availableUntil)}
                            </span>
                        </div>
                    </div>

                    <div className="mt-auto pt-2 border-top border-secondary d-flex gap-2">
                        <Button 
                            variant="outline-info" 
                            size="sm" 
                            className="flex-grow-1 fw-bold"
                           onClick={() => handleViewSubmissions(a)}
                        >
                            📊 Eredmények
                        </Button>
                        <Button variant="outline-danger" size="sm" onClick={() => handleDeleteAssignment(a.assignmentId)}>törlés🗑️</Button>
                    </div>
                </Card.Body>
            </Card>
        </Col>
    );

    return (
        <Container className="py-4 text-light">
            <div className="d-flex justify-content-between align-items-center mb-4">
                <div>
                    <Button variant="outline-secondary" size="sm" onClick={() => navigate('/classrooms')} className="mb-2">← Vissza</Button>
                    <h2 className="fw-bold m-0">{classroomName}</h2>
                </div>
            </div>

            <Tabs defaultActiveKey="assignments" className="mb-4 custom-dark-tabs"
                  onSelect={(key) => {
                      if (key === 'members' && unseenPendingList.length > 0) {
                          markAsViewed('viewedTeacherPending', pendingMembers.map(m => `${classroomId}:${m.userId}`));
                      }
                  }}>

                <Tab eventKey="assignments" title={
                    <span className="fw-bold">
                        📝 Feladatok & Tesztek
                        {unseenUngradedList.length > 0 && <Badge bg="danger" pill className="ms-2 animate-pulse">{unseenUngradedList.length}</Badge>}
                    </span>}>
                    
                    <div className="mb-4 mt-3">
                        <Button variant="primary" className="fw-bold px-4 shadow-sm" onClick={openAssignmentModal}>+ Új Feladat / Teszt Kiírása</Button>
                    </div>

            <Tabs defaultActiveKey="active" className="mb-3 border-secondary custom-dark-tabs">
                
                {/* 1. AKTUÁLISAN FUTÓ FELADATOK */}
                <Tab eventKey="active" title={
                    <span className="fw-bold text-info">
                        🔥 Aktív ({activeAssignments.length})
                        {activeUngradedCount > 0 && <Badge bg="danger" pill className="ms-2 animate-pulse shadow-sm">{activeUngradedCount}</Badge>}
                    </span>
                }>
                    <Row className="g-4 mt-1">
                        {activeAssignments.map(a => renderAssignmentCard(a, 'active'))}
                    </Row>
                </Tab>

                {/* 2. JÖVŐBEN INDULÓ FELADATOK */}
                <Tab eventKey="scheduled" title={
                    <span className="fw-bold text-warning">
                        📅 Ütemezett ({scheduledAssignments.length})
                        {scheduledUngradedCount > 0 && <Badge bg="danger" pill className="ms-2 animate-pulse shadow-sm">{scheduledUngradedCount}</Badge>}
                    </span>
                }>
                    <Row className="g-4 mt-1">
                        {scheduledAssignments.map(a => renderAssignmentCard(a, 'scheduled'))}
                    </Row>
                </Tab>

                {/* 3. ARCHÍVUM */}
                <Tab eventKey="expired" title={
                    <span className="fw-bold text-secondary">
                        ⏳ Lejárt ({expiredAssignments.length})
                        {expiredUngradedCount > 0 && <Badge bg="danger" pill className="ms-2 animate-pulse shadow-sm">{expiredUngradedCount}</Badge>}
                    </span>
                }>
                    <Row className="g-4 mt-1">
                        {expiredAssignments.map(a => renderAssignmentCard(a, 'expired'))}
                    </Row>
                </Tab>
            </Tabs>
        </Tab>

                <Tab eventKey="members" title={
                    
                    <span className="fw-bold">
                        👥 Tagság kezelése
                        {unseenPendingList.length > 0 && <Badge bg="danger" pill className="ms-2 animate-pulse">{unseenPendingList.length}</Badge>}
                    </span>}>

                    <Row className="g-4 mt-1">
                        <Col md={6}>
                            <Card className="bg-dark border-secondary h-100">
                                <Card.Header className="bg-dark border-secondary d-flex justify-content-between">
                                    <h5 className="m-0 text-warning">Várakozó</h5>
                                        <Badge bg={pendingMembers.length > 0 ? "danger" : "warning"} text={pendingMembers.length > 0 ? "light" : "dark"} className={pendingMembers.length > 0 ? "shadow animate-pulse" : ""}>
                                            {pendingMembers.length}
                                        </Badge>
                                </Card.Header>
                                <ListGroup variant="flush">
                                    {pendingMembers.map(member => (
                                        <ListGroup.Item key={member.memberId} className="bg-dark text-light border-secondary d-flex justify-content-between">
                                            <div><div className="fw-bold">{member.studentName || member.name}</div><div className="text-secondary small">{member.email}</div></div>
                                            <div className="d-flex gap-2"><Button variant="success" size="sm" onClick={() => handleModerate(member.userId, true)}>✓</Button><Button variant="outline-danger" size="sm" onClick={() => handleModerate(member.userId, false)}>✕</Button></div>
                                        </ListGroup.Item>
                                    ))}
                                </ListGroup>
                            </Card>
                        </Col>
                        <Col md={6}>
                            <Card className="bg-dark border-secondary h-100">
                                <Card.Header className="bg-dark border-secondary d-flex justify-content-between">
                                    <h5 className="m-0 text-info">Aktív</h5><Badge bg="info" text="dark">{acceptedMembers.length}</Badge>
                                </Card.Header>
                                <ListGroup variant="flush">
                                    {acceptedMembers.map(member => (
                                        <ListGroup.Item key={member.memberId} className="bg-dark text-light border-secondary d-flex justify-content-between">
                                            <div><div className="fw-bold">{member.studentName || member.name} <span className="text-light small">#{member.userTag}</span></div><div className="text-secondary small">Belépett: {new Date(member.joinedAt).toLocaleDateString()}</div></div>
                                            <Button variant="outline-danger" size="sm" onClick={() => handleKick(member.userId)}>Kirúgás</Button>
                                        </ListGroup.Item>
                                    ))}
                                </ListGroup>
                            </Card>
                        </Col>
                    </Row>
                </Tab>

                <Tab eventKey="statistics" title={<span className="fw-bold">📊 Statisztika & Haladás</span>}>
                    {stats && (
                            <div className="mt-4">
                                <Row className="mb-4 text-center">
                                    <Col md={6}>
                                        <Card className="bg-dark border-info p-3">
                                            <h6 className="text-secondary">Osztályterem Átlag</h6>
                                            <h2 className="text-info fw-bold">{stats.classAverage}%</h2>
                                        </Card>
                                    </Col>
                                    <Col md={6}>
                                        <Card className="bg-dark border-success p-3">
                                            <h6 className="text-secondary">Kiadott feladatok</h6>
                                            <h2 className="text-success fw-bold">{stats.totalAssignments} db</h2>
                                        </Card>
                                    </Col>
                                </Row>

                                <Card className="bg-dark text-light border-secondary shadow-lg">
                                    <Card.Header className="bg-dark border-secondary fw-bold text-primary">
                                        Diákok Aggregált Teljesítménye
                                    </Card.Header>
                                    <Table hover variant="dark" responsive className="m-0">
                                        <thead>
                                            <tr className="text-secondary">
                                                <th>Diák Neve</th>
                                                <th className="text-center">Befejezett</th>
                                                <th className="text-center">Haladás</th>
                                                <th className="text-center">Átlagpont</th>
                                                <th>Utolsó Aktivitás</th>
                                            </tr>
                                        </thead>
                                        <tbody>
                                            {stats.studentProgress.map((s, idx) => (
                                                <tr key={idx} className="align-middle">
                                                    <td>
                                                        <div className="fw-bold">{s.studentName}</div>
                                                        <div className="small text-secondary">{s.studentEmail}</div>
                                                    </td>
                                                    <td className="text-center">{s.completedCount} / {stats.totalAssignments}</td>
                                                    <td className="text-center" style={{ width: '150px' }}>
                                                        <ProgressBar 
                                                            now={(s.completedCount / stats.totalAssignments) * 100} 
                                                            variant="info" 
                                                            style={{ height: '8px' }} 
                                                        />
                                                    </td>
                                                    <td className="text-center">
                                                        <Badge bg={s.averageScore >= 80 ? "success" : s.averageScore >= 50 ? "warning" : "danger"} className="fs-6">
                                                            {s.averageScore}%
                                                        </Badge>
                                                    </td>
                                                    <td className="text-secondary small">
                                                        {s.lastActivity !== "Nincs adat" ? new Date(s.lastActivity).toLocaleString('hu-HU') : "Még nem kezdte el"}
                                                    </td>
                                                </tr>
                                            ))}
                                        </tbody>
                                    </Table>
                                </Card>
                            </div>
                        )}
                </Tab>
            </Tabs>

            <Modal show={isAssignmentModalOpen} onHide={() => setIsAssignmentModalOpen(false)} size="xl" centered contentClassName="bg-dark text-light border-secondary">
                <Modal.Header closeButton className="border-secondary" closeVariant="white">
                    <Modal.Title className="fw-bold text-primary">Feladat / Tananyag Összeállítása</Modal.Title>
                </Modal.Header>
                <Form onSubmit={handleAssignmentSubmit}>
                    <Modal.Body>
                        <Row>
                            {/* BAL: ALAPOK */}
                            <Col lg={4}>
                                <div className="p-3 border border-secondary rounded bg-darker mb-3">
                                    <Form.Label className="text-info fw-bold mb-2">Cél:</Form.Label>
                                    <Form.Check type="radio" label="Szigorú Teszt" name="amode" checked={assignmentMode === 'TEST'} onChange={() => setAssignmentMode('TEST')} className="text-danger fw-bold mb-1" />
                                    <Form.Check type="radio" label="Tananyag / Gyakorló" name="amode" checked={assignmentMode === 'PRACTICE'} onChange={() => setAssignmentMode('PRACTICE')} className="text-success fw-bold mb-3" />
                                    
                                    <Form.Group className="mb-3"><Form.Label className="small text-secondary fw-bold">Cím</Form.Label><Form.Control type="text" required className="bg-dark text-light border-secondary" value={assignmentForm.title} onChange={e => setAssignmentForm({...assignmentForm, title: e.target.value})} /></Form.Group>
                                    <Form.Group className="mb-3"><Form.Label className="small text-secondary fw-bold">Leírás</Form.Label><Form.Control as="textarea" rows={2} className="bg-dark text-light border-secondary" value={assignmentForm.description} onChange={e => setAssignmentForm({...assignmentForm, description: e.target.value})} /></Form.Group>
                                    <Form.Group className="mb-3"><Form.Label className="small text-secondary fw-bold">Idő (perc)</Form.Label><Form.Control type="number" 
                                            min="1" 
                                            className="bg-dark text-light border-secondary" 
                                            value={assignmentForm.timeLimitMinutes} 
                                            onChange={e => {
                                                const val = e.target.value.replace(/\D/g, '');
                                                setAssignmentForm({...assignmentForm, timeLimitMinutes: val});
                                            }} /></Form.Group>
                                    
                                    {assignmentMode === 'TEST' && (
                                        <Form.Group className="mb-3">
                                            <Form.Label className="small text-warning fw-bold">Maximum próbálkozás (Újraírás)</Form.Label>
                                            <Form.Control 
                                              type="number" 
                                                min="1"
                                                placeholder="Üresen hagyva korlátlan" 
                                                className="bg-dark text-light border-warning" 
                                                value={assignmentForm.maxAttempts || ''} 
                                                onChange={e => {
                                                    const val = e.target.value.replace(/\D/g, '');
                                                    setAssignmentForm({...assignmentForm, maxAttempts: val});
                                                }}
                                            />
                                        </Form.Group>
                                    )}
                                    
                                    {assignmentMode === 'TEST' && (
                                        <div className="small border-top border-secondary pt-2">
                                            <Form.Check type="switch" label="Azonnali visszajelzés" className="mb-1" 
                                                checked={assignmentForm.hasFeedback} 
                                                onChange={e => setAssignmentForm({...assignmentForm, hasFeedback: e.target.checked})} />
                                                
                                            <Form.Check type="switch" label="Kevert sorrend" className="mb-1" 
                                                checked={assignmentForm.isRandomized} 
                                                onChange={e => setAssignmentForm({...assignmentForm, isRandomized: e.target.checked})} />
                                                
                                            <Form.Check type="switch" label="Második esély a hibás válaszoknál" 
                                                checked={assignmentForm.allowRetries} 
                                                onChange={e => setAssignmentForm({...assignmentForm, allowRetries: e.target.checked})} />
                                        </div>
                                    )}
                                </div>
                            </Col>

                            {/* KÖZÉP: VÁLOGATÁS */}
                            <Col lg={4} className="border-start border-secondary">
                                <h6 className="fw-bold text-info">Válogatás Forrásból</h6>
                                <Form.Select className="bg-dark text-light border-secondary mb-3" value={selectedLessonId} onChange={handleLessonChange}>
                                    <option value="">-- Válassz leckét --</option>
                                    {lessons.map(l => <option key={l.lessonId} value={l.lessonId}>[{l.difficulty}] {l.title}</option>)}
                                </Form.Select>
                                {availableExercises.length > 0 && (
                                    <>
                                        <Button variant="outline-info" size="sm" className="w-100 mb-2" onClick={handleSelectAllExercises}>Összes kijelölése (ebből a leckéből)</Button>
                                        <div style={{ maxHeight: '350px', overflowY: 'auto' }} className="pe-2 custom-scrollbar">
                                            {availableExercises.map(ex => (
                                                <div key={ex.exerciseId} className="border-bottom border-secondary py-2">
                                                    <div className="d-flex justify-content-between align-items-center">
                                                        <Form.Check id={`ex-${ex.exerciseId}`} label={<span className="small">{ex.content?.question || ex.type}</span>} checked={assignmentForm.exerciseIds.includes(ex.exerciseId)} onChange={() => toggleExerciseSelection(ex)} />
                                                        <Button variant="link" size="sm" className="text-info p-0" onClick={() => togglePreview(ex.exerciseId)}>👁️</Button>
                                                    </div>
                                                    {previewExerciseId === ex.exerciseId && (
                                                        <div className="mt-1 p-2 bg-dark rounded border border-info small">
                                                            <strong>Típus:</strong> {ex.type}<br/>
                                                            {ex.content?.options && <div><strong>Opciók:</strong> {ex.content.options.join(", ")}</div>}
                                                        </div>
                                                    )}
                                                </div>
                                            ))}
                                        </div>
                                    </>
                                )}
                            </Col>

                            {/* JOBB: KOSÁR (SUMMARY) */}
                            <Col lg={4} className="border-start border-secondary">
                                <h6 className="fw-bold text-success mb-3">Kiválasztott tartalom ({selectedExercisesData.length} db)</h6>
                                <div style={{ maxHeight: '450px', overflowY: 'auto' }} className="pe-2 custom-scrollbar">
                                    {selectedExercisesData.length === 0 ? <p className="text-light small">Még nem választottál feladatot.</p> : (
                                        <ListGroup variant="flush">
                                            {selectedExercisesData.map((ex, index) => (
                                                <ListGroup.Item key={ex.id} className="bg-dark text-light border-secondary d-flex justify-content-between align-items-center p-2">
                                                    <div className="small overflow-hidden text-truncate" style={{ maxWidth: '80%' }}>{index + 1}. {ex.title}</div>
                                                    <Button variant="outline-danger" size="sm" className="border-0" onClick={() => removeFromSummary(ex.id)}>✕</Button>
                                                </ListGroup.Item>
                                            ))}
                                        </ListGroup>
                                    )}
                                </div>
                            </Col>
                        </Row>
                        
                        <Row className="mt-4 border-top border-secondary pt-3 small">
                            <Col md={6}><Form.Group><Form.Label className="text-secondary fw-bold">Elérhető:</Form.Label><Form.Control type="datetime-local" className="bg-dark text-light border-secondary" value={assignmentForm.availableFrom} onChange={e => setAssignmentForm({...assignmentForm, availableFrom: e.target.value})} /></Form.Group></Col>
                            <Col md={6}><Form.Group><Form.Label className="text-secondary fw-bold">Határidő:</Form.Label><Form.Control type="datetime-local" className="bg-dark text-light border-secondary" value={assignmentForm.availableUntil} onChange={e => setAssignmentForm({...assignmentForm, availableUntil: e.target.value})} /></Form.Group></Col>
                        </Row>
                    </Modal.Body>
                    <Modal.Footer className="border-secondary">
                        <Button variant="outline-light" onClick={() => setIsAssignmentModalOpen(false)}>Mégse</Button>
                        <Button variant="primary" 
                            type="submit" 
                            className="fw-bold px-4" 
                            disabled={assignmentForm.exerciseIds.length === 0 || isSubmitting}
                            >
                            {isSubmitting ? (
                                <><Spinner size="sm" animation="border" className="me-2" /> Mentés...</>
                            ) : (
                                assignmentMode === 'TEST' ? 'Dolgozat Kiosztása' : 'Tananyag Publikálása'
                            )}
                        </Button>
                    </Modal.Footer>
                </Form>
            </Modal>
        </Container>
    );
};

export default TeacherClassroomDetail;