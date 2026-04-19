import React, { useState, useEffect, useCallback } from 'react';
import { useParams, useNavigate, useLocation } from 'react-router-dom';
import { Container, Tab, Tabs, Card, Button, Badge, Row, Col, ListGroup, Modal, Form, Spinner } from 'react-bootstrap';
import { classroomApi } from '../services/classroomApi';
import { assignmentApi } from '../services/assignmentApi';
import { lessonApi } from '../services/lessonApi';

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
        } catch (error) {
            console.error("Hiba az adatok lekérésekor:", error);
        } finally {
            setIsLoading(false);
        }
    }, [classroomId]);

    useEffect(() => {
        fetchDashboardData();
    }, [fetchDashboardData]); 

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
    const activeAssignments = assignments.filter(a => !a.availableUntil || new Date(a.availableUntil) > now);
    const expiredAssignments = assignments.filter(a => a.availableUntil && new Date(a.availableUntil) <= now);

    return (
        <Container className="py-4 text-light">
            <div className="d-flex justify-content-between align-items-center mb-4">
                <div>
                    <Button variant="outline-secondary" size="sm" onClick={() => navigate('/classrooms')} className="mb-2">← Vissza</Button>
                    <h2 className="fw-bold m-0">{classroomName}</h2>
                </div>
            </div>

            <Tabs defaultActiveKey="assignments" className="mb-4 custom-dark-tabs">
                <Tab eventKey="assignments" title={<span className="fw-bold">📝 Feladatok & Tesztek</span>}>
                    <div className="mb-4 mt-3">
                        <Button variant="primary" className="fw-bold px-4" onClick={openAssignmentModal}>+ Új Feladat / Teszt Kiírása</Button>
                    </div>
                    
                    {/* BELSŐ FÜLEK AZ AKTÍV ÉS LEJÁRT FELADATOKNAK */}
                    <Tabs defaultActiveKey="active" className="mb-3 border-secondary custom-dark-tabs">
                        
                        {/* 1. AKTÍV FELADATOK */}
                        <Tab eventKey="active" title={<span className="fw-bold text-info">🔥 Aktív Feladatok ({activeAssignments.length})</span>}>
                            <Row className="g-4 mt-1">
                                {activeAssignments.length === 0 ? (
                                    <Col><div className="p-5 text-center border border-secondary rounded bg-dark text-light">Nincs aktív feladat.</div></Col>
                                ) : (
                                    activeAssignments.map(a => (
                                        <Col md={6} lg={4} key={a.assignmentId}>
                                            <Card className="h-100 bg-dark text-light border-info shadow-sm hover-border-primary transition-all">
                                                <Card.Body className="d-flex flex-column">
                                                    <div className="d-flex justify-content-between align-items-start mb-2">
                                                        <Card.Title className="fw-bold text-info m-0">{a.title}</Card.Title>
                                                        <div>
                                                            {a.test ? <Badge bg="danger" className="me-2">Tesztmód</Badge> : <Badge bg="success" className="me-2">Gyakorló</Badge>}
                                                            <Button variant="outline-danger" size="sm" className="border-0 py-0 fs-5" onClick={() => handleDeleteAssignment(a.assignmentId)} title="Feladat törlése">🗑️</Button>
                                                        </div>
                                                    </div>
                                                    <Card.Text className="text-secondary mb-3 flex-grow-1" style={{ fontSize: '0.9rem' }}>{a.description}</Card.Text>
                                                    
                                                    <ListGroup variant="flush" className="bg-transparent border-top border-secondary pt-2 mb-3">
                                                        <ListGroup.Item className="bg-transparent text-light border-0 p-1" style={{ fontSize: '0.85rem' }}><strong>Feladatok:</strong> {a.exerciseCount} db</ListGroup.Item>
                                                        <ListGroup.Item className="bg-transparent text-light border-0 p-1" style={{ fontSize: '0.85rem' }}><strong>Idő:</strong> {a.timeLimitMinutes ? `${a.timeLimitMinutes} perc` : 'Nincs'}</ListGroup.Item>
                                                    </ListGroup>
                                    
                                                    <div className="mt-auto pt-2 border-top border-secondary">
                                                        <Button variant="outline-info" className="w-100 fw-bold shadow-sm mt-2" onClick={() => navigate(`/assignment/${a.assignmentId}/submissions`, { state: { assignmentTitle: a.title, classroomName: classroomName } })}>
                                                            👨‍🏫 Beadott munkák értékelése
                                                        </Button>
                                                    </div>
                                                </Card.Body>
                                            </Card>
                                        </Col>
                                    ))
                                )}
                            </Row>
                        </Tab>

                        {/* 2. LEJÁRT FELADATOK */}
                        <Tab eventKey="expired" title={<span className="fw-bold text-secondary">⏳ Lejárt / Archív ({expiredAssignments.length})</span>}>
                            <Row className="g-4 mt-1">
                                {expiredAssignments.length === 0 ? (
                                    <Col><div className="p-5 text-center border border-secondary rounded bg-dark text-muted">Nincs lejárt feladat.</div></Col>
                                ) : (
                                    expiredAssignments.map(a => (
                                        <Col md={6} lg={4} key={a.assignmentId}>
                                            <Card className="h-100 bg-dark text-secondary border-secondary shadow-sm" style={{ opacity: 0.85 }}>
                                                <Card.Body className="d-flex flex-column">
                                                    <div className="d-flex justify-content-between align-items-start mb-2">
                                                        <Card.Title className="fw-bold m-0">{a.title}</Card.Title>
                                                        <div>
                                                            <Badge bg="secondary" className="me-2">Lejárt</Badge>
                                                            <Button variant="outline-secondary" size="sm" className="border-0 py-0 fs-5" onClick={() => handleDeleteAssignment(a.assignmentId)}>🗑️</Button>
                                                        </div>
                                                    </div>
                                                    <Card.Text className="mb-3 flex-grow-1 small">{a.description}</Card.Text>
                                    
                                                    <div className="mt-auto pt-2 border-top border-secondary">
                                                        <Button variant="outline-secondary" className="w-100 fw-bold mt-2" onClick={() => navigate(`/assignment/${a.assignmentId}/submissions`, { state: { assignmentTitle: a.title, classroomName: classroomName } })}>
                                                            📁 Eredmények megtekintése
                                                        </Button>
                                                    </div>
                                                </Card.Body>
                                            </Card>
                                        </Col>
                                    ))
                                )}
                            </Row>
                        </Tab>
                    </Tabs>
                </Tab>

                <Tab eventKey="members" title={<span className="fw-bold">👥 Tagság kezelése</span>}>
                    <Row className="g-4 mt-1">
                        <Col md={6}>
                            <Card className="bg-dark border-secondary h-100">
                                <Card.Header className="bg-dark border-secondary d-flex justify-content-between">
                                    <h5 className="m-0 text-warning">Várakozó</h5><Badge bg="warning" text="dark">{pendingMembers.length}</Badge>
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
                                            <div><div className="fw-bold">{member.studentName || member.name} <span className="text-muted small">#{member.userTag}</span></div><div className="text-secondary small">Belépett: {new Date(member.joinedAt).toLocaleDateString()}</div></div>
                                            <Button variant="outline-danger" size="sm" onClick={() => handleKick(member.userId)}>Kirúgás</Button>
                                        </ListGroup.Item>
                                    ))}
                                </ListGroup>
                            </Card>
                        </Col>
                    </Row>
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
                                    {selectedExercisesData.length === 0 ? <p className="text-muted small">Még nem választottál feladatot.</p> : (
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