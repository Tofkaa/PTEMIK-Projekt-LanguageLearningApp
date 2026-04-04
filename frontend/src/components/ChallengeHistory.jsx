/**
 * ChallengeHistory Component
 * Renders a read-only log of all closed (Completed, Expired, Declined) challenges.
 * Visually distinguishes wins, losses, and ties.
 */

import { useState, useEffect } from 'react';
import { Card, Spinner, Alert, Badge, Row, Col } from 'react-bootstrap';
import api from '../services/api';
import { useAuth } from '../context/AuthContext';

const ChallengeHistory = () => {
    const { user } = useAuth();
    const [history, setHistory] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');

    useEffect(() => {
        fetchHistory();
    }, []);

    const fetchHistory = async () => {
        try {
            const response = await api.get('/challenges/history');
            setHistory(response.data || []);
        } catch (err) {
            setError('Nem sikerült betölteni az előzményeket.', err);
        } finally {
            setLoading(false);
        }
    };

    /**
     * Generates the appropriate UI badge based on the challenge status and the winner.
     * * @param {Object} challenge - The challenge data object from the backend.
     * @returns {JSX.Element|null} The React Bootstrap Badge component.
     */
    const getResultBadge = (challenge) => {
        if (challenge.status === 'DECLINED') return <Badge bg="danger">Elutasítva ❌</Badge>;
        if (challenge.status === 'EXPIRED') return <Badge bg="secondary">Lejárt ⏳</Badge>;
        
        if (challenge.status === 'COMPLETED') {
            if (challenge.winnerName === "Döntetlen") return <Badge bg="warning" text="dark">Döntetlen 🤝</Badge>;
            if (challenge.winnerName === user?.name) return <Badge bg="success">Győzelem! 🏆</Badge>;
            return <Badge bg="danger">Vereség 😢</Badge>;
        }
        return null;
    };

    if (loading) return <div className="text-center p-4"><Spinner animation="border" variant="info" /></div>;
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
            <h5 className="text-light mb-4">Előzmények ({history.length})</h5>
            <Row>
                {history.map((challenge) => {
                    const iAmChallenger = challenge.challengerName === user?.name;
                    const opponentName = iAmChallenger ? challenge.opponentName : challenge.challengerName;

                    return (
                        <Col md={12} key={challenge.challengeId} className="mb-3">
                            <Card className="bg-dark border-secondary shadow-sm opacity-75">
                                <Card.Body className="d-flex justify-content-between align-items-center">
                                    <div>
                                        <h6 className="text-light mb-1">
                                            ⚔️ Ellenfél: <strong className="text-info">{opponentName}</strong>
                                        </h6>
                                        <div className="text-light small">
                                            Lecke: {challenge.lessonTitle} ({challenge.difficulty})
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