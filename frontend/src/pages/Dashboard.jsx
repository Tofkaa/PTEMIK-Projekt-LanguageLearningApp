import { useState, useEffect } from 'react';
import { Container, Row, Col, Card, Button, Spinner, Alert, ProgressBar, Badge } from 'react-bootstrap';
import { useAuth } from '../context/AuthContext.jsx';
import api from '../services/api.jsx';
import { useNavigate } from 'react-router-dom';
import Leaderboard from '../components/Leaderboard.jsx';

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
                borderColor: 'border-info',
                glow: '0 0 20px rgba(13, 202, 240, 0.2)' 
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
            case 'HARD': return <Badge bg="danger" pill>HARD</Badge>;
            case 'MEDIUM': return <Badge bg="warning" text="dark" pill>MEDIUM</Badge>;
            case 'EASY': return <Badge bg="success" pill>EASY</Badge>;
            default: return <Badge bg="primary" pill>{difficulty}</Badge>;
        }
    };

    return (
        <div className="min-vh-100 pb-5 text-light">
            <Container>
                {/* --- HEADER SECTION --- */}
                <Row className="mb-4 pt-3">
                    <Col>
                        <h2 className="fw-bold fs-1">Üdv újra, <span className="text-info">{user?.name}</span>! 👋</h2>
                        <p className="text-light opacity-75 fs-5">
                            Készen állsz a mai tanulásra? Itt a te személyre szabott áttekintésed.
                        </p>
                    </Col>
                </Row>

                {/* --- 2-COLUMN MAIN GRID --- */}
                <Row className="g-4">
                    
                    {/* --- LEFT COLUMN: FOCUS ON LEARNING (lg=8) --- */}
                    <Col lg={8}>
                        <div className="d-flex flex-column gap-4">
                            
                            {/* HERO WIDGET */}
                            {heroState && (
                                <Card 
                                    className={`border-2 ${heroState.borderColor} bg-dark rounded-4`}
                                    style={{ boxShadow: heroState.glow || 'none' }}
                                >
                                    <Card.Body className="d-flex flex-column flex-md-row align-items-md-center justify-content-between p-4 p-md-5">
                                        <div className="mb-4 mb-md-0 pe-md-4">
                                            <h3 className="fw-bold text-light mb-2">{heroState.title}</h3>
                                            <p className="text-light opacity-75 fs-5 mb-0">
                                                {heroState.subtitle}
                                            </p>
                                        </div>
                                        <div className="text-md-end shrink-0">
                                            <Button 
                                                variant={heroState.buttonVariant} 
                                                size="lg" 
                                                className="px-5 py-3 rounded-pill fw-bold shadow-sm"
                                                onClick={() => navigate(`/lesson/${heroState.lesson.lessonId}`)}
                                            >
                                                {heroState.buttonText}
                                            </Button>
                                        </div>
                                    </Card.Body>
                                </Card>
                            )}

                            {/* LEARNING PATH (LESSONS GRID) */}
                            <Card className="shadow-sm border-0 bg-transparent text-light">
                                <Card.Body className="p-0">
                                    <h4 className="fw-bold border-bottom border-secondary pb-3 mb-4 d-flex align-items-center gap-2">
                                        📚 Tanulási Útvonal
                                    </h4>
                                    
                                    {/* UI State: Loading */}
                                    {isLoading && (
                                        <div className="text-center py-5">
                                            <Spinner animation="border" variant="info" />
                                        </div>
                                    )}
                                    
                                    {/* UI State: Error */}
                                    {error && <Alert variant="danger" className="rounded-4">{error}</Alert>}

                                    {/* UI State: Data Render */}
                                    {!isLoading && !error && Object.keys(groupedLessons).length > 0 && (
                                        <div className="d-flex flex-column gap-5">
                                            {Object.entries(groupedLessons).map(([topicName, topicLessons]) => (
                                                <div key={topicName}>
                                                    <h5 className="text-info fw-bold mb-3 px-1 text-uppercase tracking-wide small">
                                                        {topicName}
                                                    </h5>
                                                    <Row className="g-3">
                                                        {topicLessons.map((lesson) => (
                                                            <Col md={6} key={lesson.lessonId}> 
                                                                <Card 
                                                                    className={`h-100 border shadow-sm bg-dark text-light rounded-4 ${lesson.completed ? 'border-success border-opacity-50' : 'border-secondary'}`} 
                                                                    style={{ transition: 'transform 0.2s, box-shadow 0.2s', cursor: 'pointer' }} 
                                                                    onMouseEnter={(e) => { e.currentTarget.style.transform = 'translateY(-5px)'; e.currentTarget.style.boxShadow = '0 .5rem 1rem rgba(0,0,0,.15)' }} 
                                                                    onMouseLeave={(e) => { e.currentTarget.style.transform = 'translateY(0)'; e.currentTarget.style.boxShadow = 'none' }}
                                                                >
                                                                    <Card.Body className="d-flex flex-column p-4">
                                                                        <Card.Title className="fw-bold mb-2 d-flex justify-content-between align-items-start">
                                                                            <span className="pe-2">{lesson.title}</span>
                                                                            {lesson.completed && <span title="Már teljesítetted!" className="fs-5">✅</span>}
                                                                        </Card.Title>
                                                                        
                                                                        <Card.Text className="text-light opacity-50 small mb-4 flex-grow-1">
                                                                            {lesson.description}
                                                                        </Card.Text>
                                                                        
                                                                        <div className="d-flex justify-content-between align-items-center mt-auto pt-3 border-top border-secondary border-opacity-25">
                                                                            {getDifficultyBadge(lesson.difficulty)}
                                                                            <Button 
                                                                                variant={lesson.completed ? "outline-success" : "info"} 
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
                        </div>
                    </Col>

                    {/* --- RIGHT COLUMN: SIDEBAR (META DATA) (lg=4) --- */}
                    <Col lg={4}>
                        <div className="d-flex flex-column gap-4 sticky-top" style={{ top: '20px' }}>
                            
                            {/* STATISTICS */}
                            <Card className="shadow-lg border-secondary rounded-4 bg-dark text-light">
                                <Card.Body className="p-4">
                                    <h5 className="fw-bold border-bottom border-secondary pb-3 mb-4">📊 Teljesítményed</h5>
                                    
                                    {/* 1. XP Progress */}
                                    <div className="mb-4">
                                        <div className="d-flex justify-content-between align-items-end mb-2">
                                            <span className="fw-bold text-info fs-5">Szint {currentLevel}</span>
                                            <span className="text-muted small fw-bold">{currentXp} / {xpForNextLevel} XP</span>
                                        </div>
                                        <ProgressBar now={progressPercentage} variant="info" className="rounded-pill" style={{ height: '10px' }} />
                                        <div className="text-center mt-2 small text-light opacity-50">
                                            Még {xpForNextLevel - currentXp} XP a szintlépéshez!
                                        </div>
                                    </div>

                                    <div className="d-flex flex-column gap-3">
                                        {/* 2. Daily Streak */}
                                        <div className="d-flex justify-content-between align-items-center p-3 bg-black bg-opacity-25 rounded-3">
                                            <span className="fw-bold text-light d-flex align-items-center gap-2">
                                                <span className="fs-4">🔥</span> Napi sorozat
                                            </span>
                                            <span className="text-warning fw-bold fs-5">{user?.streak || 0} nap</span>
                                        </div>

                                        {/* 3. Course Progress - NOW FIXING THE UNUSED VARIABLE ISSUE */}
                                        <div className="p-3 bg-black bg-opacity-25 rounded-3 d-flex flex-column gap-2">
                                            <div className="d-flex justify-content-between align-items-center">
                                                <span className="fw-bold text-light d-flex align-items-center gap-2">
                                                    <span className="fs-4">📖</span> Elvégzett leckék
                                                </span>
                                                <span className="text-success fw-bold fs-5">{completedLessonsCount} / {totalLessonsCount}</span>
                                            </div>
                                            {/* We use the variable here to show a subtle progress bar */}
                                            <ProgressBar now={courseProgressPercentage} variant="success" className="rounded-pill" style={{ height: '4px' }} />
                                        </div>
                                    </div>
                                </Card.Body>
                            </Card>

                            {/* LEADERBOARD */}
                            <Card className="shadow-lg border-secondary rounded-4 bg-dark overflow-hidden">
                                <Card.Body className="p-0">
                                    <Leaderboard defaultScope="global" />
                                </Card.Body>
                            </Card>
                            
                        </div>
                    </Col>
                </Row>
            </Container>
        </div>
    );
};

export default Dashboard;