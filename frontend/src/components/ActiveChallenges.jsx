/**
 * ActiveChallenges Component
 * Displays the list of ongoing duels for the authenticated user.
 * Manages the UI state to distinguish between waiting periods and actionable turns.
 */

import { useState, useEffect } from 'react';
import { Card, Button, Spinner, Alert, Badge, Row, Col } from 'react-bootstrap';
import { useNavigate } from 'react-router-dom';
import api from '../services/api';
// 1. BEIMPORTÁLJUK AZ AUTH CONTEXT-ET
import { useAuth } from '../context/AuthContext';

const ActiveChallenges = () => {
    const navigate = useNavigate();
    // 2. KINYERJÜK A BEJELENTKEZETT FELHASZNÁLÓT
    const { user } = useAuth(); 
    const [challenges, setChallenges] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');

    useEffect(() => {
        fetchChallenges();
    }, []);

    const fetchChallenges = async () => {
        try {
            const response = await api.get('/challenges/active');
            setChallenges(response.data || []);
        } catch (err) {
            setError('Nem sikerült betölteni az aktív kihívásokat.', err);
        } finally {
            setLoading(false);
        }
    };

    const handlePlayChallenge = (challenge) => {
        // Átirányítás a Bypass móddal!
        navigate(`/lesson/${challenge.lessonId}?challengeId=${challenge.challengeId}`);
    };

    if (loading) return <div className="text-center p-4"><Spinner animation="border" variant="warning" /></div>;
    if (error) return <Alert variant="danger">{error}</Alert>;

    if (challenges.length === 0) {
        return (
            <div className="p-5 text-center text-light">
                <h5 className="mb-3">Minden csendes...</h5>
                <p className="text-light">Jelenleg nincs egyetlen aktív kihívásod sem. Lépj a Barátaim fülre, és indíts egyet!</p>
            </div>
        );
    }

    return (
        <div className="p-3">
            <h5 className="text-light mb-4">Aktív Kihívások ({challenges.length})</h5>
            <Row>
                {challenges.map((challenge) => {
                    // Determine if the current user initiated this challenge to render contextual text
                    const iAmChallenger = challenge.challengerName === user?.name;

                    return (
                        <Col md={12} key={challenge.challengeId} className="mb-3">
                            <Card className={`bg-dark border-${challenge.isMyTurn ? 'warning' : 'secondary'} shadow-sm`}>
                                <Card.Body className="d-flex justify-content-between align-items-center">
                                    <div>
                                        {/* Contextual Header based on roles */}
                                        <h5 className="text-light mb-1 fw-bold">
                                            {iAmChallenger 
                                                ? `⚔️ Kihívtad: ${challenge.opponentName}` 
                                                : `🔥 ${challenge.challengerName} kihívott téged!`}
                                        </h5>
                                        {/* ... lesson info ... */}
                                    </div>
                                    <div className="d-flex flex-column align-items-end gap-2">
                                        <small className="text-danger">
                                            Lejár: {new Date(challenge.expiresAt).toLocaleDateString('hu-HU')}
                                        </small>
                                        
                                        {/* Render action button ONLY if the backend flag indicates it is the user's turn */}
                                        {challenge.isMyTurn && (
                                            <Button 
                                                variant="warning" 
                                                className="fw-bold px-4"
                                                onClick={() => handlePlayChallenge(challenge)} 
                                            >
                                                Játék! 🚀
                                            </Button>
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

export default ActiveChallenges;