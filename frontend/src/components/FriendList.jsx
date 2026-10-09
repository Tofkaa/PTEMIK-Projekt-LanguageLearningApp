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
import { useAuth } from '../context/AuthContext';

const AVAILABLE_COURSES = {
    'en': { name: 'Angol', flag: '🇬🇧' },
    'es': { name: 'Spanyol', flag: '🇪🇸' },
    'de': { name: 'Német', flag: '🇩🇪' }
};

/**
 * @component
 * @returns {React.ReactElement} A grid of friend cards with challenge initiation capabilities.
 */
const FriendList = () => {
    const navigate = useNavigate();
    const { user } = useAuth();
    const [friends, setFriends] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');
    const {notifications, refreshNotifications} = useNotifications();

    const [showModal, setShowModal] = useState(false);
    const [selectedFriend, setSelectedFriend] = useState(null);

    const [challengeCourse, setChallengeCourse] = useState(user?.activeCourseCode || 'en');
    const [lessons, setLessons] = useState([]);
    const [selectedLesson, setSelectedLesson] = useState('');
    const [fetchingLessons, setFetchingLessons] = useState(false);

    const [expiresIn, setExpiresIn] = useState(3);
    const [challengeLoading, setChallengeLoading] = useState(false);
    const [challengeError, setChallengeError] = useState('');

    useEffect(() => {
        fetchFriends(true);
    }, [notifications.totalFriends]);

    // Automatikusan frissítjük a leckék listáját, ha a modal nyitva van és nyelvet vált
    useEffect(() => {
        if (showModal) {
            fetchLessonsForChallenge(challengeCourse);
        }
    }, [challengeCourse, showModal]);

    const fetchFriends = async (isInitialLoad = false) => {
        if (isInitialLoad) setLoading(true);
        
        try {
            const response = await api.get('/friendships/accepted', {
                params: { _t: new Date().getTime() }
            });
            setFriends(response.data || []);
        } catch (err) {
            if (isInitialLoad) setError('Nem sikerült betölteni a barátlistát.', err);
        } finally {
            if (isInitialLoad) setLoading(false);
        }
    };

    const fetchLessonsForChallenge = async (courseCode) => {
        setFetchingLessons(true);
        try {

            const response = await api.get('/lessons/all-for-challenge', {
                params: { courseCode: courseCode }
            });
            
            const fetchedLessons = response.data || [];
            
            const filteredLessons = fetchedLessons.filter(l => 
                !l.language || l.language === courseCode || l.courseCode === courseCode
            );

            const displayLessons = filteredLessons.length > 0 ? filteredLessons : fetchedLessons;
            
            setLessons(displayLessons);
            
            if (displayLessons.length > 0) {
                setSelectedLesson(displayLessons[0].lessonId);
            } else {
                setSelectedLesson('');
            }
        } catch (err) {
            console.error("Nem sikerült lekérni a leckéket a kihíváshoz", err);
        } finally {
            setFetchingLessons(false);
        }
    };

    const handleOpenChallenge = (friend) => {
        setSelectedFriend(friend);
        setChallengeError('');
        // Alapértelmezetten a felhasználó aktív nyelvével indítjuk a modalt
        setChallengeCourse(user?.activeCourseCode || 'en');
        setShowModal(true);
    };

    const handleCloseModal = () => {
        setShowModal(false);
        setSelectedFriend(null);
    };

    /**
     * Initiates a new challenge by creating a DRAFT state in the backend.
     * Automatically redirects the user to the LessonPlayer upon success, attaching the bypass ID.
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

            const response = await api.post('/challenges/create', payload);
            const challengeId = response.data.challengeId;

            if (refreshNotifications) {
                refreshNotifications();
            }
            setShowModal(false);

            navigate(`/lesson/${selectedLesson}?challengeId=${challengeId}`);

        } catch (err) {
            setChallengeError(err.response?.data?.message || 'Hiba történt a kihívás indításakor.');
        } finally {
            setChallengeLoading(false);
        }
    };

    const handleRemoveFriend = async (friendshipId, friendName) => {
        if (window.confirm(`Biztosan törlöd ${friendName} felhasználót a barátaid közül?`)) {
            try {
                await api.delete(`/friendships/${friendshipId}`);
                fetchFriends();
            } catch (err) {
                console.error("Hiba a barát törlésekor:", err);
                setChallengeError('Nem sikerült törölni a barátot.');
            }
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
                                        onClick={() => handleRemoveFriend(friend.friendshipId, friend.name)}
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
                        {/* 1. LÉPÉS: NYELV KIVÁLASZTÁSA */}
                        <Form.Group className="mb-3">
                            <Form.Label className="text-muted fw-bold">1. Milyen nyelven szeretnél párbajozni?</Form.Label>
                            <Form.Select 
                                className="bg-secondary text-light border-secondary shadow-none"
                                value={challengeCourse}
                                onChange={(e) => setChallengeCourse(e.target.value)}
                                disabled={challengeLoading || fetchingLessons}
                            >
                                {Object.entries(AVAILABLE_COURSES).map(([code, data]) => (
                                    <option key={code} value={code}>
                                        {data.flag} {data.name}
                                    </option>
                                ))}
                            </Form.Select>
                        </Form.Group>

                        {/* 2. LÉPÉS: LECKE KIVÁLASZTÁSA */}
                        <Form.Group className="mb-3">
                            <Form.Label className="text-muted fw-bold">2. Melyik leckéből hívod ki?</Form.Label>
                            <Form.Select 
                                className="bg-secondary text-light border-secondary shadow-none"
                                value={selectedLesson}
                                onChange={(e) => setSelectedLesson(e.target.value)}
                                disabled={challengeLoading || fetchingLessons || lessons.length === 0}
                            >
                                {fetchingLessons ? (
                                    <option value="">Leckék betöltése...</option>
                                ) : lessons.length === 0 ? (
                                    <option value="">-- Nincs elérhető lecke ezen a nyelven --</option>
                                ) : (
                                    lessons.map(lesson => (
                                        <option key={lesson.lessonId} value={lesson.lessonId}>
                                            {lesson.title} ({lesson.difficulty})
                                        </option>
                                    ))
                                )}
                            </Form.Select>
                        </Form.Group>

                        {/* 3. LÉPÉS: LEJÁRATI IDŐ */}
                        <Form.Group className="mb-4">
                            <Form.Label className="text-muted fw-bold">3. Meddig érvényes a kihívás?</Form.Label>
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
                    <Button variant="info" className="fw-bold" onClick={handleStartChallenge} disabled={challengeLoading || fetchingLessons || lessons.length === 0}>
                        {challengeLoading ? 'Készülés...' : 'Játék Indítása! 🚀'}
                    </Button>
                </Modal.Footer>
            </Modal>
        </div>
    );
};

export default FriendList;