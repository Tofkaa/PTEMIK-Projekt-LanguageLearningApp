import { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { Container, Card, Button, ProgressBar, Spinner, Form, Row, Col } from 'react-bootstrap';
import api from '../services/api.jsx';
import { useAuth } from '../context/AuthContext.jsx'; // <-- Needed to update global XP state
import WordBankExercise from '../components/exercises/WordBankExercise.jsx';
import MultipleChoiceExercise from '../components/exercises/MultipleChoiceExercise.jsx';

/**
 * LessonPlayer Component
 * Manages the interactive learning experience (Quiz Engine).
 * Full lifecycle: Data Fetching -> Quiz Engine -> POST Submission -> Result Screen
 */
const LessonPlayer = () => {
    const { id: lessonId } = useParams();
    const navigate = useNavigate();
    
    // Use the login function to update the in-memory XP after completing a lesson
    const { user, login } = useAuth(); 

    // --- STATE MANAGEMENT ---
    
    // Network and data states
    const [exercises, setExercises] = useState([]);
    const [isLoading, setIsLoading] = useState(true);
    const [isSubmitting, setIsSubmitting] = useState(false); // Active while waiting for backend evaluation
    const [error, setError] = useState(null);
    const [lessonResult, setLessonResult] = useState(null); // Stores the successful POST response

    // Quiz engine internal states
    const [currentIndex, setCurrentIndex] = useState(0); // Index of the current exercise
    const [currentAnswer, setCurrentAnswer] = useState(''); // Text currently typed by the user
    const [collectedAnswers, setCollectedAnswers] = useState([]); // Array of answers to be submitted
    const [startTime, setStartTime] = useState(null); // Timestamp for tracking completion time
    const [elapsedTime, setElapsedTime] = useState(0); // Timestamp for tracking elapsed time mid-lesson

    // --- PHASE 1: DATA FETCHING ---
    useEffect(() => {
        const fetchExercises = async () => {
            try {
                const response = await api.get(`/lessons/${lessonId}/exercises`);
                if (response.data.length === 0) {
                    setError("No exercises found for this lesson.");
                } else {
                    setExercises(response.data);
                    setStartTime(Date.now()); // Start the timer
                }
            } catch (err) {
                console.error("Error fetching exercises:", err);
                setError("Failed to connect to the server.");
            } finally {
                setIsLoading(false);
            }
        };

        fetchExercises();
    }, [lessonId]);

    useEffect(() => {
        let timer;
        // Csak akkor fusson az óra, ha már betöltött, van kezdési idő, és még nem küldtük be
        if (startTime && !isSubmitting && !lessonResult) {
            timer = setInterval(() => {
                // Kiszámoljuk a pontos különbséget a kezdés óta
                setElapsedTime(Math.floor((Date.now() - startTime) / 1000));
            }, 1000);
        }
        // Takarítás, ha a komponens leáll, vagy a függőségek változnak
        return () => clearInterval(timer);
    }, [startTime, isSubmitting, lessonResult]);

    // Segédfüggvény az idő formázásához (pl. 65 mp -> "01:05")
    const formatTime = (totalSeconds) => {
        const minutes = Math.floor(totalSeconds / 60);
        const seconds = totalSeconds % 60;
        return `${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}`;
    };

    // Helper variables for UI
    const progressPercentage = exercises.length > 0 ? (currentIndex / exercises.length) * 100 : 0;
    const currentExercise = exercises[currentIndex];

    // --- PHASE 2: NEXT QUESTION OR SUBMIT ---
    const handleNextOrSubmit = async () => {
        // Since React state updates are asynchronous, we create a local array 
        // to immediately include the final answer before submission.
        const finalAnswers = [
            ...collectedAnswers, 
            {
                exerciseId: currentExercise.exerciseId,
                answer: currentAnswer.trim()
            }
        ];
        
        // Save the current answer and clear the input field
        setCollectedAnswers(finalAnswers);
        setCurrentAnswer('');

        if (currentIndex < exercises.length - 1) {
            // If there are more questions, increment the index
            setCurrentIndex(prevIndex => prevIndex + 1);
        } else {
            // --- PHASE 3: SUBMIT TO BACKEND ---
            setIsSubmitting(true);

            // Construct the payload expected by the backend
            const payload = {
                timeTakenSeconds: elapsedTime,
                answers: finalAnswers
            };

            try {
                console.log("Submitting payload:", payload);
                const response = await api.post(`/lessons/${lessonId}/submit`, payload);
                console.log("Evaluation successful:", response.data);
                
                // Store the result, which triggers the rendering of the Result Screen
                setLessonResult(response.data);

                // EXTRA: Update the global User Context to immediately reflect the newly earned XP
                // in the Navigation Bar and Dashboard without requiring a page reload.
                const updatedUser = { ...user, xp: user.xp + (response.data.xpEarned || 0) };
                login(localStorage.getItem('token'), updatedUser); 

            } catch (err) {
                console.error("Submission error:", err);
                setError("An error occurred while submitting your answers.");
            } finally {
                setIsSubmitting(false);
            }
        }
    };

    // --- PHASE 4: RENDER RESULT SCREEN ---
    if (lessonResult) {
        const isPassed = lessonResult.passed;

        return (
            <div className="min-vh-100 d-flex flex-column justify-content-center align-items-center text-light pb-5 pt-5">
                
                {/* 1. Header / Icon Area */}
                <div className="text-center mb-4">
                    <div 
                        className="mb-3 d-inline-flex justify-content-center align-items-center rounded-circle shadow-lg"
                        style={{ 
                            width: '120px', height: '120px', fontSize: '4rem', 
                            backgroundColor: isPassed ? 'rgba(25, 135, 84, 0.2)' : 'rgba(220, 53, 69, 0.2)',
                            border: `4px solid ${isPassed ? '#198754' : '#dc3545'}`
                        }}
                    >
                        {isPassed ? '🏆' : '💔'}
                    </div>
                    <h1 className="fw-bold display-5 mb-2">{isPassed ? 'Sikeres Lecke!' : 'Gyakorolj még egy kicsit!'}</h1>
                    <p className="text-light opacity-75 fs-5 fst-italic">"{lessonResult.feedback}"</p>
                </div>

                {/* 2. Stats Card */}
                <Card className="shadow-lg border-0 bg-dark text-light p-3 rounded-4 w-100 mb-4" style={{ maxWidth: '600px' }}>
                    <Card.Body>
                        <Row className="text-center mb-4 g-3">
                            {/* Accuracy Stat */}
                           <Col xs={4}>
                                <div className="p-3 bg-secondary bg-opacity-25 rounded-4 border border-secondary h-100 d-flex flex-column justify-content-center">
                                    <h6 className="text-light opacity-75 text-uppercase fw-bold mb-2" style={{ fontSize: '0.75rem', letterSpacing: '1px' }}>Pontosság</h6>
                                    <h3 className={isPassed ? 'text-success fw-bold mb-0' : 'text-danger fw-bold mb-0'}>
                                        {lessonResult.score}%
                                    </h3>
                                </div>
                            </Col>
                            {/* Time Taken*/}
                            <Col xs={4}>
                                <div className="p-3 bg-secondary bg-opacity-25 rounded-4 border border-secondary h-100 d-flex flex-column justify-content-center">
                                    <h6 className="text-light opacity-75 text-uppercase fw-bold mb-2" style={{ fontSize: '0.75rem', letterSpacing: '1px' }}>Idő</h6>
                                    <h3 className="text-info fw-bold mb-0">
                                        {formatTime(elapsedTime)}
                                    </h3>
                                </div>
                            </Col>
                            {/* XP Stat */}
                            <Col xs={4}>
                                <div className="p-3 bg-secondary bg-opacity-25 rounded-4 border border-secondary h-100 d-flex flex-column justify-content-center">
                                    <h6 className="text-light opacity-75 text-uppercase fw-bold mb-2" style={{ fontSize: '0.75rem', letterSpacing: '1px' }}>XP</h6>
                                    <h3 className="text-warning fw-bold mb-0">
                                        +{lessonResult.xpEarned}⭐
                                    </h3>
                                </div>
                            </Col>
                        </Row>

                        {/* Detailed Breakdown */}
                        <div className="d-flex justify-content-between align-items-center p-3 mb-4 bg-black bg-opacity-25 rounded-3 border border-secondary">
                            <span className="text-light fw-bold">Helyes válaszok</span>
                            <span className="fs-5 fw-bold text-info">
                                {lessonResult.correctAnswersCount} <span className="text-light opacity-50 fs-6">/ {lessonResult.totalQuestionsCount}</span>
                            </span>
                        </div>

                        {lessonResult.mistakes && lessonResult.mistakes.length > 0 && (
                            <div className="mt-4 text-start">
                                <h5 className="text-warning fw-bold mb-3 border-bottom border-secondary pb-2">Hibák áttekintése:</h5>
                                {lessonResult.mistakes.map((mistake, idx) => (
                                    <div key={idx} className="bg-black bg-opacity-50 p-3 rounded-3 mb-3 border border-secondary">
                                        <p className="mb-2 text-light fw-bold">{mistake.question}</p>
                                        <p className="mb-1 text-danger small fw-bold">❌ Te válaszod: <span className="fw-normal">{mistake.submittedAnswer || '(Üresen hagyva)'}</span></p>
                                        <p className="mb-0 text-success small fw-bold">✅ Helyes válasz: <span className="fw-normal">{mistake.correctAnswer}</span></p>
                                    </div>
                                ))}
                            </div>
                        )}

                        {/* 3. Action Buttons */}
                        <div className="d-grid gap-3 mt-4">
                            {isPassed ? (
                                <Button variant="info" size="lg" className="fw-bold rounded-pill text-dark py-3" onClick={() => navigate('/dashboard')}>
                                    Vissza a Dashboardra
                                </Button>
                            ) : (
                                <>
                                    <Button variant="outline-info" size="lg" className="fw-bold rounded-pill py-3" onClick={() => window.location.reload()}>
                                        Újrapróbálom
                                    </Button>
                                    <Button variant="link" className="text-light opacity-50 text-decoration-none" onClick={() => navigate('/dashboard')}>
                                        Befejezés később
                                    </Button>
                                </>
                            )}
                        </div>
                    </Card.Body>
                </Card>
            </div>
        );
    }

    // --- PHASE 5: RENDER LOADING STATE ---
    if (isLoading || isSubmitting) {
        return (
            <div className="min-vh-100 d-flex flex-column justify-content-center align-items-center text-light">
                <Spinner animation="border" variant="info" className="mb-3" />
                <h5>{isSubmitting ? 'Evaluating answers...' : 'Loading exercises...'}</h5>
            </div>
        );
    }

    // --- PHASE 6: RENDER ERROR STATE ---
    if (error) { 
        return (
            <Container className="mt-5 text-center text-light">
                <h3 className="text-danger">{error}</h3>
                <Button variant="outline-light" className="mt-3" onClick={() => navigate('/dashboard')}>Back to Dashboard</Button>
            </Container>
        );
    }

    // --- PHASE 7: RENDER ACTIVE QUIZ ENGINE ---
    return (
        <div className="min-vh-100 pb-5 text-light d-flex align-items-center">
            <Container>
               {/* Progress Bar Header */}
                <div className="mb-4">
                    <div className="d-flex justify-content-between align-items-center mb-2 fw-bold text-light opacity-75 small">
                        <span style={{ width: '60px' }}>{currentIndex + 1} / {exercises.length}</span>
                        
                        <span className="text-info fs-6 px-3 py-1 bg-dark rounded-pill border border-secondary">
                            ⏱️ {formatTime(elapsedTime)}
                        </span>
                        
                        <span className="text-end" style={{ width: '60px' }}>{Math.round(progressPercentage)}%</span>
                    </div>
                    <ProgressBar now={progressPercentage} variant="info" style={{ height: '10px', backgroundColor: '#333' }} className="rounded-pill border border-secondary" />
                </div>

               {/* Quiz Card */}
                <Card className="shadow-lg border-0 bg-dark text-light">
                    <Card.Body className="p-4 p-md-5 text-center">
                        <h4 className="mb-4 text-info fw-bold">Translate the following sentence!</h4>
                        <p className="fs-4 mb-5 border border-secondary rounded p-3 bg-gradient">
                            {currentExercise?.content?.question?.replace('Translate to English: ', '').replace('Rakd sorba: ', '')}
                        </p>
                        
                        {currentExercise?.type === 'WORD_BANK' ? (
                            <WordBankExercise 
                                data={currentExercise.content} 
                                onAnswer={setCurrentAnswer} 
                            />
                       ) : currentExercise?.type === 'MULTIPLE_CHOICE' ? (
                            <MultipleChoiceExercise 
                                data={currentExercise.content} 
                                currentAnswer={currentAnswer} 
                                onAnswer={setCurrentAnswer} 
                            />
                        ) : (
                            <Form.Group className="mb-5 text-start">
                                <Form.Control 
                                    as="textarea" 
                                    rows={3} 
                                    placeholder="Type the English translation here..." 
                                    value={currentAnswer} 
                                    onChange={(e) => setCurrentAnswer(e.target.value)} 
                                    className="fs-5" 
                                    autoFocus 
                                />
                            </Form.Group>
                        )}
                        <div className="d-flex justify-content-between mt-4">
                            <Button variant="outline-secondary" onClick={() => navigate('/dashboard')}>Finish Later</Button>
                            
                            {/* Dynamically update button text based on quiz progress */}
                            <Button variant="info" className="px-5 fw-bold text-dark" onClick={handleNextOrSubmit} disabled={currentAnswer.trim().length === 0}>
                                {currentIndex === exercises.length - 1 ? 'Submit & Evaluate' : 'Next'}
                            </Button>
                        </div>
                    </Card.Body>
                </Card>
            </Container>
        </div>
    );
};

export default LessonPlayer;