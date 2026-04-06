/**
 * @file ActiveChallenges.jsx
 * @description Displays the list of ongoing duels for the authenticated user.
 * Manages the UI state to distinguish between waiting periods and actionable turns.
 */

import { useState, useEffect} from 'react';
import { Card, Button, Spinner, Alert, Row, Col } from 'react-bootstrap';
import { useNavigate } from 'react-router-dom';
import api from '../services/api';
import { useAuth } from '../context/AuthContext';
import { useNotifications } from '../context/NotificationContext';

/**
 * @component
 * @returns {React.ReactElement} Grid of active challenges.
 */
const ActiveChallenges = () => {
    const navigate = useNavigate();
    const { user } = useAuth(); 
    const [challenges, setChallenges] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');
    const { notifications } = useNotifications();

    useEffect(() => {
       fetchChallenges(true); 
    }, [notifications.pendingChallenges]);

    /**
     * Fetches active challenges, bypassing cache.
     */
    const fetchChallenges = async (isInitialLoad = false) => {
        if(isInitialLoad) setLoading(true);
        
        try {
            const response = await api.get('/challenges/active', {
                params: { _t: new Date().getTime() } // Cache-busting
            });
            setChallenges(response.data || []);
        } catch (err) {
           if (isInitialLoad) setError('Nem sikerült betölteni az aktív kihívásokat.', err);
        } finally {
            if (isInitialLoad) setLoading(false);
        }
    };

    const handlePlayChallenge = (challenge) => {
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

    // ... Return HTML blokk (a Row és Col rész marad teljesen ugyanaz)
    return (
        <div className="p-3">
            <h5 className="text-light mb-4">Aktív Kihívások ({challenges.length})</h5>
            <Row>
                {challenges.map((challenge) => {
                    const iAmChallenger = challenge.challengerName === user?.name;
                    return (
                        <Col md={12} key={challenge.challengeId} className="mb-3">
                            <Card className={`bg-dark border-${challenge.isMyTurn ? 'warning' : 'secondary'} shadow-sm`}>
                                <Card.Body className="d-flex justify-content-between align-items-center">
                                    <div>
                                        <h5 className="text-light mb-1 fw-bold">
                                            {iAmChallenger 
                                                ? `⚔️ Kihívtad: ${challenge.opponentName}` 
                                                : `🔥 ${challenge.challengerName} kihívott téged!`}
                                        </h5>
                                    </div>
                                    <div className="d-flex flex-column align-items-end gap-2">
                                        <small className="text-danger">
                                            Lejár: {new Date(challenge.expiresAt).toLocaleDateString('hu-HU')}
                                        </small>
                                        {challenge.isMyTurn && (
                                            <Button variant="warning" className="fw-bold px-4" onClick={() => handlePlayChallenge(challenge)} >
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