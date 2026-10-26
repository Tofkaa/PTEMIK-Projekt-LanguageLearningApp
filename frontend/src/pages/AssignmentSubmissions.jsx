/**
 * @file AssignmentSubmissions.jsx
 * @description Teacher dashboard view for evaluating and grading student submissions.
 * Displays aggregate performance stats, individual sessions, and an interactive grading modal.
 */

import React, { useState, useEffect, useCallback, useRef } from 'react';
import { useParams, useNavigate, useLocation } from 'react-router-dom';
import { Container, Card, Button, Badge, Table, Modal, Form, Spinner, Row, Col, Alert } from 'react-bootstrap';
import { assignmentApi } from '../services/assignmentApi';
import { formatToLocalDisplay, parseServerDate } from '../utils/dateUtils';
import ExercisePreviewCard from '../components/ExercisePreviewCard';
import LanguageText from '../components/LanguageText';
import LearningFeedback from '../components/LearningFeedback';

/**
 * @component
 * @returns {React.ReactElement} The submission overview and grading interface.
 */
const AssignmentSubmissions = () => {
    const { id: assignmentId } = useParams();
    const navigate = useNavigate();
    const location = useLocation();
    
    const assignmentTitle = location.state?.assignmentTitle || 'Feladat';
    const classroomName = location.state?.classroomName || 'Osztályterem';
    const timeLimitMinutes = location.state?.timeLimitMinutes || null;

    const [sessions, setSessions] = useState([]);
    const [isLoading, setIsLoading] = useState(true);
    
    // Grading modal states
    const [selectedSession, setSelectedSession] = useState(null);
    const [isGradingModalOpen, setIsGradingModalOpen] = useState(false);
    const [gradeForm, setGradeForm] = useState({ teacherScore: '', teacherComment: '' });
    const [isSubmitting, setIsSubmitting] = useState(false);
    const [expandedPreviewIndex, setExpandedPreviewIndex] = useState(null);
    const [loadError, setLoadError] = useState('');
    const [gradeError, setGradeError] = useState('');
    const [gradeConflict, setGradeConflict] = useState(false);
    const [isReloadingGrade, setIsReloadingGrade] = useState(false);
    const [latestGrade, setLatestGrade] = useState(null);
    const gradingView = useRef(0);
    const [feedback, setFeedback] = useState(null);

    /**
     * Calculates if the student's submission is late, and if it is, by how much
     */
    const getLateInfo = (session) => {
        if (!session?.startedAt || !session?.finishedAt) return null;
        
        const startDate = parseServerDate(session.startedAt);
        const endDate = parseServerDate(session.finishedAt);
        if (!startDate || !endDate) return '-';
        const start = startDate.getTime();
        const end = endDate.getTime();
        
        const deadline = Object.hasOwn(session, 'submissionDeadline')
            ? parseServerDate(session.submissionDeadline)?.getTime()
            : timeLimitMinutes ? start + timeLimitMinutes * 60000 : null;
        if (deadline == null) return null;
        const overMs = end - deadline;
        
        // 5 másodperc kegyelmi idő hálózati késés miatt
        if (overMs > 5000) {
            const overS = Math.floor(overMs / 1000);
            const overM = Math.floor(overS / 60);
            const remS = overS % 60;
            return `${overM}p ${remS}mp`;
        }
        return null;
    };

    /**
     * Toggles the inline preview box for a specific question's details.
     */
    const togglePreview = (index) => {
        setExpandedPreviewIndex(prev => prev === index ? null : index);
    };

    /**
     * Fetches all completed sessions for the selected assignment.
     */
    const fetchSessions = useCallback(async () => {
        setIsLoading(true);
        setLoadError('');
        try {
            const res = await assignmentApi.getAssignmentSessions(assignmentId);
            setSessions(res.data);
        } catch (error) {
            console.error("Hiba a beadott munkák lekérésekor:", error);
            setLoadError('Nem sikerült lekérni a listát. Ellenőrizd a kapcsolatot és a jogosultságodat, majd próbáld újra!');
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

    /**
     * Prepares and opens the grading modal with the selected session's data.
     */
    const openGradingModal = (session) => {
        gradingView.current += 1;
        setSelectedSession(session);
        setGradeForm({
            teacherScore: session.teacherScore ?? session.finalScore,
            teacherComment: session.teacherComment || ''
        });
        setGradeError('');
        setGradeConflict(false);
        setLatestGrade(null);
        setIsReloadingGrade(false);
        setExpandedPreviewIndex(null);
        setIsGradingModalOpen(true);
    };

    const closeGradingModal = () => {
        if (isSubmitting) return;
        gradingView.current += 1;
        setIsGradingModalOpen(false);
    };
    const reloadLatestGrade = async () => {
        if (isReloadingGrade || isSubmitting) return;
        const view = gradingView.current;
        setIsReloadingGrade(true);
        try {
            const response = await assignmentApi.getAssignmentSessions(assignmentId);
            if (view !== gradingView.current) return;
            const current = response.data.find(session => session.sessionId === selectedSession.sessionId);
            if (!current) { setGradeError('A beadás már nem található. Frissítsd a listát.'); return; }
            setSessions(response.data);
            setSelectedSession(current);
            setLatestGrade(current);
            setGradeConflict(false);
            setGradeError('');
        } catch {
            if (view === gradingView.current) setGradeError('A legfrissebb értékelés betöltése nem sikerült. A saját módosításaid megmaradtak.');
        } finally {
            if (view === gradingView.current) setIsReloadingGrade(false);
        }
    };

    /**
     * Submits the teacher's manual evaluation.
     */
    const handleGradeSubmit = async (e) => {
        e.preventDefault();
        if (isSubmitting || isReloadingGrade || gradeConflict) return;
        const score = Number(gradeForm.teacherScore);
        if (gradeForm.teacherScore === '' || !Number.isInteger(score) || score < 0 || score > 100) {
            setGradeError('A végső pontszám 0 és 100 közötti egész szám legyen!');
            return;
        }
        if (gradeForm.teacherComment.length > 4000) { setGradeError('A megjegyzés legfeljebb 4000 karakter lehet.'); return; }
        setIsSubmitting(true);
        setGradeError('');

        try {
            const response = await assignmentApi.gradeSession(selectedSession.sessionId, {
                teacherScore: score,
                teacherComment: gradeForm.teacherComment,
                revision: selectedSession.gradingRevision ?? 0
            });
            setSessions(previous => previous.map(session => session.sessionId === selectedSession.sessionId
                ? { ...session, teacherScore: score, teacherComment: gradeForm.teacherComment, graded: true,
                    gradingRevision: response.data?.gradingRevision ?? (selectedSession.gradingRevision ?? 0) + 1 } : session));
            setFeedback({ type: 'success', msg: `Az értékelés publikálva: ${selectedSession.studentName}.` });
            setIsGradingModalOpen(false);
        } catch (error) {
            if (error.response?.status === 409) setGradeConflict(true);
            setGradeError(error.response?.data?.message || 'Nem sikerült elmenteni az értékelést. A pontszám és a megjegyzés megmaradt; próbáld újra!');
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
        const sortedMistakes = Object.entries(mistakeCounts).sort((a, b) => b[1] - a[1]);
        return { question: sortedMistakes[0][0], count: sortedMistakes[0][1] };
    };

    const hardest = getHardestQuestion();

    if (isLoading) {
        return <Container role="status" className="d-flex justify-content-center align-items-center" style={{ height: '50vh' }}><Spinner animation="border" variant="info" aria-hidden="true" /> Beadott munkák betöltése…</Container>;
    }

    return (
        <Container className="py-4 text-light animate-fade-in">
            <div className="mb-4">
                <Button variant="outline-secondary" size="sm" onClick={() => navigate(-1)} className="mb-2">
                    ← Vissza az osztályterembe
                </Button>
                <p className="h4 text-secondary">{classroomName}</p>
                <h1 className="h2 fw-bold m-0 app-accent-text">Beadott munkák: {assignmentTitle}</h1>
            </div>
            <LearningFeedback error={loadError} feedback={feedback} />
            {loadError && <Button variant="outline-info" onClick={fetchSessions}>Lista újratöltése</Button>}

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
                        <div className="fs-4 fw-bold app-accent-text">
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
                                <div className="small fw-bold text-warning px-2">
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
                    <div className="table-responsive" role="region" tabIndex={0} aria-label="Beadások, görgethető táblázat">
                    <Table hover variant="dark" className="submission-table m-0">
                        <caption className="visually-hidden">Beadások, pontszámok és értékelési műveletek</caption>
                        <thead className="border-secondary">
                            <tr>
                                <th scope="col">Diák Neve</th>
                                <th scope="col">Beküldve</th>
                                <th scope="col" className="text-center">Automata Pont</th>
                                <th scope="col" className="text-center">Tanári Pont</th>
                                <th scope="col" className="text-center">Státusz</th>
                                <th scope="col" className="text-end">Művelet</th>
                            </tr>
                        </thead>
                        <tbody>
                            {sessions.length === 0 ? (
                                <tr><td colSpan="6" className="text-center py-4 text-light">Még senki sem küldte be ezt a feladatot.</td></tr>
                            ) : (
                                sessions.map(session => {
                                    const lateString = getLateInfo(session);

                                    return (
                                        <tr key={session.sessionId} className="align-middle">
                                            <th scope="row" className="fw-normal">
                                                <div className="fw-bold">{session.studentName}</div>
                                                <div className="small text-secondary">{session.studentEmail}</div>
                                            </th>
                                            <td>
                                                <div>{formatToLocalDisplay(session.finishedAt)}</div>
                                                {lateString && (
                                                    <Badge bg="danger" className="mt-1 shadow-sm d-block" title={`Az eredeti korlát ${timeLimitMinutes} perc volt.`}>
                                                        ⏱️ +{lateString} késés
                                                    </Badge>
                                                )}
                                            </td>
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
                                                <Button aria-label={`${session.graded ? 'Módosítás' : 'Értékelés'}: ${session.studentName}, ${formatToLocalDisplay(session.finishedAt)}`} variant={session.graded ? "outline-info" : "primary"} size="sm" onClick={() => openGradingModal(session)}>
                                                    {session.graded ? 'Módosítás' : 'Értékelés'}
                                                </Button>
                                            </td>
                                        </tr>
                                    );
                                })
                            )}
                        </tbody>
                    </Table>
                    </div>
                </Card.Body>
            </Card>

            {/* GRADING MODAL */}
            <Modal show={isGradingModalOpen} onHide={closeGradingModal} aria-labelledby="grading-modal-title" size="lg" centered contentClassName="bg-dark text-light border-secondary">
                <Modal.Header closeButton closeLabel="Értékelés bezárása" className="border-secondary">
                    <Modal.Title as="h2" id="grading-modal-title" className="fw-bold app-accent-text">
                        Dolgozat Értékelése: {selectedSession?.studentName}
                    </Modal.Title>
                </Modal.Header>
                <Form onSubmit={handleGradeSubmit}>
                    <Modal.Body style={{ maxHeight: '60vh', overflowY: 'auto' }} className="custom-scrollbar">
                        <LearningFeedback error={gradeError} />
                        {(gradeConflict || latestGrade || isReloadingGrade) && <div className="mb-3">
                            <Button variant="outline-info" onClick={reloadLatestGrade} aria-busy={isReloadingGrade}
                                aria-disabled={isReloadingGrade || isSubmitting}>Legfrissebb értékelés betöltése</Button>
                            {latestGrade && <section className="border rounded p-3 mt-2" aria-label="Legfrissebb publikált értékelés">
                                <p role="status">A legfrissebb értékelést betöltöttük. A saját mezőid megmaradtak; összevetés után újra publikálhatsz.</p>
                                <p>Legutóbbi pontszám: {latestGrade.teacherScore ?? latestGrade.finalScore}%</p>
                                <p style={{ whiteSpace: 'pre-wrap' }}>{latestGrade.teacherComment || 'Nincs tanári megjegyzés.'}</p>
                            </section>}
                        </div>}
                        
                        {/* HATALMAS PIROS FIGYELMEZTETÉS A KÉSÉSRŐL A MODALBAN */}
                        {selectedSession && getLateInfo(selectedSession) && (
                            <Alert role="note" variant="danger" className="d-flex align-items-center mb-4 shadow-sm border-danger">
                                <span aria-hidden="true" className="fs-2 me-3">⚠️</span>
                                <div>
                                    <h5 className="fw-bold alert-heading mb-1">Időtúllépés történt!</h5>
                                    <div>
                                        A diák <strong>+{getLateInfo(selectedSession)}</strong> késéssel adta be a munkát 
                                        a rá érvényes beadási időponthoz képest. Ezt vedd figyelembe a pontozásnál!
                                    </div>
                                </div>
                            </Alert>
                        )}

                        <div className="mb-4">
                            <h3 className="h6 fw-bold text-secondary mb-3 border-bottom border-secondary pb-2">A diák válaszai:</h3>
                            {(!selectedSession?.answers || selectedSession.answers.length === 0) ? (
                                <div className="text-light small">Nincsenek elérhető válaszadatok.</div>
                            ) : (
                                <ul className="list-unstyled">
                                    {selectedSession.answers.map((ans, index) => (
                                        <li key={index} className="mb-3 p-3 bg-black bg-opacity-25 rounded border border-secondary shadow-sm">
                                            
                                            <div className="app-accent-text fw-bold mb-2 pb-2 border-bottom border-secondary d-flex flex-wrap gap-2 justify-content-between align-items-center">
                                                <div>{index + 1}. Kérdés: <span className="text-light fw-normal"><LanguageText text={ans.question} lang={ans.exercise?.content?.questionLang || 'hu'} parts={ans.exercise?.content?.questionParts} /></span></div>
                                                
                                                {ans.exercise && (
                                                    <Button aria-label={`előnézet: ${index + 1}. kérdés`} aria-expanded={expandedPreviewIndex === index} aria-controls={`grading-preview-${index}`} variant="link" size="sm" className="app-accent-text p-0 text-decoration-none" onClick={() => togglePreview(index)}>
                                                        előnézet<span aria-hidden="true">👁️</span>
                                                    </Button>
                                                )}
                                            </div>

                                          {ans.exercise && (
                                                <div id={`grading-preview-${index}`} hidden={expandedPreviewIndex !== index} className="mt-2 mb-3">
                                                    {expandedPreviewIndex === index && <ExercisePreviewCard
                                                        exercise={ans.exercise} 
                                                        serverCorrectAnswer={ans.serverCorrectAnswer} 
                                                    />}
                                                </div>
                                            )}

                                            <div className="d-flex justify-content-between align-items-center mt-2 flex-wrap gap-2">
                                                <div>
                                                    <span className="text-secondary fw-bold">Diák válasza: </span> 
                                                    <span lang={ans.exercise?.content?.answerLang} className="ms-1 text-light">
                                                        {typeof ans.studentAnswer === 'object' ? JSON.stringify(ans.studentAnswer) : ans.studentAnswer}
                                                    </span>
                                                </div>
                                                <div className="d-flex flex-wrap gap-2">
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

                        {/* GRADING FORM */}
                        <div className="bg-darker p-3 rounded border border-secondary">
                            <Form.Group className="mb-3" controlId="teacher-final-score">
                                <Form.Label className="app-accent-text fw-bold">Végső Pontszám (%)</Form.Label>
                                <div className="d-flex flex-wrap align-items-center gap-3">
                                    <Form.Control 
                                        type="number" 
                                        min="0" max="100" 
                                        className="bg-dark text-light border-info grading-score-input"
                                        value={gradeForm.teacherScore} 
                                        readOnly={isSubmitting}
                                        onChange={e => setGradeForm({...gradeForm, teacherScore: e.target.value})} 
                                        required 
                                    />
                                    <span className="text-secondary small">(Eredeti automata pont: {selectedSession?.finalScore}%)</span>
                                </div>
                            </Form.Group>
                            <Form.Group controlId="teacher-comment">
                                <Form.Label className="app-accent-text fw-bold">Tanári Megjegyzés (Opcionális)</Form.Label>
                                <Form.Control 
                                    as="textarea" 
                                    rows={3} 
                                    maxLength={4000}
                                    aria-describedby="teacher-comment-help"
                                    className="bg-dark text-light border-secondary" 
                                    placeholder="Ide írhatsz szöveges visszajelzést a diáknak..."
                                    value={gradeForm.teacherComment} 
                                    readOnly={isSubmitting}
                                    onChange={e => setGradeForm({...gradeForm, teacherComment: e.target.value})} 
                                />
                                <Form.Text id="teacher-comment-help">Legfeljebb 4000 karakter.</Form.Text>
                            </Form.Group>
                        </div>

                    </Modal.Body>
                    <Modal.Footer className="border-secondary">
                        <Button variant="outline-secondary" onClick={closeGradingModal} disabled={isSubmitting}>Mégse</Button>
                        <Button variant="success" type="submit" className="fw-bold px-4" aria-disabled={isSubmitting || isReloadingGrade || gradeConflict} aria-busy={isSubmitting}>
                            {isSubmitting && <Spinner size="sm" aria-hidden="true" className="me-2" />}Eredmény Publikálása
                        </Button>
                    </Modal.Footer>
                </Form>
            </Modal>       
        </Container>
    );
};

export default AssignmentSubmissions;
