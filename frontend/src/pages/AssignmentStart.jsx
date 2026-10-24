/**
 * @file AssignmentStart.jsx
 * @description Interstitial screen displayed before a student begins an assignment.
 * Presents rules, time limits, and attempt constraints, and initializes the server-side session.
 */

import { useEffect, useState } from 'react';
import { useParams, useNavigate, useLocation } from 'react-router-dom';
import { Container, Card, Button, Row, Col, Alert } from 'react-bootstrap';
import { assignmentApi } from '../services/assignmentApi';
import { parseServerDate, formatToLocalDisplay } from '../utils/dateUtils';
import LearningFeedback from '../components/LearningFeedback';
import { useCurrentTime } from '../hooks/useCurrentTime';

/**
 * @component
 * @returns {React.ReactElement} The assignment preparation UI.
 */
const AssignmentStart = () => {
    const { id: assignmentId } = useParams();
    const navigate = useNavigate();
    const location = useLocation();
    
    const [details, setDetails] = useState(location.state?.assignmentDetails);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');
    const [isStarting, setIsStarting] = useState(false);

    const currentTime = useCurrentTime();

    useEffect(() => {
        let active = true;
        assignmentApi.getAssignmentDetails(assignmentId).then(res => { if (active) setDetails(res.data); })
            .catch(() => { if (active) setError('Nem sikerült frissíteni a feladat időbeállításait. Nyisd meg újra az oldalt.'); })
            .finally(() => { if (active) setLoading(false); });
        return () => { active = false; };
    }, [assignmentId]);

    /**
     * Initiates the assignment session via the backend API.
     * On success, transitions the user to the AssignmentPlayer with the required session data.
     * 
     * @async
     * @function startTest
     */
    const startTest = async () => {
        if (isStarting || loading || error) return;
        setIsStarting(true);
        try {
            const res = await assignmentApi.startAssignment(assignmentId);
            navigate(`/assignment/session/${res.data.sessionId}/play`, { 
                state: { sessionData: res.data, assignmentDetails: details } 
            });
        } catch (error) {
            console.error("Failed to start assignment", error);
            setError(error.response?.data?.message || 'Nem sikerült elindítani a feladatot. Frissítsd az oldalt és próbáld újra.');
            setIsStarting(false);
        }
    };

    if (loading && !details) return <Container><h1 className="h4">Feladat betöltése...</h1></Container>;
    if (!details) {
        return (
            <Container className="py-5 text-center text-light">
                <h1 className="h4 text-danger mb-4">A feladat adatai nem találhatóak. Kérlek, indítsd el újra az osztályteremből!</h1>
                <Button variant="outline-secondary" onClick={() => navigate('/classrooms')}>Vissza az osztálytermekhez</Button>
            </Container>
        );
    }

    return (
        <Container className="py-5">
            <Row className="justify-content-center">
                <Col md={8} lg={6}>
                    <Card className="bg-dark text-light border-primary shadow-lg p-4 text-center">
                        <h1 className="h2 fw-bold mb-4 app-accent-text">Készen állsz?</h1>
                        <LearningFeedback error={error} />
                        
                        {details.test ? (
                            <Alert variant="warning">
                                <h2 className="h5 fw-bold">Figyelem! Ez egy szigorú teszt.</h2>
                                <p className="mb-0">
                                    Amint rányomsz az indításra, az időmérés megkezdődik. 
                                    Ne frissíts rá az oldalra, és ne zárd be az ablakot, amíg nem küldted be a válaszaidat!
                                </p>
                            </Alert>
                        ) : (
                            <Alert variant="info">
                                <h2 className="h5 fw-bold">Gyakorló feladatsor</h2>
                                <p className="mb-0">Minden feladatra azonnali visszajelzést fogsz kapni, és újra próbálkozhatsz a hibás válaszokkal.</p>
                            </Alert>
                        )}
                        
                        <div className="my-4 text-start bg-darker p-3 rounded border border-secondary">
                            <p className="mb-2"><strong>Cím:</strong> {details.title}</p>
                            {details.description && <p className="mb-2 text-secondary"><strong>Leírás:</strong> {details.description}</p>}
                            <p className="mb-2 text-warning">
                                <strong>Időkeret:</strong> {details.timeLimitMinutes ? `${details.timeLimitMinutes} perc` : 'Nincs időkorlát'}
                            </p>
                            <p className="mb-0"><strong>Kérdések száma:</strong> {details.exerciseCount} db</p>
                            <p><strong>Rád érvényes beadási határidő:</strong> {details.availableUntil ? formatToLocalDisplay(details.availableUntil) : 'Nincs'}</p>
                            {(details.timeLimitMinutes || details.availableUntil) && <p className="small text-body-secondary">
                                A tanárod egyéni időkeretet és beadási határidőt állíthat be. Ha erre van szükséged, még az indítás előtt egyeztess vele.
                            </p>}
                            <p className="mb-0 text-info mt-2 border-top border-secondary pt-2">
                                <strong>Felhasznált próbálkozások:</strong> {details.attemptsUsed || 0} / {details.maxAttempts ? details.maxAttempts : 'Végtelen'}
                            </p>
                        </div>

                        <div className="d-grid gap-3">
                            {(() => {
                                // Safe timecheck
                                const isFuture = details.availableFrom && parseServerDate(details.availableFrom).getTime() > currentTime;
                                return (
                                    <Button 
                                        variant="primary" size="lg" className="fw-bold" 
                                        onClick={startTest} disabled={loading || isFuture || !!error} aria-disabled={isStarting}
                                    >
                                        {isStarting ? 'Indítás...' : (isFuture ? 'Még nem elérhető' : (details.test ? 'Teszt Megkezdése' : 'Gyakorlás Indítása'))}
                                    </Button>
                                );
                            })()}
                            <Button variant="outline-secondary" onClick={() => navigate(-1)} disabled={isStarting}>
                                Mégse, vissza
                            </Button>
                        </div>
                    </Card>
                </Col>
            </Row>
        </Container>
    );
};

export default AssignmentStart;
