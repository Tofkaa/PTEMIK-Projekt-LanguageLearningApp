/**
 * @file FriendList.jsx
 * @description Displays the user's accepted friends and acts as the entry point for initiating new Challenges.
 * Manages the modal state and submission logic for Draft Challenge creation.
 */

import { useState, useEffect } from 'react';
import { Card, Button, Spinner, Alert, Badge, Row, Col, Modal, Form } from 'react-bootstrap';
import { useNavigate } from 'react-router-dom';
import api from '../services/api';
import { useNotifications } from '../context/NotificationContext';

/**
 * @component
 * @returns {React.ReactElement} A grid of friend cards with challenge initiation capabilities.
 */
const FriendList = () => {
    const navigate = useNavigate();
    const [friends, setFriends] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');
    const {notifications} = useNotifications();

    const [showModal, setShowModal] = useState(false);
    const [selectedFriend, setSelectedFriend] = useState(null);
    const [lessons, setLessons] = useState([]);
    const [selectedLesson, setSelectedLesson] = useState('');
    const [expiresIn, setExpiresIn] = useState(3);
    const [challengeLoading, setChallengeLoading] = useState(false);
    const [challengeError, setChallengeError] = useState('');

    useEffect(() => {
        fetchFriends(true);
        fetchLessons(); // Prepare lessons for selection 
    }, [notifications.totalFriends]);

    const fetchFriends = async (isInitialLoad = false) => {
        if (isInitialLoad) setLoading(true);
        
        try {
            const response = await api.get('/friendships/accepted');
            setFriends(response.data || []);
        } catch (err) {
            if (isInitialLoad) setError('Nem sikerült betölteni a barátlistát.', err);
        } finally {
            if (isInitialLoad) setLoading(false);
        }
    };

    const fetchLessons = async () => {
        try {
            const response = await api.get('/lessons/all-for-challenge');
            setLessons(response.data || []);
        } catch (err) {
            console.error("Nem sikerült lekérni a leckéket a kihíváshoz", err);
        }
    };

    const handleOpenChallenge = (friend) => {
        setSelectedFriend(friend);
        setChallengeError('');
        if (lessons.length > 0) {
            setSelectedLesson(lessons[0].lessonId);
        }
        setShowModal(true);
    };

    const handleCloseModal = () => {
        setShowModal(false);
        setSelectedFriend(null);
    };

    /**
     * Initiates a new challenge by creating a DRAFT state in the backend.
     * Automatically redirects the user to the LessonPlayer upon success, attaching the bypass ID.
     * * @async
     * @function handleStartChallenge
     * @throws Will display an error alert if the API request fails.
     */
    const handleStartChallenge = async () => {
        if (!selectedLesson) {
            setChallengeError("Kérlek, válassz egy leckét!");
            return;
        }

        setChallengeLoading(true);
        setChallengeError('');

        try {
            const payload = {
                opponentId: selectedFriend.friendId,
                lessonId: selectedLesson,
                expiresInDays: expiresIn
            };

            // 1. Create the DRAFT challenge via backend API
            const response = await api.post('/challenges/create', payload);
            const challengeId = response.data.challengeId;

            // 2. Clean up modal state
            setShowModal(false);

            // 3. Redirect to the Lesson Player with the bypass challengeId attached to the URL
            navigate(`/lesson/${selectedLesson}?challengeId=${challengeId}`);

        } catch (err) {
            setChallengeError(err.response?.data?.message || 'Hiba történt a kihívás indításakor.');
        } finally {
            setChallengeLoading(false);
        }
    };
    
    if (loading) return <div className="text-center p-4"><Spinner animation="border" variant="info" /></div>;
    if (error) return <Alert variant="danger">{error}</Alert>;

    if (friends.length === 0) {
        return (
            <div className="p-5 text-center text-light">
                <h5 className="mb-3">Még nincsenek barátaid a listán.</h5>
                <p>Lépj át a Keresés fülre, és jelölj be valakit a kódja alapján!</p>
            </div>
        );
    }

    return (
        <div className="p-3">
            <h5 className="text-light mb-4">Barátaim ({friends.length})</h5>
            <Row>
                {friends.map((friend) => (
                    <Col md={6} key={friend.friendshipId} className="mb-3">
                        <Card className="bg-dark border-secondary h-100 shadow-sm friend-card">
                            <Card.Body className="d-flex flex-column justify-content-between">
                                <div className="d-flex justify-content-between align-items-start mb-3">
                                    <div>
                                        <h5 className="text-light mb-0 fw-bold">
                                            {friend.name}
                                        </h5>
                                        <div className="text-light small">
                                            #{friend.userTag}
                                        </div>
                                    </div>
                                    <Badge bg="secondary" text="light" className="font-monospace">
                                        {friend.friendCode}
                                    </Badge>
                                </div>
                                
                                <div className="d-flex gap-2 mt-auto">
                                    {/* MOST MÁR AKTÍV A GOMB! */}
                                    <Button 
                                        variant="outline-info" 
                                        size="sm" 
                                        className="w-100 fw-bold"
                                        onClick={() => handleOpenChallenge(friend)}
                                    >
                                        ⚔️ Kihívás
                                    </Button>
                                    <Button 
                                        variant="outline-danger" 
                                        size="sm"
                                        title="Barát törlése"
                                        onClick={() => console.log('Törlés...', friend.friendshipId)}
                                    >
                                        ✖
                                    </Button>
                                </div>
                            </Card.Body>
                        </Card>
                    </Col>
                ))}
            </Row>

            {/* --- A FELUGRÓ ABLAK (MODAL) --- */}
            <Modal show={showModal} onHide={handleCloseModal} centered data-bs-theme="dark" className="text-light">
                <Modal.Header closeButton className="border-secondary bg-dark">
                    <Modal.Title className="fw-bold">
                        ⚔️ Kihívod: <span className="text-info">{selectedFriend?.name}</span>
                    </Modal.Title>
                </Modal.Header>
                <Modal.Body className="bg-dark">
                    {challengeError && <Alert variant="danger">{challengeError}</Alert>}
                    
                    <Form>
                        <Form.Group className="mb-3">
                            <Form.Label className="text-muted fw-bold">1. Melyik leckéből hívod ki?</Form.Label>
                            <Form.Select 
                                className="bg-secondary text-light border-secondary shadow-none"
                                value={selectedLesson}
                                onChange={(e) => setSelectedLesson(e.target.value)}
                                disabled={challengeLoading}
                            >
                                {lessons.map(lesson => (
                                    <option key={lesson.lessonId} value={lesson.lessonId}>
                                        {lesson.title} ({lesson.difficulty})
                                    </option>
                                ))}
                            </Form.Select>
                        </Form.Group>

                        <Form.Group className="mb-4">
                            <Form.Label className="text-muted fw-bold">2. Meddig érvényes a kihívás?</Form.Label>
                            <Form.Select 
                                className="bg-secondary text-light border-secondary shadow-none"
                                value={expiresIn}
                                onChange={(e) => setExpiresIn(parseInt(e.target.value))}
                                disabled={challengeLoading}
                            >
                                <option value={1}>1 nap</option>
                                <option value={2}>2 nap</option>
                                <option value={3}>3 nap</option>
                                <option value={4}>4 nap</option>
                                <option value={5}>5 nap</option>
                                <option value={6}>6 nap</option>
                                <option value={7}>7 nap</option>
                            </Form.Select>
                        </Form.Group>
                    </Form>

                    <Alert variant="warning" className="mb-0 text-light fw-bold border-0 shadow-sm">
                        ⚠️ A kihívás elküldéséhez neked is azonnal le kell játszanod ezt a leckét! Ne indítsd el, ha most nem érsz rá!
                    </Alert>

                </Modal.Body>
                <Modal.Footer className="border-secondary bg-dark">
                    <Button variant="secondary" onClick={handleCloseModal} disabled={challengeLoading}>
                        Mégse
                    </Button>
                    <Button variant="info" className="fw-bold" onClick={handleStartChallenge} disabled={challengeLoading || lessons.length === 0}>
                        {challengeLoading ? 'Készülés...' : 'Játék Indítása! 🚀'}
                    </Button>
                </Modal.Footer>
            </Modal>
        </div>
    );
};

export default FriendList;