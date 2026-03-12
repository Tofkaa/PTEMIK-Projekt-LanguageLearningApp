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

    return (
        <div className="min-vh-100 pb-5 text-light">
            {/* Navigation Bar */}
            <NavigationBar />

            <Container>
                {/* Welcome Header */}
                <Row className="mb-4">
                    <Col>
                        <h2 className="fw-bold">Welcome back, {user?.name}! 👋</h2>
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
                                <h5 className="fw-bold border-bottom pb-2 mb-3">Elérhető Leckék</h5>
                                
                                {/* Loading State */}
                                {isLoading && (
                                    <div className="text-center py-5">
                                        <Spinner animation="border" variant="primary" />
                                        <p className="mt-2 text-light">Leckék keresése...</p>
                                    </div>
                                )}

                                {/* Error State */}
                                {error && <Alert variant="danger">{error}</Alert>}

                                {/* Empty State */}
                                {!isLoading && !error && lessons.length === 0 && (
                                    <Alert variant="info">Jelenleg nincsenek elérhető leckék.</Alert>
                                )}

                                {/* Render Fetched Lessons */}
                                {!isLoading && !error && lessons.length > 0 && (
                                    <Row>
                                        {/* .map() iterates through the array and creates a UI card for each lesson */}
                                        {lessons.map((lesson) => (
                                            <Col md={6} key={lesson.id} className="mb-3">
                                                <Card className="h-100 border-0 shadow-sm bg-dark text-light">
                                                    <Card.Body>
                                                        <Card.Title className="fw-bold">{lesson.title}</Card.Title>
                                                        <Card.Text className="text-light small">
                                                            {lesson.description}
                                                        </Card.Text>
                                                        <div className="d-flex justify-content-between align-items-center mt-3">
                                                            <span className="badge bg-primary">{lesson.difficultyLevel}</span>
                                                            <Button variant="outline-success" size="sm" onClick={() => navigate(`/lesson/${lesson.id}`)}>
                                                                Indítás
                                                            </Button>
                                                        </div>
                                                    </Card.Body>
                                                </Card>
                                            </Col>
                                        ))}
                                    </Row>
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