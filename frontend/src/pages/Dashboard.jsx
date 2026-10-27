import { Container, Row, Col, Card, Button, Spinner, Alert, ProgressBar, Badge } from 'react-bootstrap';
import { useAuth } from '../context/AuthContext.jsx';
import useScopedData from '../hooks/useScopedData';
import { useNavigate } from 'react-router-dom';
import Leaderboard from '../components/Leaderboard.jsx';
import TutorialTour from '../components/TutorialTour.jsx';
import LessonMastery from '../components/LessonMastery.jsx';
import { getMasteryRecommendation, LEVEL_NAMES } from '../utils/mastery';

/**
 * Dashboard Component
 * The central hub for authenticated users. 
 * Orchestrates user statistics, course progress, and dynamically fetches and groups the learning path.
 */
const Dashboard = () => {
    // --- CONTEXT & ROUTING ---
    const { user } = useAuth();
    const hasUser = Boolean(user);
    const navigate = useNavigate();

    // --- LOCAL STATE ---
    const scope = JSON.stringify([user?.userId, user?.activeCourseCode, user?.preferredDifficulty]);
    const { data: lessons, isLoading, error: loadError } = useScopedData('/lessons', scope, hasUser);
    const error = loadError ? 'Nem sikerült betölteni a leckéket a szerverről.' : '';

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
        const playable = lessons.filter(lesson => !lesson.availableDifficulties || lesson.availableDifficulties.includes(lesson.difficulty));
        const nextLesson = playable.find(lesson => !lesson.completed);
        const mastery = getMasteryRecommendation(lessons);

        if (completedLessonsCount === totalLessonsCount && mastery) {
            heroState = {
                type: 'MASTERY',
                title: mastery.remaining === 1 ? 'Már csak egy csillag hiányzik ebből a leckéből!' : 'Gyűjtsd össze a hiányzó csillagokat!',
                titleIcon: '⭐',
                subtitle: `${mastery.lesson.title} – szerezd meg a ${LEVEL_NAMES[mastery.difficulty].toLocaleLowerCase('hu')} szint csillagát!`,
                lesson: mastery.lesson,
                replayDifficulty: mastery.difficulty,
                buttonText: 'Következő csillag',
                buttonVariant: 'info',
                borderColor: 'border-info',
            };
        } else if (completedLessonsCount === totalLessonsCount && completedLessonsCount > 0
            && (playable.length > 0 || lessons.some(lesson => lesson.availableDifficulties?.length))) {
            // Everything completed
            const practiceLesson = playable[0] || lessons.find(lesson => lesson.availableDifficulties?.length);
            const allMastered = lessons.every(lesson => !lesson.availableDifficulties?.length || lesson.mastered)
                && lessons.some(lesson => lesson.mastered);
            heroState = {
                type: allMastered ? 'ALL_MASTERED' : 'ALL_DONE',
                title: allMastered ? 'Minden csillagot megszereztél!' : 'Minden elérhető leckét teljesítettél!',
                titleIcon: allMastered ? '🏆' : '🎉',
                subtitle: allMastered ? 'A kurzus minden elérhető szintjét sikeresen teljesítetted. A megszerzett csillagaid megmaradnak: gyakorolj kedvedre!'
                    : 'Fantasztikus munka! Frissítsd fel a tudásod egy korábbi lecke ismétlésével.',
                lesson: practiceLesson,
                replayDifficulty: allMastered ? (practiceLesson.availableDifficulties.includes(practiceLesson.difficulty)
                    ? practiceLesson.difficulty : practiceLesson.availableDifficulties[0]) : undefined,
                buttonText: allMastered ? 'Szabad gyakorlás' : 'Tudás felfrissítése',
                buttonIcon: '🔄',
                buttonVariant: 'warning',
                borderColor: 'border-warning'
            };
        } else if (completedLessonsCount === 0 && nextLesson) {
            // No completed lessons
            heroState = {
                type: 'FIRST_STEPS',
                title: 'Készen állsz az első kalandra?',
                titleIcon: '🚀',
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
                title: 'Folytasd a tanulást!',
                titleIcon: '📚',
                subtitle: `A következő logikus lépés: ${nextLesson.title} (${nextLesson.topicName || 'Egyéb'})`,
                lesson: nextLesson,
                buttonText: 'Folytatás',
                buttonIcon: '➡️',
                buttonVariant: 'info',
                borderColor: 'border-info',
                glow: '0 0 20px rgba(13, 202, 240, 0.2)' 
            };
        }
    }

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
            case 'HARD': return <Badge bg="danger" pill><span aria-hidden="true">HARD</span><span className="visually-hidden">Profi</span></Badge>;
            case 'MEDIUM': return <Badge bg="warning" text="dark" pill><span aria-hidden="true">MEDIUM</span><span className="visually-hidden">Haladó</span></Badge>;
            case 'EASY': return <Badge bg="success" pill><span aria-hidden="true">EASY</span><span className="visually-hidden">Kezdő</span></Badge>;
            default: return <Badge bg="primary" pill>{difficulty}</Badge>;
        }
    };

   return (
        <div className="min-vh-100 pb-5 text-light">
            <TutorialTour />
            <Container>
                {/* --- HEADER SECTION --- */}
                <Row className="mb-4 pt-3">
                    <Col>
                        <h1 className="fw-bold fs-1">Üdv újra, <span className="app-accent-text">{user?.name}</span>! <span aria-hidden="true">👋</span></h1>
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
                                    className={`tour-hero-widget border-2 ${heroState.borderColor} bg-dark rounded-4${heroState.type === 'MASTERY' || heroState.type === 'ALL_MASTERED' ? ' mastery-hero' : ''}`}
                                    style={{ boxShadow: heroState.glow || 'none' }}
                                >
                                    <Card.Body className="d-flex flex-column flex-md-row flex-wrap gap-3 align-items-md-center justify-content-between p-4 p-md-5">
                                        <div className="dashboard-hero-copy pe-md-4">
                                            <h2 className="h3 fw-bold text-light mb-2">{heroState.title} <span aria-hidden="true">{heroState.titleIcon}</span></h2>
                                            <p className="text-light opacity-75 fs-5 mb-0">
                                                {heroState.subtitle}
                                            </p>
                                        </div>
                                        <div className="dashboard-hero-action text-md-end">
                                            <Button 
                                                variant={heroState.buttonVariant} 
                                                size="lg" 
                                                className="px-5 py-3 rounded-pill fw-bold shadow-sm"
                                                aria-label={`${heroState.buttonText}: ${heroState.lesson.title}${heroState.replayDifficulty ? `, ${LEVEL_NAMES[heroState.replayDifficulty]} szint` : ''}`}
                                                onClick={() => navigate(`/lesson/${heroState.lesson.lessonId}${heroState.replayDifficulty ? `?difficulty=${heroState.replayDifficulty}` : ''}`)}
                                            >
                                                {heroState.buttonText} <span aria-hidden="true">{heroState.buttonIcon}</span>
                                            </Button>
                                        </div>
                                    </Card.Body>
                                </Card>
                            )}

                           {/* LEARNING PATH (LESSONS GRID) */}
                            <Card className="shadow-sm border-0 bg-transparent text-light">
                                <Card.Body className="p-0">
                                    <h2 className="h4 tour-learning-path fw-bold border-bottom border-secondary pb-3 mb-4 d-flex align-items-center gap-2">
                                        <span aria-hidden="true">📚</span> Tanulási Útvonal
                                    </h2>
                                    <details className="mastery-guide">
                                        <summary>Hogyan működnek a csillagok?</summary>
                                        <p>A csillagok balról jobbra a Kezdő, Haladó és Profi szintet jelölik.
                                            A sikeres teljesítés számít a masterybe, nem kell hibátlanul megoldanod a leckét.
                                            Az első teljesítésért szintenként jár XP; a kézzel választott ismétlés nem változtatja a dinamikus ajánlást.
                                            Minden elérhető szint teljesítésével leckemasteryt, a témakör összes leckéjével témakörmasteryt szerezhetsz.</p>
                                    </details>

                                    {/* UI State: Loading */}
                                    {isLoading && (
                                        <div className="text-center py-5">
                                            <Spinner animation="border" variant="info" role="status"><span className="visually-hidden">Leckék betöltése...</span></Spinner>
                                        </div>
                                    )}
                                    
                                    {/* UI State: Error */}
                                    {error && <Alert variant="danger" className="rounded-4">{error}</Alert>}

                                    {/* UI State: Data Render */}
                                    {!isLoading && !error && Object.keys(groupedLessons).length > 0 && (
                                        <div className="d-flex flex-column gap-5">
                                            {Object.entries(groupedLessons).map(([topicName, topicLessons]) => (
                                                <div key={topicName}>
                                                    <h3 className="app-accent-text fw-bold mb-3 px-1 text-uppercase tracking-wide small">
                                                        {topicName}
                                                    </h3>
                                                    {topicLessons.some(lesson => lesson.availableDifficulties?.length) && <p className="small px-1">
                                                        {topicLessons.every(lesson => lesson.topicMastered) ? <Badge bg="success"><span aria-hidden="true">🏆 </span>Témakörmastery</Badge>
                                                            : `Mastery: ${topicLessons.filter(lesson => lesson.mastered).length} / ${topicLessons.filter(lesson => lesson.availableDifficulties?.length).length} lecke`}
                                                    </p>}
                                                    <Row className="g-3">
                                                        {topicLessons.map((lesson) => (
                                                            <Col md={6} key={lesson.lessonId}> 
                                                                <Card 
                                                                    as="article" aria-labelledby={`lesson-title-${lesson.lessonId}`}
                                                                    className={`lesson-card h-100 border shadow-sm bg-dark text-light rounded-4 ${lesson.completed ? 'border-success border-opacity-50' : 'border-secondary'}`}
                                                                >
                                                                    <Card.Body className="d-flex flex-column p-4">
                                                                        <Card.Title as="h4" id={`lesson-title-${lesson.lessonId}`} className="h5 fw-bold mb-2 d-flex justify-content-between align-items-start">
                                                                            <span className="pe-2">{lesson.title}</span>{' '}
                                                                            {lesson.completed && <span className="fs-5"><span aria-hidden="true">✅</span><span className="visually-hidden"> – Teljesítve</span></span>}
                                                                        </Card.Title>
                                                                        
                                                                        <Card.Text id={`lesson-description-${lesson.lessonId}`} className="text-body-secondary small mb-4 flex-grow-1">
                                                                            {lesson.description}
                                                                        </Card.Text>
                                                                        
                                                                        <div className="d-flex justify-content-between align-items-center mt-auto pt-3 border-top border-secondary border-opacity-25">
                                                                            <span className="small">Következő szint: {getDifficultyBadge(lesson.difficulty)}</span>
                                                                            <Button
                                                                                variant={lesson.completed ? "outline-success" : "info"} 
                                                                                size="sm" 
                                                                                className="rounded-pill px-4 fw-bold"
                                                                                aria-label={`${lesson.completed ? 'Ismétlés' : 'Indítás'}: ${lesson.title}`}
                                                                                aria-describedby={`lesson-description-${lesson.lessonId}`}
                                                                                disabled={lesson.availableDifficulties && !lesson.availableDifficulties.includes(lesson.difficulty)}
                                                                                onClick={() => navigate(`/lesson/${lesson.lessonId}`)}
                                                                            > 
                                                                                {lesson.completed ? 'Ismétlés' : 'Indítás'}
                                                                            </Button>
                                                                        </div>
                                                                        {lesson.availableDifficulties && !lesson.availableDifficulties.includes(lesson.difficulty) && <p className="small mt-2 mb-0">
                                                                            A beállított szinten nincs feladat. Válassz másik nehézséget a beállításokban{lesson.completed ? ', vagy az alábbi ismétlésnél' : ''}.
                                                                        </p>}
                                                                        <LessonMastery key={`${lesson.lessonId}-${lesson.completedDifficulties?.join('-')}`} lesson={lesson}
                                                                            onReplay={level => navigate(`/lesson/${lesson.lessonId}?difficulty=${level}`)} />
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
                        <div className="d-flex flex-column gap-4" style={{ top: '20px' }}>
                            
                          {/* STATISTICS */}
                            <Card className="tour-performance shadow-lg border-secondary rounded-4 bg-dark text-light">
                                <Card.Body className="p-4">
                                    <h2 className="h5 fw-bold border-bottom border-secondary pb-3 mb-4"><span aria-hidden="true">📊</span> Teljesítményed</h2>
                                    
                                    {/* 1. XP Progress */}
                                    <div className="mb-4">
                                        <div className="d-flex justify-content-between align-items-end mb-2">
                                            <span className="fw-bold app-accent-text fs-5">Szint {currentLevel}</span>
                                            <span className="text-muted small fw-bold">{currentXp} / {xpForNextLevel} XP</span>
                                        </div>
                                        <ProgressBar className="rounded-pill" style={{ height: '10px' }}><ProgressBar aria-label="Előrehaladás a következő szintig" now={progressPercentage} variant="info" /></ProgressBar>
                                        <div className="text-center mt-2 small text-body-secondary">
                                            Még {xpForNextLevel - currentXp} XP a szintlépéshez!
                                        </div>
                                    </div>

                                    <div className="d-flex flex-column gap-3">
                                        {/* 2. Daily Streak */}
                                        <div className="d-flex justify-content-between align-items-center p-3 bg-black bg-opacity-25 rounded-3">
                                            <span className="fw-bold text-light d-flex align-items-center gap-2">
                                                <span aria-hidden="true" className="fs-4">🔥</span> Napi sorozat
                                            </span>
                                            <span className="text-warning fw-bold fs-5">{user?.streak || 0} nap</span>
                                        </div>

                                        {/* 3. Course Progress - NOW FIXING THE UNUSED VARIABLE ISSUE */}
                                        <div className="p-3 bg-black bg-opacity-25 rounded-3 d-flex flex-column gap-2">
                                            <div className="d-flex justify-content-between align-items-center">
                                                <span className="fw-bold text-light d-flex align-items-center gap-2">
                                                    <span aria-hidden="true" className="fs-4">📖</span> Elvégzett leckék
                                                </span>
                                                <span className="text-success fw-bold fs-5">{completedLessonsCount} / {totalLessonsCount}</span>
                                            </div>
                                            {/* We use the variable here to show a subtle progress bar */}
                                            <ProgressBar className="rounded-pill" style={{ height: '4px' }}><ProgressBar aria-label="Elvégzett leckék aránya" now={courseProgressPercentage} variant="success" /></ProgressBar>
                                        </div>
                                    </div>
                                </Card.Body>
                            </Card>

                            {/* LEADERBOARD */}
                            <Card className="tour-leaderboard shadow-lg border-secondary rounded-4 bg-dark overflow-hidden">
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
