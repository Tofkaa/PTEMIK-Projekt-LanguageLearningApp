import { useState, useEffect } from 'react';
import { Container, Row, Col, Card, Button, Spinner, Alert, ProgressBar } from 'react-bootstrap';
import { useAuth } from '../context/AuthContext.jsx';
import NavigationBar from '../components/NavigationBar.jsx';
import api from '../services/api.jsx';
import { useNavigate } from 'react-router-dom';


/**
 * Dashboard Component
 * The main landing page for authenticated users.
 * Displays user statistics and fetches available lessons from the backend.
 */
const Dashboard = () => {
    // 1. Retrieve the authenticated user's data from the global context
    const { user } = useAuth();

    // 2. Component-level states for managing the lessons data, loading state, and errors
    const [lessons, setLessons] = useState([]);
    const [isLoading, setIsLoading] = useState(true);
    const [error, setError] = useState('');
    const navigate = useNavigate();

    const currentXp = user?.xp || 0;
    const currentLevel = Math.floor(currentXp / 100) + 1; 
    const xpForNextLevel = currentLevel * 100;
    const xpInCurrentLevel = currentXp % 100;
    const progressPercentage = (xpInCurrentLevel / 100) * 100;

    /**
     * useEffect Hook
     * Runs exactly once when the Dashboard component mounts.
     * Fetches the list of available lessons from the Spring Boot API.
     */
    useEffect(() => {
        const fetchLessons = async () => {
            try {
                // Send GET request to the backend to retrieve lessons
                const response = await api.get('/lessons');
                console.log("Lessons fetched successfully:", response.data);
                
                // Update the state with the fetched array of lessons
                setLessons(response.data);
            } catch (err) {
                console.error("Error fetching lessons:", err);
                setError('Nem sikerült betölteni a leckéket a szerverről.');
            } finally {
                // Turn off the loading spinner regardless of success or failure
                setIsLoading(false);
            }
        };

        // Trigger the asynchronous fetch function
        fetchLessons();
    }, []); // The empty array [] means this runs only once on mount

    const groupedLessons = lessons.reduce((acc, lesson) => {
        // Ha valamiért hiányozna a topicName, "Egyéb" kategóriába tesszük
        const topic = lesson.topicName || "Egyéb Témakörök";
        
        if (!acc[topic]) {
            acc[topic] = [];
        }
        acc[topic].push(lesson);
        
        return acc;
    }, {});

    const getDifficultyBadge = (difficulty) => {
        switch (difficulty) {
            case 'HARD': return <span className="badge bg-danger">HARD</span>;
            case 'MEDIUM': return <span className="badge bg-warning text-dark">MEDIUM</span>;
            case 'EASY': return <span className="badge bg-success">EASY</span>;
            default: return <span className="badge bg-primary">{difficulty}</span>;
        }
    };

    return (
        <div className="min-vh-100 pb-5 text-light">
            {/* Navigation Bar */}
            <NavigationBar />

            <Container>
                {/* Welcome Header */}
                <Row className="mb-4">
                    <Col>
                        <h2 className="fw-bold">Üdv újra, {user?.name}! 👋</h2>
                        <p className="text-light">
                            Készen állsz a mai tanulásra? Itt a te személyre szabott áttekintésed.
                        </p>
                    </Col>
                </Row>

                <Row>
                    {/* Left Column: User Statistics Card */}
                   <Col md={4} className="mb-4">
                       <Card className="shadow-sm border-0 h-100 bg-transparent text-light">
                            <Card.Body>
                                <h5 className="fw-bold border-bottom pb-2">Statisztikák</h5>
                                
                                {/* XP and Level Bar */}
                                <div className="mb-4 mt-3 p-3 bg-dark rounded border border-secondary">
                                    <div className="d-flex justify-content-between mb-1">
                                        <span className="fw-bold text-primary">Szint {currentLevel}</span>
                                        <span className="text-muted small fw-bold">
                                            {currentXp} / {xpForNextLevel} XP
                                        </span>
                                    </div>
                                    <ProgressBar 
                                        now={progressPercentage} 
                                        variant="warning" 
                                        className="rounded-pill shadow-sm" 
                                        style={{ height: '12px' }} 
                                        animated={progressPercentage > 0}
                                    />
                                    <div className="text-center mt-2 small text-light">
                                        Még {xpForNextLevel - currentXp} XP a szintlépéshez!
                                    </div>
                                </div>

                                <p className="mb-1"><strong>Szerepkör:</strong> <span className="badge bg-secondary">{user?.role}</span></p>
                                <p className="mb-1"><strong>Email:</strong> {user?.email}</p>
                            </Card.Body>
                        </Card>
                    </Col>

                    {/* Right Column: Lessons List */}
                    <Col md={8} className="mb-4">
                        <Card className="shadow-sm border-0 h-100 bg-transparent text-light">
                            <Card.Body className="p-0">
                                <h5 className="fw-bold border-bottom border-secondary pb-2 mb-4">📚 Tanulási Útvonal</h5>
                                
                                {/* Loading / Error / Empty States */}
                                {isLoading && (
                                    <div className="text-center py-5">
                                        <Spinner animation="border" variant="info" />
                                        <p className="mt-2 text-light opacity-75">Tananyagok betöltése...</p>
                                    </div>
                                )}
                                {error && <Alert variant="danger">{error}</Alert>}
                                {!isLoading && !error && lessons.length === 0 && (
                                    <Alert variant="info" className="bg-dark text-info border-info">Jelenleg nincsenek elérhető leckék a szinteden.</Alert>
                                )}

                                {/* --- GROUPED DISPLAY --- */}
                                {!isLoading && !error && Object.keys(groupedLessons).length > 0 && (
                                    <div className="d-flex flex-column gap-5">
                                        {/* Iterate through topics */}
                                        {Object.entries(groupedLessons).map(([topicName, topicLessons]) => (
                                            <div key={topicName}>
                                                {/* Topic header */}
                                                <h4 className="text-info fw-bold mb-3 px-2">
                                                    {topicName}
                                                </h4>
                                                
                                                {/* Grid for the topic's lessons*/}
                                                <Row className="g-3">
                                                    {topicLessons.map((lesson) => (
                                                        <Col md={6} key={lesson.lessonId}> 
                                                            <Card className="h-100 border border-secondary shadow-sm bg-dark text-light" style={{ transition: 'transform 0.2s', cursor: 'pointer' }} onMouseEnter={(e) => e.currentTarget.style.transform = 'translateY(-5px)'} onMouseLeave={(e) => e.currentTarget.style.transform = 'translateY(0)'}>
                                                                <Card.Body className="d-flex flex-column p-4">
                                                                    <Card.Title className="fw-bold mb-2">{lesson.title}</Card.Title>
                                                                    <Card.Text className="text-light opacity-50 small mb-4 flex-grow-1">
                                                                        {lesson.description}
                                                                    </Card.Text>
                                                                    <div className="d-flex justify-content-between align-items-center mt-auto pt-3 border-top border-secondary border-opacity-50">
                                                                        {getDifficultyBadge(lesson.difficulty)}
                                                                        <Button 
                                                                            variant="outline-info" 
                                                                            size="sm" 
                                                                            className="rounded-pill px-4 fw-bold"
                                                                            onClick={() => navigate(`/lesson/${lesson.lessonId}`)}
                                                                        > 
                                                                            Indítás
                                                                        </Button>
                                                                    </div>
                                                                </Card.Body>
                                                            </Card>
                                                        </Col>
                                                    ))}
                                                </Row>
                                            </div>
                                        ))}
                                    </div>
                                )}
                            </Card.Body>
                        </Card>
                    </Col>
                </Row>
            </Container>
        </div>
    );
};

export default Dashboard;