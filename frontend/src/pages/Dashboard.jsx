import { useState, useEffect } from 'react';
import { Container, Row, Col, Card, Button, Spinner, Alert, ProgressBar } from 'react-bootstrap';
import { useAuth } from '../context/AuthContext.jsx';
import NavigationBar from '../components/NavigationBar.jsx';
import api from '../services/api.jsx';
import { useNavigate } from 'react-router-dom';

/**
 * Dashboard Component
 * The central hub for authenticated users. 
 * Orchestrates user statistics, course progress, and dynamically fetches and groups the learning path.
 */
const Dashboard = () => {
    // --- CONTEXT & ROUTING ---
    const { user } = useAuth();
    const navigate = useNavigate();

    // --- LOCAL STATE ---
    const [lessons, setLessons] = useState([]);
    const [isLoading, setIsLoading] = useState(true);
    const [error, setError] = useState('');

    // --- DERIVED STATE: XP & LEVEL CALCULATIONS ---
    // Instead of storing these in separate state variables (which could lead to desyncs),
    // we derive them on-the-fly from the single source of truth: the `user` context.
    const currentXp = user?.xp || 0;
    const currentLevel = Math.floor(currentXp / 100) + 1; 
    const xpForNextLevel = currentLevel * 100;
    const xpInCurrentLevel = currentXp % 100;
    const progressPercentage = (xpInCurrentLevel / 100) * 100;

  // --- DERIVED STATE: LESSON PROGRESS CALCULATIONS ---
    const totalLessonsCount = lessons.length;
    const completedLessonsCount = lessons.filter(lesson => lesson.completed).length;
    const courseProgressPercentage = totalLessonsCount === 0 ? 0 : (completedLessonsCount / totalLessonsCount) * 100;

    // --- DERIVED STATE: HERO WIDGET LOGIC ---
    let heroState = null;

    if (!isLoading && !error && totalLessonsCount > 0) {
        // Megkeressük a legelső befejezetlen leckét
        const nextLesson = lessons.find(lesson => !lesson.completed);

        if (!nextLesson && completedLessonsCount > 0) {
            // Everything completed
            const randomLesson = lessons[Math.floor(Math.random() * totalLessonsCount)];
            heroState = {
                type: 'ALL_DONE',
                title: 'Minden elérhető leckét teljesítettél! 🎉',
                subtitle: 'Fantasztikus munka! Frissítsd fel a tudásod egy korábbi lecke ismétlésével.',
                lesson: randomLesson,
                buttonText: 'Tudás felfrissítése 🔄',
                buttonVariant: 'warning',
                borderColor: 'border-warning'
            };
        } else if (completedLessonsCount === 0 && nextLesson) {
            // No completed lessons
            heroState = {
                type: 'FIRST_STEPS',
                title: 'Készen állsz az első kalandra? 🚀',
                subtitle: `Kezdd el a nyelvtanulást a(z) "${nextLesson.title}" leckével!`,
                lesson: nextLesson,
                buttonText: 'Első lecke indítása',
                buttonVariant: 'success',
                borderColor: 'border-success'
            };
        } else if (nextLesson) {
            // Normal progress
            heroState = {
                type: 'CONTINUE',
                title: 'Folytasd a tanulást! 📚',
                subtitle: `A következő logikus lépés: ${nextLesson.title} (${nextLesson.topicName || 'Egyéb'})`,
                lesson: nextLesson,
                buttonText: 'Folytatás ➡️',
                buttonVariant: 'info',
                borderColor: 'border-info'
            };
        }
    }

    /**
     * Component Lifecycle: Initialization
     */
    useEffect(() => {
        const fetchLessons = async () => {
            try {
                const response = await api.get('/lessons');
                console.log("Lessons fetched successfully:", response.data);
                setLessons(response.data);
            } catch (err) {
                console.error("Error fetching lessons:", err);
                setError('Nem sikerült betölteni a leckéket a szerverről.');
            } finally {
                setIsLoading(false);
            }
        };

        fetchLessons();
    }, []); 

    // --- DATA TRANSFORMATION ---
    const groupedLessons = lessons.reduce((acc, lesson) => {
        const topic = lesson.topicName || "Egyéb Témakörök";
        if (!acc[topic]) {
            acc[topic] = [];
        }
        acc[topic].push(lesson);
        return acc;
    }, {});

    /**
     * UI Helper: Generates a color-coded Bootstrap badge based on difficulty.
     */
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
            <NavigationBar />

            <Container>
                {/* --- HEADER SECTION --- */}
                <Row className="mb-4">
                    <Col>
                        <h2 className="fw-bold">Üdv újra, {user?.name}! 👋</h2>
                        <p className="text-light opacity-75">
                            Készen állsz a mai tanulásra? Itt a te személyre szabott áttekintésed.
                        </p>
                    </Col>
                </Row>
                {/* --- HERO WIDGET SECTION --- */}
                {heroState && (
                    <Row className="mb-4">
                        <Col>
                            <Card className={`border-2 ${heroState.borderColor} bg-dark bg-gradient shadow-lg`}>
                                <Card.Body className="d-flex flex-column flex-md-row align-items-md-center justify-content-between p-4 p-md-5">
                                    <div className="mb-4 mb-md-0">
                                        <h3 className="fw-bold text-light mb-2">{heroState.title}</h3>
                                        <p className="text-light opacity-75 fs-5 mb-0">
                                            {heroState.subtitle}
                                        </p>
                                    </div>
                                    <div className="text-md-end">
                                        <Button 
                                            variant={heroState.buttonVariant} 
                                            size="lg" 
                                            className="px-5 py-3 rounded-pill fw-bold shadow-sm"
                                            style={{ minWidth: '250px' }}
                                            onClick={() => navigate(`/lesson/${heroState.lesson.lessonId}`)}
                                        >
                                            {heroState.buttonText}
                                        </Button>
                                    </div>
                                </Card.Body>
                            </Card>
                        </Col>
                    </Row>
                )}

                <Row>
                    {/* --- LEFT COLUMN: USER STATISTICS --- */}
                   <Col md={4} className="mb-4">
                       <Card className="shadow-sm border-0 h-100 bg-transparent text-light">
                            <Card.Body>
                                <h5 className="fw-bold border-bottom border-secondary pb-2">Statisztikák</h5>
                                
                                <div className="mb-4 mt-3 p-3 bg-dark rounded border border-secondary">
                                    
                                    {/* 1. XP Progress Bar */}
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
                                    />
                                    <div className="text-center mt-2 mb-4 small text-light opacity-75">
                                        Még {xpForNextLevel - currentXp} XP a szintlépéshez!
                                    </div>

                                    {/* 2. Course Completion Progress Bar */}
                                    <div className="d-flex justify-content-between align-items-center mt-3 p-2 bg-black bg-opacity-25 rounded border border-secondary">
                                        <span className="fw-bold text-success fs-5">📚 Haladás</span>
                                        <span className="text-light fw-bold fs-5">
                                            {completedLessonsCount} / {totalLessonsCount}
                                        </span>
                                    </div>
                                    <ProgressBar 
                                        now={courseProgressPercentage} 
                                        variant="success" 
                                        className="rounded-pill shadow-sm mt-2 mb-3" 
                                        style={{ height: '8px' }} 
                                    />

                                    {/* 3. Daily Gamification Streak */}
                                    <div className="d-flex justify-content-between align-items-center mt-3 p-2 bg-black bg-opacity-25 rounded border border-secondary">
                                        <span className="fw-bold text-warning fs-5">🔥 Napi sorozat</span>
                                        <span className="text-light fw-bold fs-5">
                                            {user?.streak || 0} nap
                                        </span>
                                    </div>
                                </div>

                                {/* User Metadata */}
                                <p className="mb-1"><strong>Szerepkör:</strong> <span className="badge bg-secondary">{user?.role}</span></p>
                                <p className="mb-1"><strong>Email:</strong> {user?.email}</p>
                            </Card.Body>
                        </Card>
                    </Col>

                    {/* --- RIGHT COLUMN: LEARNING PATH (LESSONS GRID) --- */}
                    <Col md={8} className="mb-4">
                        <Card className="shadow-sm border-0 h-100 bg-transparent text-light">
                            <Card.Body className="p-0">
                                <h5 className="fw-bold border-bottom border-secondary pb-2 mb-4">📚 Tanulási Útvonal</h5>
                                
                                {/* UI State: Loading */}
                                {isLoading && (
                                    <div className="text-center py-5">
                                        <Spinner animation="border" variant="info" />
                                    </div>
                                )}
                                
                                {/* UI State: Error */}
                                {error && <Alert variant="danger">{error}</Alert>}

                                {/* UI State: Data Render */}
                                {!isLoading && !error && Object.keys(groupedLessons).length > 0 && (
                                    <div className="d-flex flex-column gap-5">
                                        {/* Dynamic iteration over grouped topics */}
                                        {Object.entries(groupedLessons).map(([topicName, topicLessons]) => (
                                            <div key={topicName}>
                                                <h4 className="text-info fw-bold mb-3 px-2">
                                                    {topicName}
                                                </h4>
                                                
                                                <Row className="g-3">
                                                    {topicLessons.map((lesson) => (
                                                        <Col md={6} key={lesson.lessonId}> 
                                                            {/* UI Enhancement: Dynamic Card Styling
                                                              Completed lessons get a success border and a green visual cue.
                                                            */}
                                                            <Card 
                                                                className={`h-100 border shadow-sm bg-dark text-light ${lesson.completed ? 'border-success' : 'border-secondary'}`} 
                                                                style={{ transition: 'transform 0.2s', cursor: 'pointer' }} 
                                                                onMouseEnter={(e) => e.currentTarget.style.transform = 'translateY(-5px)'} 
                                                                onMouseLeave={(e) => e.currentTarget.style.transform = 'translateY(0)'}
                                                            >
                                                                <Card.Body className="d-flex flex-column p-4">
                                                                    <Card.Title className="fw-bold mb-2 d-flex justify-content-between align-items-start">
                                                                        {lesson.title}
                                                                        {/* Visual indication of completion */}
                                                                        {lesson.completed && (
                                                                            <span title="Már teljesítetted!" style={{ fontSize: '1.2rem' }}>✅</span>
                                                                        )}
                                                                    </Card.Title>
                                                                    
                                                                    <Card.Text className="text-light opacity-50 small mb-4 flex-grow-1">
                                                                        {lesson.description}
                                                                    </Card.Text>
                                                                    
                                                                    <div className="d-flex justify-content-between align-items-center mt-auto pt-3 border-top border-secondary border-opacity-50">
                                                                        {getDifficultyBadge(lesson.difficulty)}
                                                                        {/* Dynamic Call-to-Action button based on progression */}
                                                                        <Button 
                                                                            variant={lesson.completed ? "outline-success" : "outline-info"} 
                                                                            size="sm" 
                                                                            className="rounded-pill px-4 fw-bold"
                                                                            onClick={() => navigate(`/lesson/${lesson.lessonId}`)}
                                                                        > 
                                                                            {lesson.completed ? 'Ismétlés' : 'Indítás'}
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