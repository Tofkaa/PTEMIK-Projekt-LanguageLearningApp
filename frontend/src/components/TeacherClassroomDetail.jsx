import React, { useState, useEffect, useCallback } from 'react';
import { useParams, useNavigate, useLocation } from 'react-router-dom';
import { Container, Tab, Tabs, Card, Button, Badge, Row, Col, ListGroup, Modal, Form } from 'react-bootstrap';
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
        timeLimitMinutes: '',
        availableFrom: '',
        availableUntil: '',
        exerciseIds: []
    });

    const [previewExerciseId, setPreviewExerciseId] = useState(null);
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
        const payload = {
        title: assignmentForm.title,
        description: assignmentForm.description,
        isTest: assignmentMode === 'TEST', 
        
        
        hasFeedback: assignmentMode === 'TEST' ? assignmentForm.hasFeedback : true,
        randomized: assignmentForm.isRandomized, 
        allowRetries: assignmentMode === 'TEST' ? assignmentForm.allowRetries : true,
        
        timeLimitMinutes: assignmentForm.timeLimitMinutes ? parseInt(assignmentForm.timeLimitMinutes) : null,
        availableFrom: assignmentForm.availableFrom ? new Date(assignmentForm.availableFrom).toISOString() : null,
        availableUntil: assignmentForm.availableUntil ? new Date(assignmentForm.availableUntil).toISOString() : null,
        exerciseIds: assignmentForm.exerciseIds
    };
        try {
            await assignmentApi.createAssignment(classroomId, payload);
            setIsAssignmentModalOpen(false);
            setAssignmentForm({
                title: '', description: '', isTest: false, isRandomized: true,
                allowRetries: false, timeLimitMinutes: '', availableFrom: '', availableUntil: '', exerciseIds: []
            });
            setSelectedExercisesData([]);
            setAssignmentMode('TEST');
            setSelectedLessonId('');
            setAvailableExercises([]);
            fetchDashboardData(); 
        } catch (error) {
            console.error("Hiba a feladat kiosztásakor:", error);
            alert(error.response?.data?.message || "Hiba történt a mentés során.");
        }
    };

    if (isLoading) {
        return <Container className="d-flex justify-content-center align-items-center" style={{ height: '50vh' }}><div className="spinner-border text-primary"></div></Container>;
    }

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
                    <Row className="g-4">
                        {assignments.length === 0 ? (
                            <Col><div className="p-5 text-center border border-secondary rounded bg-dark text-light">Nincs még kiírt feladat.</div></Col>
                        ) : (
                            assignments.map(a => (
                                <Col md={6} lg={4} key={a.assignmentId}>
                                    <Card className="h-100 bg-dark text-light border-secondary shadow-sm">
                                        <Card.Body>
                                            <div className="d-flex justify-content-between align-items-start mb-2">
                                                <Card.Title className="fw-bold text-primary m-0">{a.title}</Card.Title>
                                                {a.test ? <Badge bg="danger">Tesztmód</Badge> : <Badge bg="success">Gyakorló</Badge>}
                                            </div>
                                            <Card.Text className="text-secondary mb-3" style={{ fontSize: '0.9rem' }}>{a.description}</Card.Text>
                                            <ListGroup variant="flush" className="bg-transparent border-top border-secondary pt-2">
                                                <ListGroup.Item className="bg-transparent text-light border-0 p-1" style={{ fontSize: '0.85rem' }}><strong>Feladatok:</strong> {a.exerciseCount} db</ListGroup.Item>
                                                <ListGroup.Item className="bg-transparent text-light border-0 p-1" style={{ fontSize: '0.85rem' }}><strong>Idő:</strong> {a.timeLimitMinutes ? `${a.timeLimitMinutes} perc` : 'Nincs'}</ListGroup.Item>
                                            </ListGroup>
                                        </Card.Body>
                                    </Card>
                                </Col>
                            ))
                        )}
                    </Row>
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
                                    <Form.Group className="mb-3"><Form.Label className="small text-secondary fw-bold">Idő (perc)</Form.Label><Form.Control type="number" className="bg-dark text-light border-secondary" value={assignmentForm.timeLimitMinutes} onChange={e => setAssignmentForm({...assignmentForm, timeLimitMinutes: e.target.value})} /></Form.Group>
                                    
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
                        <Button variant="primary" type="submit" className="fw-bold px-4" disabled={assignmentForm.exerciseIds.length === 0}>
                            {assignmentMode === 'TEST' ? 'Dolgozat Kiosztása' : 'Tananyag Publikálása'}
                        </Button>
                    </Modal.Footer>
                </Form>
            </Modal>
        </Container>
    );
};

export default TeacherClassroomDetail;