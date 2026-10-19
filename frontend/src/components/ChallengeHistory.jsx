/**
 * @file ChallengeHistory.jsx
 * @description Renders a read-only log of all closed (Completed, Expired, Declined) challenges.
 * Visually distinguishes wins, losses, and ties, and maps backend timestamps securely.
 */

import { useState, useEffect } from 'react';
import { Card, Spinner, Alert, Badge, Row, Col } from 'react-bootstrap';
import api from '../services/api';
import { useAuth } from '../context/AuthContext';
import { useNotifications } from '../context/NotificationContext'
import { formatToLocalDisplay } from '../utils/dateUtils';

const AVAILABLE_COURSES = {
    'en': { name: 'Angol', flag: '🇬🇧' },
    'es': { name: 'Spanyol', flag: '🇪🇸' },
    'de': { name: 'Német', flag: '🇩🇪' }
};

/**
 * @component
 * @returns {React.ReactElement} A list of historical challenge records.
 */
const ChallengeHistory = () => {
    const { user } = useAuth();
    const [history, setHistory] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');
    const { notifications } = useNotifications();

    useEffect(() => {
        fetchHistory(true);
    }, [notifications.totalHistory]); 

    /**
     * Fetches the user's closed challenges from the API.
     */
    const fetchHistory = async (isInitialLoad = false) => {
        if (isInitialLoad) setLoading(true);       
        
        try {
            const response = await api.get('/challenges/history', {
                params: { _t: Date.now() }
            });
            setHistory(response.data || []);
       } catch (err) {
            console.error("Előzmények betöltési hiba:", err);
            if (isInitialLoad) setError('Nem sikerült betölteni az előzményeket.');
        } finally {
           if (isInitialLoad) setLoading(false);
        }
    };

    /**
     * Generates the appropriate UI badge based on the challenge status and the winner.
     */
    const getResultBadge = (challenge) => {
        if (challenge.status === 'DECLINED') return <Badge bg="danger">Elutasítva <span aria-hidden="true">❌</span></Badge>;
        if (challenge.status === 'EXPIRED') return <Badge bg="secondary">Lejárt <span aria-hidden="true">⏳</span></Badge>;
        
        if (challenge.status === 'COMPLETED') {
            if (challenge.winnerName === "Döntetlen") return <Badge bg="warning" text="dark">Döntetlen <span aria-hidden="true">🤝</span></Badge>;
            if (challenge.winnerName === user?.name) return <Badge bg="success">Győzelem! <span aria-hidden="true">🏆</span></Badge>;
            return <Badge bg="danger">Vereség <span aria-hidden="true">😢</span></Badge>;
        }
        return null;
    };

    if (loading) return <div role="status" className="text-center p-4"><Spinner animation="border" variant="info" aria-hidden="true" /> Előzmények betöltése…</div>;
    if (error) return <Alert variant="danger">{error}</Alert>;

    if (history.length === 0) {
        return (
            <div className="p-5 text-center text-light">
                <h5 className="mb-3">Még nem vívtál meg egyetlen csatát sem.</h5>
                <p className="text-light">A befejezett, lejárt vagy elutasított kihívások itt fognak megjelenni.</p>
            </div>
        );
    }

    return (
        <div className="p-3">
            <h2 className="h5 text-light mb-4">Előzmények ({history.length})</h2>
            <Row>
                {history.map((challenge) => {
                    const iAmChallenger = challenge.challengerName === user?.name;
                    const opponentName = iAmChallenger ? challenge.opponentName : challenge.challengerName;

                    const courseCode = challenge.language || challenge.courseCode || 'en'; 
                    const courseData = AVAILABLE_COURSES[courseCode] || { name: 'Ismeretlen', flag: '🏳️' };

                    return (
                        <Col md={12} key={challenge.challengeId} className="mb-3">
                            <Card className="bg-dark border-secondary shadow-sm">
                                <Card.Body className="d-flex justify-content-between align-items-center">
                                    <div>
                                        <h3 className="h6 text-light mb-1">
                                            <span aria-hidden="true">⚔️ </span>Ellenfél: <strong className="text-info">{opponentName}</strong>
                                        </h3>
                                        <div className="text-light small">
                                           
                                            <span className="me-2"><span aria-hidden="true">{courseData.flag} </span>{courseData.name}.</span>
                                            Lecke: {challenge.lessonTitle} ({challenge.difficulty})
                                        </div>
                                        <div className="text-secondary small mt-1">
                                            Befejezve: {formatToLocalDisplay(challenge.completedAt)}
                                        </div>
                                    </div>
                                    <div className="d-flex flex-column align-items-end gap-1">
                                        {getResultBadge(challenge)}
                                        {challenge.winnerName && challenge.winnerName !== "Döntetlen" && challenge.status === 'COMPLETED' && (
                                            <small className="text-light mt-1" style={{ fontSize: '0.75rem' }}>
                                                Győztes: {challenge.winnerName}
                                            </small>
                                        )}
                                    </div>
                                </Card.Body>
                            </Card>
                        </Col>
                    );
                })}
            </Row>
        </div>
    );
};

export default ChallengeHistory;
