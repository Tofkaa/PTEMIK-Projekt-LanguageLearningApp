import React, { useState, useEffect, useCallback } from 'react';
import { useParams, useNavigate, useLocation } from 'react-router-dom';
import { Container, Tab, Tabs, Card, Button, Badge, Row, Col, ListGroup, Modal, Form } from 'react-bootstrap';
import { classroomApi } from '../services/classroomApi';
import { assignmentApi } from '../services/assignmentApi';
import { lessonApi } from '../services/lessonApi';

/**
 * Teacher Dashboard for managing a specific classroom.
 * Includes student moderation, assignment creation, and overview.
 */
const TeacherClassroomDetail = () => {
    const { id: classroomId } = useParams();
    const navigate = useNavigate();
    const location = useLocation();
    
    const classroomName = location.state?.className || 'Osztályterem Kezelése';

    // --- STATE: Members ---
    const [pendingMembers, setPendingMembers] = useState([]);
    const [acceptedMembers, setAcceptedMembers] = useState([]);
    
    // --- STATE: Assignments ---
    const [assignments, setAssignments] = useState([]);
    const [isAssignmentModalOpen, setIsAssignmentModalOpen] = useState(false);
    
    // --- STATE: Form Data & Selectors ---
    const [lessons, setLessons] = useState([]);
    const [selectedLessonId, setSelectedLessonId] = useState('');
    const [availableExercises, setAvailableExercises] = useState([]);
    
    const [assignmentForm, setAssignmentForm] = useState({
        title: '',
        description: '',
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

/**
     * Fetches all required data for the dashboard in parallel.
     * Wrapped in useCallback to satisfy exhaustive-deps linting rules.
     */
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
        try {
            await classroomApi.moderateMember(classroomId, studentId, approve);
            fetchDashboardData();
        } catch (error) {
            console.error("Hiba a moderálás során:", error);
        }
    };

    const handleKick = async (studentId) => {
        if (window.confirm("Biztosan el akarod távolítani ezt a diákot az osztályból?")) {
            try {
                await classroomApi.kickMember(classroomId, studentId);
                fetchDashboardData();
            } catch (error) {
                console.error("Hiba a diák eltávolításakor:", error);
            }
        }
    };

    // --- ASSIGNMENT CREATION LOGIC ---
    
    /**
     * Opens the modal and fetches the list of available lessons for the dropdown.
     */
    const openAssignmentModal = async () => {
        try {
            const res = await lessonApi.getAllLessons();
            setLessons(res.data);
            setIsAssignmentModalOpen(true);
        } catch (error) {
            console.error("Hiba a leckék betöltésekor:", error);
        }
    };

    /**
     * Triggers when the teacher selects a lesson from the dropdown.
     * Fetches the specific exercises belonging to that lesson.
     */
    const handleLessonChange = async (e) => {
        const lessonId = e.target.value;
        setSelectedLessonId(lessonId);
        
        if (lessonId) {
            try {
                const res = await lessonApi.getLessonExercises(lessonId);
                setAvailableExercises(res.data);
            } catch (error) {
                console.error("Hiba a feladatok betöltésekor:", error);
            }
        } else {
            setAvailableExercises([]);
        }
    };

    /**
     * Toggles the selection of a specific exercise for the assignment.
     */
    const toggleExerciseSelection = (exerciseId) => {
        setAssignmentForm(prev => {
            const isSelected = prev.exerciseIds.includes(exerciseId);
            if (isSelected) {
                return { ...prev, exerciseIds: prev.exerciseIds.filter(id => id !== exerciseId) };
            } else {
                return { ...prev, exerciseIds: [...prev.exerciseIds, exerciseId] };
            }
        });
    };

    /**
     * Toggles the visibility of the exercise preview box.
     * @param {string} exerciseId - The ID of the exercise to preview.
     */
    const togglePreview = (exerciseId) => {
        setPreviewExerciseId(prev => prev === exerciseId ? null : exerciseId);
    };

    const handleAssignmentSubmit = async (e) => {
        e.preventDefault();
        
        // Formázás a backend számára
        const payload = {
            ...assignmentForm,
            timeLimitMinutes: assignmentForm.timeLimitMinutes ? parseInt(assignmentForm.timeLimitMinutes) : null,
            availableFrom: assignmentForm.availableFrom ? new Date(assignmentForm.availableFrom).toISOString() : null,
            availableUntil: assignmentForm.availableUntil ? new Date(assignmentForm.availableUntil).toISOString() : null
        };

        try {
            await assignmentApi.createAssignment(classroomId, payload);
            setIsAssignmentModalOpen(false);
            setAssignmentForm({
                title: '', description: '', isTest: false, isRandomized: true,
                allowRetries: false, timeLimitMinutes: '', availableFrom: '', availableUntil: '', exerciseIds: []
            });
            setSelectedLessonId('');
            setAvailableExercises([]);
            fetchDashboardData(); 
        } catch (error) {
            console.error("Hiba a feladat kiosztásakor:", error);
            alert(error.response?.data?.message || "Hiba történt a mentés során.");
        }
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
            <div className="d-flex justify-content-between align-items-center mb-4">
                <div>
                    <Button variant="outline-secondary" size="sm" onClick={() => navigate('/classrooms')} className="mb-2">
                        ← Vissza az osztályokhoz
                    </Button>
                    <h2 className="fw-bold m-0">{classroomName}</h2>
                </div>
            </div>

            <Tabs defaultActiveKey="assignments" className="mb-4 custom-dark-tabs">
                
                {/* --- ASSIGNMENTS TAB --- */}
                <Tab eventKey="assignments" title={<span className="fw-bold">📝 Feladatok & Tesztek</span>}>
                    <div className="mb-4 mt-3">
                        <Button variant="primary" className="fw-bold px-4" onClick={openAssignmentModal}>
                            + Új Feladat / Teszt Kiírása
                        </Button>
                    </div>

                    <Row className="g-4">
                        {assignments.length === 0 ? (
                            <Col>
                                <div className="p-5 text-center border border-secondary rounded bg-dark text-light">
                                    Nincs még kiírt feladat ebben az osztályban.
                                </div>
                            </Col>
                        ) : (
                            assignments.map(a => (
                                <Col md={6} lg={4} key={a.assignmentId}>
                                    <Card className="h-100 bg-dark text-light border-secondary shadow-sm">
                                        <Card.Body>
                                            <div className="d-flex justify-content-between align-items-start mb-2">
                                                <Card.Title className="fw-bold text-primary m-0">{a.title}</Card.Title>
                                                {a.test ? <Badge bg="danger">Tesztmód</Badge> : <Badge bg="success">Gyakorló</Badge>}
                                            </div>
                                            <Card.Text className="text-secondary mb-3" style={{ fontSize: '0.9rem' }}>
                                                {a.description}
                                            </Card.Text>
                                            <ListGroup variant="flush" className="bg-transparent border-top border-secondary pt-2">
                                                <ListGroup.Item className="bg-transparent text-light border-0 p-1" style={{ fontSize: '0.85rem' }}>
                                                    <strong>Feladatok:</strong> {a.exerciseCount} db
                                                </ListGroup.Item>
                                                <ListGroup.Item className="bg-transparent text-light border-0 p-1" style={{ fontSize: '0.85rem' }}>
                                                    <strong>Időkorlát:</strong> {a.timeLimitMinutes ? `${a.timeLimitMinutes} perc` : 'Nincs'}
                                                </ListGroup.Item>
                                                <ListGroup.Item className="bg-transparent text-light border-0 p-1" style={{ fontSize: '0.85rem' }}>
                                                    <strong>Javítás:</strong> {a.allowRetries ? 'Engedélyezve' : 'Tiltva'}
                                                </ListGroup.Item>
                                            </ListGroup>
                                        </Card.Body>
                                    </Card>
                                </Col>
                            ))
                        )}
                    </Row>
                </Tab>

                {/* --- MEMBERS TAB --- */}
                <Tab eventKey="members" title={<span className="fw-bold">👥 Tagság kezelése</span>}>
                    <Row className="g-4 mt-1">
                        <Col md={6}>
                            <Card className="bg-dark text-light border-secondary shadow-sm h-100">
                                <Card.Header className="border-secondary bg-dark d-flex justify-content-between align-items-center">
                                    <h5 className="m-0 text-warning">Várakozó Kérelmek</h5>
                                    <Badge bg="warning" text="dark">{pendingMembers.length}</Badge>
                                </Card.Header>
                                <ListGroup variant="flush">
                                    {pendingMembers.length === 0 ? (
                                        <ListGroup.Item className="bg-dark text-light border-secondary text-center py-4">
                                            Nincs új csatlakozási kérelem.
                                        </ListGroup.Item>
                                    ) : (
                                        pendingMembers.map(member => (
                                            <ListGroup.Item key={member.memberId} className="bg-dark text-light border-secondary d-flex justify-content-between align-items-center">
                                                <div>
                                                    <div className="fw-bold">{member.studentName || member.name || member.username || 'Ismeretlen felhasználó'}</div>
                                                    <div className="text-secondary" style={{ fontSize: '0.8rem' }}>{member.studentEmail || member.email || 'Nincs email'}</div>
                                                </div>
                                                <div className="d-flex gap-2">
                                                    <Button variant="success" size="sm" onClick={() => handleModerate(member.userId, true)}>✓ Elfogad</Button>
                                                    <Button variant="outline-danger" size="sm" onClick={() => handleModerate(member.userId, false)}>✕ Elutasít</Button>
                                                </div>
                                            </ListGroup.Item>
                                        ))
                                    )}
                                </ListGroup>
                            </Card>
                        </Col>

                        <Col md={6}>
                            <Card className="bg-dark text-light border-secondary shadow-sm h-100">
                                <Card.Header className="border-secondary bg-dark d-flex justify-content-between align-items-center">
                                    <h5 className="m-0 text-info">Aktív Diákok</h5>
                                    <Badge bg="info" text="dark">{acceptedMembers.length}</Badge>
                                </Card.Header>
                                <ListGroup variant="flush">
                                    {acceptedMembers.length === 0 ? (
                                        <ListGroup.Item className="bg-dark text-light border-secondary text-center py-4">
                                            Az osztály még üres.
                                        </ListGroup.Item>
                                    ) : (
                                        acceptedMembers.map(member => (
                                            <ListGroup.Item key={member.memberId} className="bg-dark text-light border-secondary d-flex justify-content-between align-items-center">
                                                <div>
                                                    <div className="fw-bold">{member.studentName || member.name || member.username || 'Ismeretlen felhasználó'} <span className="text-light">#{member.userTag}</span></div>
                                                    <div className="text-secondary" style={{ fontSize: '0.8rem' }}>Csatlakozott: {new Date(member.joinedAt).toLocaleDateString()}</div>
                                                </div>
                                                <Button variant="outline-danger" size="sm" onClick={() => handleKick(member.userId)}>Kirúgás</Button>
                                            </ListGroup.Item>
                                        ))
                                    )}
                                </ListGroup>
                            </Card>
                        </Col>
                    </Row>
                </Tab>

                <Tab eventKey="stats" title={<span className="fw-bold">📊 Statisztikák</span>}>
                     <Card className="bg-dark text-light border-secondary shadow-sm p-5 text-center mt-3">
                        <h4 className="text-light">A Statisztika modul hamarosan érkezik...</h4>
                    </Card>
                </Tab>
            </Tabs>

            {/* --- ASSIGNMENT CREATION MODAL --- */}
            <Modal show={isAssignmentModalOpen} onHide={() => setIsAssignmentModalOpen(false)} size="lg" centered contentClassName="bg-dark text-light border-secondary">
                <Modal.Header closeButton className="border-secondary" closeVariant="white">
                    <Modal.Title className="fw-bold text-primary">Új Feladat Kiosztása</Modal.Title>
                </Modal.Header>
                <Form onSubmit={handleAssignmentSubmit}>
                    <Modal.Body>
                        <Row>
                            {/* Bal oszlop: Alap adatok */}
                            <Col md={6}>
                                <Form.Group className="mb-3">
                                    <Form.Label className="text-secondary fw-bold">Cím</Form.Label>
                                    <Form.Control type="text" required className="bg-dark text-light border-secondary"
                                        value={assignmentForm.title} onChange={e => setAssignmentForm({...assignmentForm, title: e.target.value})} />
                                </Form.Group>
                                <Form.Group className="mb-3">
                                    <Form.Label className="text-secondary fw-bold">Leírás / Instrukciók</Form.Label>
                                    <Form.Control as="textarea" rows={3} className="bg-dark text-light border-secondary"
                                        value={assignmentForm.description} onChange={e => setAssignmentForm({...assignmentForm, description: e.target.value})} />
                                </Form.Group>
                                
                                <Form.Group className="mb-3">
                                    <Form.Label className="text-secondary fw-bold">Időkorlát (perc)</Form.Label>
                                    <Form.Control type="number" min="1" placeholder="Hagyd üresen, ha nincs" className="bg-dark text-light border-secondary"
                                        value={assignmentForm.timeLimitMinutes} onChange={e => setAssignmentForm({...assignmentForm, timeLimitMinutes: e.target.value})} />
                                </Form.Group>

                                <Form.Check type="switch" label="Szigorú teszt (Nincs azonnali visszajelzés)" className="mb-2 text-warning fw-bold"
                                    checked={assignmentForm.isTest} onChange={e => setAssignmentForm({...assignmentForm, isTest: e.target.checked})} />
                                <Form.Check type="switch" label="Feladatok sorrendjének keverése" className="mb-2 text-light"
                                    checked={assignmentForm.isRandomized} onChange={e => setAssignmentForm({...assignmentForm, isRandomized: e.target.checked})} />
                                <Form.Check type="switch" label="Hibás válaszok javításának engedélyezése" className="mb-4 text-light"
                                    checked={assignmentForm.allowRetries} onChange={e => setAssignmentForm({...assignmentForm, allowRetries: e.target.checked})} />
                            </Col>

                            {/* Jobb oszlop: Feladatok kiválasztása */}
                            <Col md={6}>
                                <div className="p-3 border border-secondary rounded bg-darker h-100">
                                    <h5 className="fw-bold text-info mb-3">Feladatok összeállítása</h5>
                                    
                                    <Form.Group className="mb-3">
                                        <Form.Label className="text-secondary">Válassz leckét forrásként:</Form.Label>
                                        <Form.Select className="bg-dark text-light border-secondary" value={selectedLessonId} onChange={handleLessonChange}>
                                            <option value="">-- Lecke kiválasztása --</option>
                                            {lessons.map(l => <option key={l.lessonId} value={l.lessonId}>{l.title}</option>)}
                                        </Form.Select>
                                    </Form.Group>

                                    {availableExercises.length > 0 && (
                                        <div className="mt-3">
                                            <p className="text-secondary mb-2" style={{ fontSize: '0.85rem' }}>Pipáld be, melyeket kéred a dolgozatba:</p>
                                            <div style={{ maxHeight: '300px', overflowY: 'auto' }} className="pe-2 custom-scrollbar">
                                                {availableExercises.map(ex => (
                                                    <div key={ex.exerciseId} className="mb-2 border-bottom border-secondary pb-2">
                                                        <div className="d-flex justify-content-between align-items-center">
                                                            <Form.Check 
                                                                id={`ex-${ex.exerciseId}`}
                                                                label={<span style={{ fontSize: '0.9rem' }}>{ex.content?.question || ex.type}</span>}
                                                                checked={assignmentForm.exerciseIds.includes(ex.exerciseId)}
                                                                onChange={() => toggleExerciseSelection(ex.exerciseId)}
                                                                className="mb-0"
                                                            />
                                                            <Button 
                                                                variant="link" 
                                                                size="sm" 
                                                                className="text-info text-decoration-none p-0 ms-2"
                                                                onClick={() => togglePreview(ex.exerciseId)}
                                                            >
                                                                {previewExerciseId === ex.exerciseId ? '▲ Bezár' : '👁️ Előnézet'}
                                                            </Button>
                                                        </div>
                                                        
                                                        {/* PREVIEW BOX */}
                                                        {previewExerciseId === ex.exerciseId && (
                                                            <div className="mt-2 p-2 bg-dark rounded border border-info" style={{ fontSize: '0.85rem' }}>
                                                                <div className="text-secondary mb-1"><strong>Típus:</strong> <Badge bg="secondary">{ex.type}</Badge></div>
                                                                
                                                                {ex.imageUrl && (
                                                                    <div className="mb-1">
                                                                        <a href={ex.imageUrl} target="_blank" rel="noreferrer" className="text-info">
                                                                            🖼️ Kép megtekintése
                                                                        </a>
                                                                    </div>
                                                                )}
                                                                
                                                                {ex.content?.options && (
                                                                    <div className="mb-1">
                                                                        <strong>Opciók:</strong>
                                                                        <ul className="mb-0 ps-3 text-light">
                                                                            {ex.content.options.map((opt, i) => <li key={i}>{opt}</li>)}
                                                                        </ul>
                                                                    </div>
                                                                )}
                                                                
                                                                {ex.content?.hint && (
                                                                    <div className="text-warning"><strong>Tipp:</strong> {ex.content.hint}</div>
                                                                )}
                                                            </div>
                                                        )}
                                                    </div>
                                                ))}
                                            </div>
                                            <div className="mt-3 pt-2 border-top border-secondary fw-bold text-success text-end">
                                                Kiválasztva: {assignmentForm.exerciseIds.length} db
                                            </div>
                                        </div>
                                    )}
                                    {selectedLessonId && availableExercises.length === 0 && (
                                        <p className="text-light mt-3">Ebben a leckében nincsenek feladatok.</p>
                                    )}
                                </div>
                            </Col>
                        </Row>
                        
                        <Row className="mt-4 border-top border-secondary pt-3">
                            <Col md={6}>
                                <Form.Group>
                                    <Form.Label className="text-secondary fw-bold">Elérhető ettől:</Form.Label>
                                    <Form.Control type="datetime-local" className="bg-dark text-light border-secondary"
                                        value={assignmentForm.availableFrom} onChange={e => setAssignmentForm({...assignmentForm, availableFrom: e.target.value})} />
                                </Form.Group>
                            </Col>
                            <Col md={6}>
                                <Form.Group>
                                    <Form.Label className="text-secondary fw-bold">Határidő:</Form.Label>
                                    <Form.Control type="datetime-local" className="bg-dark text-light border-secondary"
                                        value={assignmentForm.availableUntil} onChange={e => setAssignmentForm({...assignmentForm, availableUntil: e.target.value})} />
                                </Form.Group>
                            </Col>
                        </Row>
                    </Modal.Body>
                    <Modal.Footer className="border-secondary">
                        <Button variant="outline-light" onClick={() => setIsAssignmentModalOpen(false)}>Mégse</Button>
                        <Button variant="primary" type="submit" className="fw-bold" disabled={assignmentForm.exerciseIds.length === 0}>
                            Dolgozat Kiosztása
                        </Button>
                    </Modal.Footer>
                </Form>
            </Modal>
        </Container>
    );
};

export default TeacherClassroomDetail;