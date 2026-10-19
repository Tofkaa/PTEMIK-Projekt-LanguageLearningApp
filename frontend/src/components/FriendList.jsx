/**
 * @file FriendList.jsx
 * @description Displays the user's accepted friends and acts as the entry point for initiating new Challenges.
 * Manages the modal state and submission logic for Draft Challenge creation.
 */

import { useState, useEffect, useRef } from 'react';
import { Card, Button, Spinner, Alert, Badge, Row, Col, Modal, Form } from 'react-bootstrap';
import { useNavigate } from 'react-router-dom';
import api from '../services/api';
import { useNotifications } from '../context/NotificationContext';
import { useAuth } from '../context/AuthContext';
import LearningFeedback from './LearningFeedback';

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
    const [challengeDifficulty, setChallengeDifficulty] = useState('MEDIUM');
    const [lessons, setLessons] = useState([]);
    const [selectedLesson, setSelectedLesson] = useState('');
    const [fetchingLessons, setFetchingLessons] = useState(false);

    const [expiresIn, setExpiresIn] = useState(3);
    const [challengeLoading, setChallengeLoading] = useState(false);
    const [challengeError, setChallengeError] = useState('');
    const [removeError, setRemoveError] = useState('');
    const [removingId, setRemovingId] = useState(null);
    const listHeadingRef = useRef(null);
    const restoreListFocus = useRef(false);
    const lessonRequestId = useRef(0);

    useEffect(() => {
        if (restoreListFocus.current) {
            listHeadingRef.current?.focus();
            restoreListFocus.current = false;
        }
    }, [friends]);

   const isFirstLoad = useRef(true);

    useEffect(() => {
        fetchFriends(isFirstLoad.current);
        isFirstLoad.current = false;
    }, [notifications.totalFriends]);

    useEffect(() => {
        if (showModal) {
            fetchLessonsForChallenge(challengeCourse);
        }
    }, [challengeCourse, showModal]);

    const fetchFriends = async (isInitialLoad = false) => {
        if (isInitialLoad) setLoading(true);
        
        try {
            const response = await api.get('/friendships/accepted', {
                params: { _t: Date.now() }
            });
            setFriends(response.data || []);
        } catch (err) {
            console.error("Hiba a barátlista betöltésekor:", err);
            if (isInitialLoad) setError('Nem sikerült betölteni a barátlistát.');
        } finally {
            if (isInitialLoad) setLoading(false);
        }
    };

    const fetchLessonsForChallenge = async (courseCode) => {
        const requestId = ++lessonRequestId.current;
        setFetchingLessons(true);
        try {

            const response = await api.get('/lessons/all-for-challenge', {
                params: { courseCode: courseCode }
            });
            if (requestId !== lessonRequestId.current) return;
            
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
            if (requestId !== lessonRequestId.current) return;
            console.error("Nem sikerült lekérni a leckéket a kihíváshoz", err);
            setLessons([]);
            setSelectedLesson('');
            setChallengeError('Nem sikerült betölteni a leckéket. Zárd be az ablakot, és próbáld újra!');
        } finally {
            if (requestId === lessonRequestId.current) setFetchingLessons(false);
        }
    };

    const handleOpenChallenge = (friend) => {
        setSelectedFriend(friend);
        setChallengeError('');
        setLessons([]);
        setSelectedLesson('');
        setFetchingLessons(true);
        // Alapértelmezetten a felhasználó aktív nyelvével indítjuk a modalt
        setChallengeCourse(user?.activeCourseCode || 'en');
        setShowModal(true);
    };

    const handleCloseModal = () => {
        if (challengeLoading) return;
        lessonRequestId.current += 1;
        setShowModal(false);
        setSelectedFriend(null);
    };

    /**
     * Initiates a new challenge by creating a DRAFT state in the backend.
     * Automatically redirects the user to the LessonPlayer upon success, attaching the bypass ID.
     */
    const handleStartChallenge = async () => {
        if (challengeLoading || fetchingLessons) return;
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
                expiresInDays: expiresIn,
                difficulty: challengeDifficulty
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

    const handleRemoveFriend = async (friendshipId, friendName, button) => {
        if (removingId) return;
        if (window.confirm(`Biztosan törlöd ${friendName} felhasználót a barátaid közül?`)) {
            setRemovingId(friendshipId);
            setRemoveError('');
            try {
                await api.delete(`/friendships/${friendshipId}`);
                restoreListFocus.current = !!button.closest('.card')?.contains(document.activeElement);
                setFriends(previous => previous.filter(friend => friend.friendshipId !== friendshipId));
                refreshNotifications?.();
            } catch (err) {
                console.error("Hiba a barát törlésekor:", err);
                setRemoveError('Nem sikerült törölni a barátot. Próbáld újra!');
            } finally {
                setRemovingId(null);
            }
        }
    };
    
    if (loading) return <div role="status" className="text-center p-4"><Spinner animation="border" variant="info" aria-hidden="true" /> Barátlista betöltése…</div>;
    if (error) return <Alert variant="danger">{error}</Alert>;

    if (friends.length === 0) {
        return (
            <div className="p-5 text-center text-light">
                <h2 ref={listHeadingRef} tabIndex={-1} className="h5 mb-3">Még nincsenek barátaid a listán.</h2>
                <p>Lépj át a Keresés fülre, és jelölj be valakit a kódja alapján!</p>
            </div>
        );
    }

    return (
        <div className="p-3">
            <LearningFeedback error={removeError} />
            <h2 ref={listHeadingRef} tabIndex={-1} className="h5 text-light mb-4">Barátaim ({friends.length})</h2>
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
                                        aria-label={`Kihívás: ${friend.name}#${friend.userTag}`}
                                        onClick={() => handleOpenChallenge(friend)}
                                    >
                                        <span aria-hidden="true">⚔️ </span>Kihívás
                                    </Button>
                                    <Button 
                                        variant="outline-danger" 
                                        size="sm"
                                        title="Barát törlése"
                                        aria-label={`Barát törlése: ${friend.name}#${friend.userTag}`}
                                        aria-disabled={!!removingId}
                                        aria-busy={removingId === friend.friendshipId}
                                        onClick={(event) => handleRemoveFriend(friend.friendshipId, friend.name, event.currentTarget)}
                                    >
                                        <span aria-hidden="true">✖</span>
                                    </Button>
                                </div>
                            </Card.Body>
                        </Card>
                    </Col>
                ))}
            </Row>

            {/* --- A FELUGRÓ ABLAK (MODAL) --- */}
            <Modal show={showModal} onHide={handleCloseModal} aria-labelledby="challenge-modal-title" centered className="text-light">
                <Modal.Header closeButton closeLabel="Kihívás bezárása" className="border-secondary bg-dark">
                    <Modal.Title id="challenge-modal-title" className="fw-bold">
                        <span aria-hidden="true">⚔️ </span>Kihívod: <span className="text-info">{selectedFriend?.name}</span>
                    </Modal.Title>
                </Modal.Header>
                <Modal.Body className="bg-dark">
                    <LearningFeedback error={challengeError} />
                    
                    <Form>
                        {/* 1. LÉPÉS: NYELV KIVÁLASZTÁSA */}
                        <Form.Group controlId="challenge-course" className="mb-3">
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

                        {/* 2. LÉPÉS: NEHÉZSÉG KIVÁLASZTÁSA */}
                        <Form.Group controlId="challenge-difficulty" className="mb-3">
                            <Form.Label className="text-muted fw-bold">2. Milyen nehézségi szinten párbajozzatok?</Form.Label>
                            <Form.Select 
                                className="bg-secondary text-light border-secondary shadow-none"
                                value={challengeDifficulty}
                                onChange={(e) => setChallengeDifficulty(e.target.value)}
                                disabled={challengeLoading}
                            >
                                <option value="EASY">Könnyű (EASY)</option>
                                <option value="MEDIUM">Közepes (MEDIUM)</option>
                                <option value="HARD">Nehéz (HARD)</option>
                            </Form.Select>
                        </Form.Group>

                        {/* 3. LÉPÉS: LECKE KIVÁLASZTÁSA */}
                        <Form.Group controlId="challenge-lesson" className="mb-3">
                            <Form.Label className="text-muted fw-bold">3. Melyik leckéből hívod ki?</Form.Label>
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
                                            {lesson.title}
                                        </option>
                                    ))
                                )}
                            </Form.Select>
                        </Form.Group>

                        {/* 4. LÉPÉS: LEJÁRATI IDŐ */}
                        <Form.Group controlId="challenge-expiry" className="mb-4">
                            <Form.Label className="text-muted fw-bold">4. Meddig érvényes a kihívás?</Form.Label>
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

                    <Alert role="note" variant="warning" className="mb-0 fw-bold border-0 shadow-sm">
                        <span aria-hidden="true">⚠️ </span>A kihívás elküldéséhez neked is azonnal le kell játszanod ezt a leckét! Ne indítsd el, ha most nem érsz rá!
                    </Alert>

                </Modal.Body>
                <Modal.Footer className="border-secondary bg-dark">
                    <Button variant="secondary" onClick={handleCloseModal} disabled={challengeLoading}>
                        Mégse
                    </Button>
                    <Button variant="info" className="fw-bold" onClick={handleStartChallenge} disabled={fetchingLessons || lessons.length === 0} aria-disabled={challengeLoading} aria-busy={challengeLoading}>
                        Játék Indítása! <span aria-hidden="true">🚀</span>
                    </Button>
                </Modal.Footer>
            </Modal>
        </div>
    );
};

export default FriendList;
