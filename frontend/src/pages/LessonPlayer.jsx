import { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { Container, Card, Button, ProgressBar, Spinner, Form } from 'react-bootstrap';
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
            const timeTakenSeconds = Math.floor((Date.now() - startTime) / 1000);

            // Construct the payload expected by the backend
            const payload = {
                timeTakenSeconds: timeTakenSeconds,
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
        return (
            <div className="min-vh-100 d-flex justify-content-center align-items-center text-light pb-5">
                <Card className="shadow-lg border-0 bg-dark text-light text-center p-5 rounded-4" style={{ maxWidth: '600px', width: '100%' }}>
                    <h1 className="display-1 mb-3" style={{ color: lessonResult.passed ? 'var(--primary-cyan)' : '#dc3545' }}>
                        {lessonResult.passed ? '🎉' : '💔'}
                    </h1>
                    <h2 className="fw-bold mb-4">{lessonResult.passed ? 'Lesson Passed!' : 'Lesson Failed.'}</h2>
                    
                    <div className="bg-secondary bg-opacity-25 rounded-3 p-4 mb-4">
                        <h4 className="mb-3 text-info fw-bold">{lessonResult.score}% Accuracy</h4>
                        <p className="fs-5 mb-1">Correct Answers: <span className="fw-bold text-success">{lessonResult.correctAnswersCount}</span> / {lessonResult.totalQuestionsCount}</p>
                        <p className="fs-5 mb-0">XP Earned: <span className="fw-bold text-warning">+{lessonResult.xpEarned} ⭐</span></p>
                    </div>

                    <p className="text-muted mb-5 fst-italic">"{lessonResult.feedback}"</p>

                    <Button variant="info" size="lg" className="fw-bold px-5 py-3 rounded-pill" onClick={() => navigate('/dashboard')}>
                        Back to Dashboard
                    </Button>
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
                    <div className="d-flex justify-content-between mb-2 fw-bold text-muted small">
                        <span>Question: {currentIndex + 1} / {exercises.length}</span>
                        <span>{Math.round(progressPercentage)}%</span>
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