import React, { useState, useEffect, useCallback } from 'react';
import { useParams, useNavigate, useLocation } from 'react-router-dom';
import { Container, Card, Button, Badge, Table, Modal, Form, Spinner, Row, Col } from 'react-bootstrap';
import { assignmentApi } from '../services/assignmentApi';


const AssignmentSubmissions = () => {
    const { id: assignmentId } = useParams();
    const navigate = useNavigate();
    const location = useLocation();
    
    const assignmentTitle = location.state?.assignmentTitle || 'Feladat';
    const classroomName = location.state?.classroomName || 'Osztályterem';

    const [sessions, setSessions] = useState([]);
    const [isLoading, setIsLoading] = useState(true);
    
    // Modal state-ek az értékeléshez
    const [selectedSession, setSelectedSession] = useState(null);
    const [isGradingModalOpen, setIsGradingModalOpen] = useState(false);
    const [gradeForm, setGradeForm] = useState({ teacherScore: '', teacherComment: '' });
    const [isSubmitting, setIsSubmitting] = useState(false);
   const [expandedPreviewIndex, setExpandedPreviewIndex] = useState(null);

    const togglePreview = (index) => {
        setExpandedPreviewIndex(prev => prev === index ? null : index);
    };

    const fetchSessions = useCallback(async () => {
        setIsLoading(true);
        try {
            const res = await assignmentApi.getAssignmentSessions(assignmentId);
            setSessions(res.data);
        } catch (error) {
            console.error("Hiba a beadott munkák lekérésekor:", error);
            alert("Nem sikerült lekérni a listát. Lehet, hogy nincs jogosultságod.");
        } finally {
            setIsLoading(false);
        }
    }, [assignmentId]);

    useEffect(() => {
        let isMounted = true;
        const load = async () => {
            if (isMounted) await fetchSessions();
        };
        load();
        return () => { isMounted = false; }; 
    }, [fetchSessions]);

    // Értékelő ablak megnyitása
    const openGradingModal = (session) => {
        setSelectedSession(session);
        setGradeForm({
            // Ha a tanár már adott pontot, azt mutatjuk, amúgy a gépét
            teacherScore: session.teacherScore !== null ? session.teacherScore : session.finalScore,
            teacherComment: session.teacherComment || ''
        });
        setIsGradingModalOpen(true);
    };

    // Értékelés beküldése
    const handleGradeSubmit = async (e) => {
        e.preventDefault();
        if (isSubmitting) return;
        setIsSubmitting(true);

        try {
            await assignmentApi.gradeSession(selectedSession.sessionId, {
                teacherScore: parseInt(gradeForm.teacherScore),
                teacherComment: gradeForm.teacherComment
            });
            setIsGradingModalOpen(false);
            fetchSessions(); // Újratöltjük a listát, hogy látszódjon a "Publikálva" státusz
        } catch (error) {
            console.error("Hiba az értékelés mentésekor:", error);
            alert("Nem sikerült elmenteni az értékelést.");
        } finally {
            setIsSubmitting(false);
        }
    };

    const getHardestQuestion = () => {
        if (!sessions || sessions.length === 0) return "Nincs elég adat";
        
        const mistakeCounts = {};
        let totalMistakes = 0;

        sessions.forEach(session => {
            if (session.answers) {
                session.answers.forEach(ans => {
                    if (!ans.correct) {
                        mistakeCounts[ans.question] = (mistakeCounts[ans.question] || 0) + 1;
                        totalMistakes++;
                    }
                });
            }
        });

        if (totalMistakes === 0) return "Mindenki hibátlan!";

        // Sorrendbe rakjuk a hibákat csökkenő sorrendben
        const sortedMistakes = Object.entries(mistakeCounts).sort((a, b) => b[1] - a[1]);
        
        return {
            question: sortedMistakes[0][0],
            count: sortedMistakes[0][1]
        };
    };

    const hardest = getHardestQuestion();

    if (isLoading) {
        return <Container className="d-flex justify-content-center align-items-center" style={{ height: '50vh' }}><Spinner animation="border" variant="info" /></Container>;
    }

    return (
        <Container className="py-4 text-light animate-fade-in">
            <div className="mb-4">
                <Button variant="outline-secondary" size="sm" onClick={() => navigate(-1)} className="mb-2">
                    ← Vissza az osztályterembe
                </Button>
                <h4 className="text-secondary">{classroomName}</h4>
                <h2 className="fw-bold m-0 text-info">Beadott munkák: {assignmentTitle}</h2>
            </div>

            <Row className="mb-4 g-3">
                <Col md={4}>
                    <div className="p-3 bg-dark border border-secondary rounded text-center shadow-sm h-100 d-flex flex-column justify-content-center">
                        <div className="small text-secondary mb-1">Beadási arány</div>
                        <div className="fs-4 fw-bold text-light">{sessions.length} db</div>
                    </div>
                </Col>
                <Col md={4}>
                    <div className="p-3 bg-dark border border-info rounded text-center shadow-sm h-100 d-flex flex-column justify-content-center">
                        <div className="small text-secondary mb-1">Teszt Átlagpontszám</div>
                        <div className="fs-4 fw-bold text-info">
                            {sessions.length > 0 ? Math.round(sessions.reduce((acc, s) => acc + (s.teacherScore !== null ? s.teacherScore : s.finalScore), 0) / sessions.length) : 0}%
                        </div>
                    </div>
                </Col>
                <Col md={4}>
                    <div className="p-3 bg-dark border border-warning rounded text-center shadow-sm h-100 d-flex flex-column justify-content-center">
                        <div className="small text-secondary mb-1">Legnehezebb kérdés</div>
                        {typeof hardest === 'string' ? (
                            <div className="fw-bold text-success">{hardest}</div>
                        ) : (
                            <>
                                <div className="small fw-bold text-warning text-truncate px-2" title={hardest.question}>
                                    {hardest.question}
                                </div>
                                <div className="text-muted small mt-1">({hardest.count} rontott válasz)</div>
                            </>
                        )}
                    </div>
                </Col>
            </Row>

            <Card className="bg-dark text-light border-secondary shadow-lg">
                <Card.Body className="p-0">
                    <Table hover variant="dark" className="m-0" responsive>
                        <thead className="border-secondary">
                            <tr>
                                <th>Diák Neve</th>
                                <th>Beküldve</th>
                                <th className="text-center">Automata Pont</th>
                                <th className="text-center">Tanári Pont</th>
                                <th className="text-center">Státusz</th>
                                <th className="text-end">Művelet</th>
                            </tr>
                        </thead>
                        <tbody>
                            {sessions.length === 0 ? (
                                <tr><td colSpan="6" className="text-center py-4 text-light">Még senki sem küldte be ezt a feladatot.</td></tr>
                            ) : (
                                sessions.map(session => (
                                    <tr key={session.sessionId} className="align-middle">
                                        <td>
                                            <div className="fw-bold">{session.studentName}</div>
                                            <div className="small text-secondary">{session.studentEmail}</div>
                                        </td>
                                        <td>{new Date(session.finishedAt).toLocaleString('hu-HU')}</td>
                                        <td className="text-center"><Badge bg="secondary" className="fs-6">{session.finalScore}%</Badge></td>
                                        <td className="text-center">
                                            {session.teacherScore !== null ? (
                                                <Badge bg={session.teacherScore >= 50 ? "success" : "danger"} className="fs-6">{session.teacherScore}%</Badge>
                                            ) : (
                                                <span className="text-light">-</span>
                                            )}
                                        </td>
                                        <td className="text-center">
                                            {session.graded ? <Badge bg="info">Publikálva</Badge> : <Badge bg="warning" text="dark">Várakozik</Badge>}
                                        </td>
                                        <td className="text-end">
                                            <Button variant={session.graded ? "outline-info" : "primary"} size="sm" onClick={() => openGradingModal(session)}>
                                                {session.graded ? 'Módosítás' : 'Értékelés'}
                                            </Button>
                                        </td>
                                    </tr>
                                ))
                            )}
                        </tbody>
                    </Table>
                </Card.Body>
            </Card>

            {/* FELUGRÓ ABLAK AZ ÉRTÉKELÉSHEZ (Részletes válaszokkal) */}
            <Modal show={isGradingModalOpen} onHide={() => setIsGradingModalOpen(false)} size="lg" centered contentClassName="bg-dark text-light border-secondary">
                <Modal.Header closeButton className="border-secondary" closeVariant="white">
                    <Modal.Title className="fw-bold text-info">
                        Dolgozat Értékelése: {selectedSession?.studentName}
                    </Modal.Title>
                </Modal.Header>
                <Form onSubmit={handleGradeSubmit}>
                    <Modal.Body style={{ maxHeight: '60vh', overflowY: 'auto' }} className="custom-scrollbar">
                        
                        {/* Diák válaszainak listázása */}
                        <div className="mb-4">
                            <h6 className="fw-bold text-secondary mb-3 border-bottom border-secondary pb-2">A diák válaszai:</h6>
                            {(!selectedSession?.answers || selectedSession.answers.length === 0) ? (
                                <div className="text-light small">Nincsenek elérhető válaszadatok.</div>
                            ) : (
                                <ul className="list-unstyled">
                                    {selectedSession.answers.map((ans, index) => (
                                        <li key={index} className="mb-3 p-3 bg-black bg-opacity-25 rounded border border-secondary shadow-sm">
                                            
                                            {/* Fejléc a szem ikonnal */}
                                            <div className="text-info fw-bold mb-2 pb-2 border-bottom border-secondary d-flex justify-content-between align-items-center">
                                                <div>{index + 1}. Kérdés: <span className="text-light fw-normal">{ans.question}</span></div>
                                                
                                                {ans.exercise && (
                                                    <Button variant="link" size="sm" className="text-info p-0 text-decoration-none" title="Feladat előnézete" onClick={() => togglePreview(index)}>
                                                        előnézet👁️
                                                    </Button>
                                                )}
                                            </div>

                                            {/* AZ INLINE ELŐNÉZET (Pontosan a tesztkészítő mintájára) */}
                                            {expandedPreviewIndex === index && ans.exercise && (
                                                <div className="mt-1 mb-3 p-2 bg-dark rounded border border-info small">
                                                    <strong>Típus:</strong> {ans.exercise.type}<br/>
                                                    {ans.exercise.content?.options && (
                                                        <div>
                                                            <strong>Opciók:</strong> {
                                                                // Ha objektum (képes opció), akkor a textet írjuk ki, amúgy simán a stringet
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

                                            {/* Diák válasza és értékelés */}
                                            <div className="d-flex justify-content-between align-items-center mt-2 flex-wrap gap-2">
                                                <div>
                                                    <span className="text-secondary fw-bold">Diák válasza: </span> 
                                                    <span className="ms-1 text-light">
                                                        {typeof ans.studentAnswer === 'object' ? JSON.stringify(ans.studentAnswer) : ans.studentAnswer}
                                                    </span>
                                                </div>
                                                <div className="d-flex gap-2">
                                                    {ans.correct ? (
                                                        <Badge bg="success" className="px-2 py-1">Helyes</Badge>
                                                    ) : (
                                                        <Badge bg="danger" className="px-2 py-1">Hibás</Badge>
                                                    )}
                                                    {ans.retried && (
                                                        <Badge bg="warning" text="dark" className="px-2 py-1">
                                                            Második eséllyel javítva
                                                        </Badge>
                                                    )}
                                                </div>
                                            </div>
                                        </li>
                                    ))}
                                </ul>
                            )}
                        </div>

                        {/* Értékelő Űrlap */}
                        <div className="bg-darker p-3 rounded border border-secondary">
                            <Form.Group className="mb-3">
                                <Form.Label className="text-info fw-bold">Végső Pontszám (%)</Form.Label>
                                <div className="d-flex align-items-center gap-3">
                                    <Form.Control 
                                        type="number" 
                                        min="0" max="100" 
                                        className="bg-dark text-light border-info w-25" 
                                        value={gradeForm.teacherScore} 
                                        onChange={e => setGradeForm({...gradeForm, teacherScore: e.target.value})} 
                                        required 
                                    />
                                    <span className="text-secondary small">(Eredeti automata pont: {selectedSession?.finalScore}%)</span>
                                </div>
                            </Form.Group>
                            <Form.Group>
                                <Form.Label className="text-info fw-bold">Tanári Megjegyzés (Opcionális)</Form.Label>
                                <Form.Control 
                                    as="textarea" 
                                    rows={3} 
                                    className="bg-dark text-light border-secondary" 
                                    placeholder="Ide írhatsz szöveges visszajelzést a diáknak..."
                                    value={gradeForm.teacherComment} 
                                    onChange={e => setGradeForm({...gradeForm, teacherComment: e.target.value})} 
                                />
                            </Form.Group>
                        </div>

                    </Modal.Body>
                    <Modal.Footer className="border-secondary">
                        <Button variant="outline-light" onClick={() => setIsGradingModalOpen(false)}>Mégse</Button>
                        <Button variant="success" type="submit" className="fw-bold px-4" disabled={isSubmitting}>
                            {isSubmitting ? <Spinner size="sm" /> : 'Eredmény Publikálása'}
                        </Button>
                    </Modal.Footer>
                </Form>
            </Modal>       
        </Container>
    );
};

export default AssignmentSubmissions;