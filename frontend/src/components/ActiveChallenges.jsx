import { safeError } from '../utils/safeError';
/**
 * @file ActiveChallenges.jsx
 * @description Displays the list of ongoing duels for the authenticated user.
 * Manages the UI state to distinguish between waiting periods and actionable turns,
 * and accurately handles timestamp display via centralized utilities.
 */

import { useRef, useState, useEffect} from 'react';
import { Card, Button, Spinner, Alert, Row, Col } from 'react-bootstrap';
import { useNavigate } from 'react-router-dom';
import api from '../services/api';
import { useAuth } from '../context/AuthContext';
import { useNotifications } from '../context/NotificationContext';
import { formatToLocalDisplay } from '../utils/dateUtils';

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
    const isFirstLoad = useRef(true);
    const { notifications } = useNotifications();

    useEffect(() => {
        fetchChallenges(isFirstLoad.current);
        isFirstLoad.current = false;
    }, [notifications.pendingChallenges]);

    /**
     * Fetches active challenges, bypassing cache to ensure fresh data.
     * 
     * @async
     * @function fetchChallenges
     * @param {boolean} [isInitialLoad=false] - Determines if the loading spinner should be triggered.
     */
   const fetchChallenges = async (isInitialLoad = false) => {
        if (isInitialLoad) setLoading(true);
        
        try {
            const response = await api.get('/challenges/active', {
                params: { _t: Date.now() } 
            });
            setChallenges(response.data || []);
        } catch (err) {
            console.error("Hiba az aktív kihívások betöltésekor:", safeError(err));
            if (isInitialLoad) setError('Nem sikerült betölteni az aktív kihívásokat.');
        } finally {
            if (isInitialLoad) setLoading(false);
        }
    };

    /**
     * Navigates the user to the lesson player to execute their turn in a duel.
     * 
     * @param {Object} challenge - The target challenge data object.
     */
    const handlePlayChallenge = (challenge) => {
        navigate(`/lesson/${challenge.lessonId}?challengeId=${challenge.challengeId}`);
    };

    if (loading) return <div role="status" className="text-center p-4"><Spinner animation="border" variant="warning" aria-hidden="true" /> Aktív kihívások betöltése…</div>;
    if (error) return <Alert variant="danger">{error}</Alert>;

    if (challenges.length === 0) {
        return (
            <div className="p-5 text-center text-light">
                <h2 className="h5 mb-3">Minden csendes...</h2>
                <p className="text-light">Jelenleg nincs egyetlen aktív kihívásod sem. Lépj a Barátaim fülre, és indíts egyet!</p>
            </div>
        );
    }

    return (
        <div className="p-3">
            <h2 className="h5 text-light mb-4">Aktív Kihívások ({challenges.length})</h2>
            <Row>
                {challenges.map((challenge) => {
                    const iAmChallenger = challenge.challengerName === user?.name;
                    return (
                        <Col md={12} key={challenge.challengeId} className="mb-3">
                            <Card className={`bg-dark border-${challenge.isMyTurn ? 'warning' : 'secondary'} shadow-sm`}>
                                <Card.Body className="d-flex flex-wrap gap-3 justify-content-between align-items-center">
                                    <div>
                                        <h3 className="h5 text-light mb-1 fw-bold">
                                            <span aria-hidden="true">{iAmChallenger ? '⚔️ ' : '🔥 '}</span>
                                            {iAmChallenger 
                                                ? `Kihívtad: ${challenge.opponentName}`
                                                : `${challenge.challengerName} kihívott téged!`}
                                        </h3>
                                        <p className="mb-1">Lecke: {challenge.lessonTitle || 'Kihívásfeladat'}</p>
                                        <div className="text-secondary small mt-1">
                                            Küldve: {formatToLocalDisplay(challenge.startTime)}
                                        </div>
                                    </div>
                                    <div className="d-flex flex-column align-items-end gap-2">
                                        <small className="text-danger">
                                            Lejár: {formatToLocalDisplay(challenge.expiresAt, false)} 
                                        </small>
                                        {challenge.isMyTurn && (
                                            <Button aria-label={`Játék! ${iAmChallenger ? challenge.opponentName : challenge.challengerName} ellen, ${challenge.lessonTitle || 'Kihívásfeladat'}`} variant="warning" className="fw-bold px-4" onClick={() => handlePlayChallenge(challenge)} >
                                                Játék! <span aria-hidden="true">🚀</span>
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
