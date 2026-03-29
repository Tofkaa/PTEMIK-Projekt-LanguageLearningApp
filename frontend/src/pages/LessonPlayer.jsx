import { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { Container, Card, Button, ProgressBar, Spinner, Form, Row, Col, Alert } from 'react-bootstrap';
import api from '../services/api.jsx';
import { useAuth } from '../context/AuthContext.jsx';
import WordBankExercise from '../components/exercises/WordBankExercise.jsx';
import MultipleChoiceExercise from '../components/exercises/MultipleChoiceExercise.jsx';
import ImageChoiceExercise from '../components/exercises/ImageChoiceExercise.jsx';

/**
 * LessonPlayer Component
 * Manages the interactive learning experience (Quiz Engine).
 * Full lifecycle: Data Fetching -> Quiz Engine -> POST Submission -> Result Screen
 */
const LessonPlayer = () => {
    const { id: lessonId } = useParams();
    const navigate = useNavigate();
    const { user, login } = useAuth(); 

    // --- STATE MANAGEMENT ---
    const [exercises, setExercises] = useState([]);
    const [isLoading, setIsLoading] = useState(true);
    const [isSubmitting, setIsSubmitting] = useState(false);
    const [error, setError] = useState(null);
    const [lessonResult, setLessonResult] = useState(null);

    const [currentIndex, setCurrentIndex] = useState(0);
    const [currentAnswer, setCurrentAnswer] = useState('');
    const [collectedAnswers, setCollectedAnswers] = useState([]);
    const [startTime, setStartTime] = useState(null);
    const [elapsedTime, setElapsedTime] = useState(0);

    const [feedback, setFeedback] = useState(null);
    const [isChecking, setIsChecking] = useState(false); 
    // --- PHASE 1: DATA FETCHING ---
    useEffect(() => {
        const fetchExercises = async () => {
            try {
                const response = await api.get(`/lessons/${lessonId}/exercises`);
                if (response.data.length === 0) {
                    setError("No exercises found for this lesson.");
                } else {
                    setExercises(response.data);
                    setStartTime(Date.now());
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
        if (startTime && !isSubmitting && !lessonResult) {
            timer = setInterval(() => {
                setElapsedTime(Math.floor((Date.now() - startTime) / 1000));
            }, 1000);
        }
        return () => clearInterval(timer);
    }, [startTime, isSubmitting, lessonResult]);

    const formatTime = (totalSeconds) => {
        const minutes = Math.floor(totalSeconds / 60);
        const seconds = totalSeconds % 60;
        return `${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}`;
    };

    const progressPercentage = exercises.length > 0 ? (currentIndex / exercises.length) * 100 : 0;
    const currentExercise = exercises[currentIndex];
    
    const isInputDisabled = feedback && feedback.type !== 'warning';

    // --- PHASE 2: IMMEDIATE FEEDBACK & NEXT QUESTION ---
    const handleCheckOrNext = async () => {
        
        if (!feedback || feedback.type === 'warning') {
            setIsChecking(true);
            try {
                const response = await api.post(`/exercises/${currentExercise.exerciseId}/check`, {
                    answer: currentAnswer.trim()
                });

                const { correct, almostCorrect, feedbackMessage } = response.data;

                if (correct) {
                    setFeedback({ type: 'success', msg: feedbackMessage });
                } else if (almostCorrect) {
                    setFeedback({ type: 'warning', msg: feedbackMessage });
                } else {
                    setFeedback({ type: 'danger', msg: feedbackMessage });
                    
                    // CLONE the lesson if it has not been retried yet, and the user failed it
                    if (!currentExercise.isRetry) {
                        setExercises(prev => [...prev, { ...currentExercise, isRetry: true }]);
                    }
                }
            } catch (err) {
                console.error("Hiba az ellenőrzés során:", err);
                finalizeAnswerAndMove();
            } finally {
                setIsChecking(false);
            }
        } 
        else {
            finalizeAnswerAndMove();
        }
    };

    const finalizeAnswerAndMove = () => {
        const finalAnswers = [
            ...collectedAnswers, 
            {
                exerciseId: currentExercise.exerciseId,
                answer: currentAnswer.trim(),
                isRetry: !!currentExercise.isRetry
            }
        ];
        
        setCollectedAnswers(finalAnswers);
        setCurrentAnswer('');
        setFeedback(null);

        if (currentIndex < exercises.length - 1) {
            setCurrentIndex(prevIndex => prevIndex + 1);
        } else {
            submitLesson(finalAnswers);
        }
    };

    // --- PHASE 3: SUBMIT TO BACKEND ---
    const submitLesson = async (finalAnswers) => {
        setIsSubmitting(true);
        const payload = {
            timeTakenSeconds: elapsedTime,
            answers: finalAnswers
        };

       try {
            console.log("Submitting final payload:", payload);
            const response = await api.post(`/lessons/${lessonId}/submit`, payload);
            setLessonResult(response.data);
            
            // --- ROBUST STATE SYNCHRONIZATION ---
            // Instead of manually calculating XP and streaks on the client (which can lead to desyncs if the user
            // navigates away quickly), we fetch the authoritative User Profile directly from the backend.
            try {
                const userResponse = await api.get('/users/me'); 
                login(localStorage.getItem('token'), userResponse.data); // Update global AuthContext
            } catch (fetchErr) {
                console.warn("Failed to fetch fresh profile, initiating fallback update:", fetchErr);
                // Safety net fallback: Apply manual calculations if the profile fetch fails
                const updatedUser = { 
                    ...user, 
                    xp: user.xp + (response.data.xpEarned || 0),
                    streak: response.data.newStreak !== undefined ? response.data.newStreak : user.streak
                };
                login(localStorage.getItem('token'), updatedUser); 
            }
        } catch (err) {
            console.error("Submission error:", err);
            setError("An error occurred while submitting your answers.");
        } finally {
            setIsSubmitting(false);
        }
    };
    // --- PHASE 4: RENDER RESULT SCREEN ---
    if (lessonResult) {
        const isPassed = lessonResult.passed;
        const originalTotalQ = lessonResult.totalQuestionsCount || 1;
        const mistakesCount = lessonResult.mistakes ? lessonResult.mistakes.length : 0;
        const totalAttempts = originalTotalQ + mistakesCount;
        const totalCorrectAnswers = lessonResult.correctAnswersCount; 
        const displayAccuracy = Math.round((totalCorrectAnswers / totalAttempts) * 100);
        const isAlreadyCompleted = isPassed && lessonResult.xpEarned === 0;

        const maxPotentialXp = originalTotalQ * 10;
        const lostXp = maxPotentialXp - lessonResult.xpEarned;
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
                                    <h3 className={displayAccuracy >= 60 ? 'text-success fw-bold mb-0' : 'text-warning fw-bold mb-0'}>
                                        {displayAccuracy}%
                                    </h3>
                                </div>
                            </Col>
                            {/* Time Taken */}
                            <Col xs={4}>
                                <div className="p-3 bg-secondary bg-opacity-25 rounded-4 border border-secondary h-100 d-flex flex-column justify-content-center">
                                    <h6 className="text-light opacity-75 text-uppercase fw-bold mb-2" style={{ fontSize: '0.75rem', letterSpacing: '1px' }}>Idő</h6>
                                    <h3 className="text-info fw-bold mb-0">
                                        {formatTime(elapsedTime)}
                                    </h3>
                                </div>
                            </Col>
                            {/* XP Stat - with lost XP displayed */}
                            <Col xs={4}>
                                <div className="p-3 bg-secondary bg-opacity-25 rounded-4 border border-secondary h-100 d-flex flex-column justify-content-center">
                                    <h6 className="text-light opacity-75 text-uppercase fw-bold mb-2" style={{ fontSize: '0.75rem', letterSpacing: '1px' }}>XP</h6>
                                    
                                    {isAlreadyCompleted ? (
                                        <>
                                            <h4 className="text-secondary fw-bold mb-0">0⭐</h4>
                                            <span className="text-info mt-1 d-block" style={{ fontSize: '0.70rem', fontWeight: 'bold' }}>
                                                Már teljesítve
                                            </span>
                                        </>
                                    ) : (
                                        <>
                                            <h3 className="text-warning fw-bold mb-0">
                                                +{lessonResult.xpEarned}⭐
                                            </h3>
                                            {lostXp > 0 && (
                                                <span className="text-danger mt-1 d-block" style={{ fontSize: '0.75rem', fontWeight: 'bold' }}>
                                                    -{lostXp} XP (hibák)
                                                </span>
                                            )}
                                        </>
                                    )}
                                </div>
                            </Col>
                        </Row>

                        {/* Detailed Breakdown */}
                        <div className="d-flex justify-content-between align-items-center p-3 mb-4 bg-black bg-opacity-25 rounded-3 border border-secondary">
                            <span className="text-light fw-bold">Helyes válaszok (próbálkozásokkal)</span>
                            <span className="fs-5 fw-bold text-info">
                                {totalCorrectAnswers} <span className="text-light opacity-50 fs-6">/ {totalAttempts}</span>
                            </span>
                        </div>

                        {/* Mistakes List */}
                        {lessonResult.mistakes && lessonResult.mistakes.length > 0 && (
                            <div className="mt-4 text-start">
                                <h5 className="text-warning fw-bold mb-3 border-bottom border-secondary pb-2">Hibák áttekintése:</h5>
                                {lessonResult.mistakes.map((mistake, idx) => (
                                    <div key={idx} className="bg-black bg-opacity-50 p-3 rounded-3 mb-3 border border-secondary">
                                        <p className="mb-2 text-light fw-bold">{mistake.question}</p>
                                        <p className="mb-1 text-danger small fw-bold">
                                            ❌ Te válaszod: <span className="fw-normal">{mistake.submittedAnswer ? mistake.submittedAnswer.replace(/[[\]]/g, '') : '(Üresen hagyva)'}</span>
                                        </p>
                                        <p className="mb-0 text-success small fw-bold">
                                            ✅ Helyes válasz: <span className="fw-normal">{mistake.correctAnswer}</span>
                                        </p>
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
    const renderExercise = () => {
        if (!currentExercise) return null;

        const { type, content } = currentExercise;
        
        // Clean up the question text (if there are any unnecessary prefixes left in the database)
        const questionText = content?.question?.replace('Translate: ', '').replace('Translate to English: ', '') || '';

        // Dynamic header depending on task type
        const getTitle = () => {
            switch (type) {
                case 'TRANSLATION': return '📝 Fordítási feladat';
                case 'MULTIPLE_CHOICE': return '✅ Feleletválasztós kérdés';
                case 'WORD_BANK': return '🧩 Szókirakó';
                case 'IMAGE_CHOICE': return '🖼️ Képes feladat';
                default: return 'Feladat';
            }
        };

        return (
            <>
                {/* Dynamic, task-specific title */}
                <h4 className="mb-4 text-info fw-bold">{getTitle()}</h4>
                
                {/* Display the question/instruction (if it exists) */}
                {questionText && (
                    <p className="fs-4 mb-4 border border-secondary rounded p-3 bg-black bg-opacity-25">
                        {questionText}
                    </p>
                )}

                {/* --- DYNAMIC LOADING OF COMPONENTS BY TYPE --- */}
                
                {type === 'IMAGE_CHOICE' && (
                    <ImageChoiceExercise 
                        exercise={currentExercise} 
                        currentAnswer={currentAnswer} 
                        onAnswer={setCurrentAnswer} 
                        disabled={isInputDisabled}
                    />
                )}

                {type === 'WORD_BANK' && (
                    <WordBankExercise 
                        data={content} 
                        onAnswer={setCurrentAnswer} 
                        currentAnswer={currentAnswer}
                        disabled={isInputDisabled}
                    />
                )}

                {type === 'MULTIPLE_CHOICE' && (
                    <MultipleChoiceExercise 
                        data={content} 
                        currentAnswer={currentAnswer} 
                        onAnswer={setCurrentAnswer} 
                        disabled={isInputDisabled}
                    />
                )}

                {type === 'TRANSLATION' && (
                    <Form.Group className="mb-5 text-start">
                        <Form.Control 
                            as="textarea" 
                            rows={3} 
                            placeholder="Írd ide a fordítást angolul..." 
                            value={currentAnswer} 
                            onChange={(e) => setCurrentAnswer(e.target.value)} 
                            className="fs-5 bg-dark text-light border-secondary shadow-none" 
                            autoFocus 
                            disabled={isInputDisabled}
                        />
                    </Form.Group>
                )}
            </>
        );
    };

    // --- DYNAMICALLY PAINT AND LABEL BUTTONS ---
    let buttonText = 'Ellenőrzés';
    let buttonVariant = 'info';

    if (feedback) {
        if (feedback.type === 'warning') {
            buttonText = 'Újraellenőrzés';
            buttonVariant = 'warning';
        } else if (feedback.type === 'success') {
            buttonText = 'Tovább';
            buttonVariant = 'success';
        } else {
            buttonText = 'Tovább';
            buttonVariant = 'danger';
        }
    }

    // If this is the last exercise and it has already been checked
    if (currentIndex === exercises.length - 1 && feedback && feedback.type !== 'warning') {
        buttonText = 'Befejezés és Értékelés';
    }

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
                        
                        {renderExercise()}

                        {/* IMMEDIATE FEEDBACK BOX */}
                        {feedback && (
                            <Alert variant={feedback.type} className="mt-4 fw-bold text-start fs-5 border-0 shadow-sm transition-all">
                                {feedback.msg}
                            </Alert>
                        )}
                        
                        <div className="d-flex justify-content-between mt-4">
                            <Button variant="outline-secondary" onClick={() => navigate('/dashboard')}>Finish Later</Button>
                            
                            <Button 
                                variant={buttonVariant} 
                                className="px-5 fw-bold text-dark" 
                                onClick={handleCheckOrNext} 
                                disabled={currentAnswer.trim().length === 0 || isChecking}
                            >
                                {isChecking ? <Spinner size="sm" animation="border" /> : buttonText}
                            </Button>
                        </div>
                    </Card.Body>
                </Card>
            </Container>
        </div>
    );
};

export default LessonPlayer;