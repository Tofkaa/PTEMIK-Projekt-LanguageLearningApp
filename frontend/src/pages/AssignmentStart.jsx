/**
 * @file AssignmentStart.jsx
 * @description Interstitial screen displayed before a student begins an assignment.
 * Presents rules, time limits, and attempt constraints, and initializes the server-side session.
 */

import React, { useState } from 'react';
import { useParams, useNavigate, useLocation } from 'react-router-dom';
import { Container, Card, Button, Row, Col, Alert, Spinner } from 'react-bootstrap';
import { assignmentApi } from '../services/assignmentApi';
import { parseServerDate } from '../utils/dateUtils';

/**
 * @component
 * @returns {React.ReactElement} The assignment preparation UI.
 */
const AssignmentStart = () => {
    const { id: assignmentId } = useParams();
    const navigate = useNavigate();
    const location = useLocation();
    
    const details = location.state?.assignmentDetails;
    const [isStarting, setIsStarting] = useState(false);

    const [currentTime] = useState(() => Date.now());

    /**
     * Initiates the assignment session via the backend API.
     * On success, transitions the user to the AssignmentPlayer with the required session data.
     * 
     * @async
     * @function startTest
     */
    const startTest = async () => {
        setIsStarting(true);
        try {
            const res = await assignmentApi.startAssignment(assignmentId);
            navigate(`/assignment/session/${res.data.sessionId}/play`, { 
                state: { sessionData: res.data, assignmentDetails: details } 
            });
        } catch (error) {
            console.error("Failed to start assignment", error);
            alert("Hiba történt a teszt indításakor. Lehet, hogy már kitöltötted, vagy lejárt az idő.");
            setIsStarting(false);
        }
    };

    if (!details) {
        return (
            <Container className="py-5 text-center text-light">
                <h4 className="text-danger mb-4">A feladat adatai nem találhatóak. Kérlek, indítsd el újra az osztályteremből!</h4>
                <Button variant="outline-secondary" onClick={() => navigate('/classrooms')}>Vissza az osztálytermekhez</Button>
            </Container>
        );
    }

    return (
        <Container className="py-5">
            <Row className="justify-content-center">
                <Col md={8} lg={6}>
                    <Card className="bg-dark text-light border-primary shadow-lg p-4 text-center">
                        <h2 className="fw-bold mb-4 text-primary">Készen állsz?</h2>
                        
                        {details.test ? (
                            <Alert variant="warning" className="bg-dark text-warning border-warning">
                                <h5 className="fw-bold">Figyelem! Ez egy szigorú teszt.</h5>
                                <p className="mb-0">
                                    Amint rányomsz az indításra, az időmérés megkezdődik. 
                                    Ne frissíts rá az oldalra, és ne zárd be az ablakot, amíg nem küldted be a válaszaidat!
                                </p>
                            </Alert>
                        ) : (
                            <Alert variant="info" className="bg-dark text-info border-info">
                                <h5 className="fw-bold">Gyakorló feladatsor</h5>
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
                                        onClick={startTest} disabled={isStarting || isFuture}
                                    >
                                        {isStarting ? <Spinner size="sm" /> : (isFuture ? 'Még nem elérhető' : (details.test ? 'Teszt Megkezdése' : 'Gyakorlás Indítása'))}
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