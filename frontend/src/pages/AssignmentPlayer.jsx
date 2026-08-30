/**
 * @file AssignmentPlayer.jsx
 * @description Manages the interactive quiz engine for classroom assignments.
 * Handles countdown timers, immediate feedback mapping, client-side progression, and final API submission.
 */

import React, { useState, useEffect, useRef } from 'react';
import { useParams, useNavigate, useLocation } from 'react-router-dom';
import { Container, Card, Button, ProgressBar, Spinner, Form, Alert } from 'react-bootstrap';
import api from '../services/api';
import { assignmentApi } from '../services/assignmentApi';
import WordBankExercise from '../components/exercises/WordBankExercise.jsx';
import MultipleChoiceExercise from '../components/exercises/MultipleChoiceExercise.jsx';
import ImageChoiceExercise from '../components/exercises/ImageChoiceExercise.jsx';
import { getRemainingTimeMs } from '../utils/dateUtils';

/**
 * @component
 * @returns {React.ReactElement} The active assignment quiz interface.
 */
const AssignmentPlayer = () => {
    const { sessionId } = useParams();
    const navigate = useNavigate();
    const location = useLocation();

    const sessionData = location.state?.sessionData;
    const details = location.state?.assignmentDetails;

    // --- LOGIC FLAGS ---
    const hasFeedback = details?.hasFeedback === true || sessionData?.hasFeedback === true;
    const allowRetries = details?.allowRetries === true || sessionData?.allowRetries === true;
    const isTest = details?.test === true || details?.isTest === true;

    // --- STATE MANAGEMENT ---
    const [exercises, setExercises] = useState(sessionData?.exercises || []);
    const [currentIndex, setCurrentIndex] = useState(0);
    const [currentAnswer, setCurrentAnswer] = useState('');
    const [collectedAnswers, setCollectedAnswers] = useState([]);
    
    const [timeLeft, setTimeLeft] = useState(null);
    const [showSummary, setShowSummary] = useState(false);
    const [isSubmitting, setIsSubmitting] = useState(false);

    const [feedback, setFeedback] = useState(null);
    const [isChecking, setIsChecking] = useState(false);

    const isAutoSubmitting = useRef(false);

    useEffect(() => {
        if (!sessionData || !details) {
            alert("A munkamenet megszakadt. Kérlek, indítsd újra a feladatot az osztályteremből!");
            navigate('/classrooms');
        }
    }, [sessionData, details, navigate]);

    // --- TIMER ---
    useEffect(() => {
        if (!details?.timeLimitMinutes || !sessionData?.startedAt) return;
        
        const timer = setInterval(() => {
            const remaining = getRemainingTimeMs(sessionData.startedAt, details.timeLimitMinutes);
            setTimeLeft(remaining);

            if (remaining !== null && remaining <= 0 && !isAutoSubmitting.current) {
                clearInterval(timer);
                isAutoSubmitting.current = true;
                handleAutoSubmit();
            }
        }, 1000);
        
        return () => clearInterval(timer);
    }, [details, sessionData]);

    /**
     * Formats the remaining time in milliseconds into a readable MM:SS format.
     * 
     * @param {number} ms - Milliseconds remaining.
     * @returns {string} Formatted time string.
     */
    const formatTimeLeft = (ms) => {
        const totalSeconds = Math.floor(ms / 1000);
        const m = Math.floor(totalSeconds / 60);
        const s = totalSeconds % 60;
        return `${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}`;
    };

    /**
     * Evaluates the current answer or progresses to the next question based on the assignment's rules.
     * Performs API calls for validation if immediate feedback is required.
     * 
     * @async
     * @function handleCheckOrNext
     */
    const handleCheckOrNext = async () => {
        
        // 1. Strict test mode (no feedback, no retries) -> Immediate progression
        if (!hasFeedback && !allowRetries) {
            finalizeAnswerAndMove();
            return;
        }

        // 2. Feedback mode: advance if user has already received the result color (excluding warning)
        if (hasFeedback && feedback && feedback.type !== 'warning') {
            finalizeAnswerAndMove();
            return;
        }

        // 3. API Validation for immediate evaluation or silent exercise cloning
        setIsChecking(true);
        let wasCloned = false; 

        try {
            const response = await api.post(`/exercises/${currentExercise.exerciseId}/check`, {
                answer: currentAnswer.trim()
            });

            const { correct, almostCorrect, feedbackMessage } = response.data;

            if (correct) {
                if (hasFeedback) setFeedback({ type: 'success', msg: feedbackMessage || "Helyes válasz!" });
            } else if (almostCorrect) {
                if (hasFeedback) {
                    setFeedback({ type: 'warning', msg: feedbackMessage || "Majdnem jó, próbáld újra!" });
                    setIsChecking(false);
                    return; 
                } else {
                    if (allowRetries && !currentExercise.isRetry) {
                        setExercises(prev => [...prev, { ...currentExercise, isRetry: true }]);
                        wasCloned = true;
                    }
                }
            } else {
                if (hasFeedback) setFeedback({ type: 'danger', msg: feedbackMessage || "Helytelen válasz." });
                
                if (allowRetries && !currentExercise.isRetry) {
                    setExercises(prev => [...prev, { ...currentExercise, isRetry: true }]);
                    wasCloned = true;
                }
            }

            if (!hasFeedback) {
                finalizeAnswerAndMove(wasCloned);
            }

        } catch (err) {
            console.error("Hiba az ellenőrzés során:", err);
            finalizeAnswerAndMove();
        } finally {
            setIsChecking(false);
        }
    };

    /**
     * Saves the current answer into the collection array and progresses the quiz state.
     * 
     * @param {boolean} [wasJustCloned=false] - Flag indicating if the current exercise was cloned for a retry.
     */
   const finalizeAnswerAndMove = (wasJustCloned = false) => {
        const existingIndex = collectedAnswers.findIndex(a => a.exerciseId === currentExercise.exerciseId);
        let finalAnswers = [...collectedAnswers];

        if (existingIndex >= 0) {
            finalAnswers[existingIndex] = {
                ...finalAnswers[existingIndex],
                answer: currentAnswer.trim(),
                retried: true
            };
        } else {
            finalAnswers.push({
                exerciseId: currentExercise.exerciseId,
                answer: currentAnswer.trim(),
                retried: false
            });
        }

        setCollectedAnswers(finalAnswers);
        setCurrentAnswer('');
        setFeedback(null);

        if (currentIndex < exercises.length - 1 || wasJustCloned) {
            setCurrentIndex(prev => prev + 1);
        } else {
            submitAssignment(finalAnswers);
        }
    };

    /**
     * Handles automatic submission when the countdown timer reaches zero.
     */
    const handleAutoSubmit = () => {
        alert("Az időd lejárt! A rendszer automatikusan beküldi az eddigi válaszaidat.");
        submitAssignment(collectedAnswers);
    };

    /**
     * Submits the compiled array of answers to the backend API.
     * 
     * @async
     * @function submitAssignment
     * @param {Array<Object>} answersToSubmit - Array containing the user's finalized answers.
     */
    const submitAssignment = async (answersToSubmit) => {
        setIsSubmitting(true);
        try {
            const payload = answersToSubmit.map(ans => ({
                exerciseId: ans.exerciseId,
                answer: ans.answer,
                isRetry: ans.isRetry,
                retried: ans.retried
            }));

            await assignmentApi.submitAssignment(sessionId, payload);

            setShowSummary(true);
            
           } catch (error) {
            alert("Nem sikerült beküldeni a tesztet. Kérlek, ellenőrizd a kapcsolatod!", error);
        } finally {
            setIsSubmitting(false);
        }
    };

    if (!exercises.length) return null;

    const currentExercise = exercises[currentIndex];
    const progressPercentage = (currentIndex / exercises.length) * 100;
    
    // Disable inputs while verifying or if a definitive feedback is shown
    const isInputDisabled = isSubmitting || (hasFeedback && feedback && feedback.type !== 'warning');

    // --- DYNAMIC BUTTON TEXT ---
    let buttonText = 'Ellenőrzés';
    let buttonVariant = 'info';

    if (!hasFeedback) {
        buttonText = 'Tovább';
    } else if (feedback) {
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

    if (currentIndex === exercises.length - 1) {
        if (!hasFeedback || (feedback && feedback.type !== 'warning')) {
            buttonText = 'Teszt Beküldése';
            buttonVariant = 'success';
        }
    }

    // --- SUMMARY SCREEN ---
    if (showSummary) {
        return (
            <div className="min-vh-100 pb-5 text-light d-flex align-items-center bg-darker animate-fade-in">
                <Container>
                    <Card className="text-center p-5 bg-dark text-light border-success shadow-lg" style={{ maxWidth: '600px', margin: '0 auto' }}>
                        <div className="display-1 mb-4">🎉</div>
                        <h2 className="fw-bold text-success mb-3">Sikeres Beküldés!</h2>
                        <p className="fs-5 text-secondary mb-4">
                            A válaszaidat rögzítettük. Az eredményedet a tanári értékelés után tekintheted meg az osztályteremben!
                        </p>
                        <Button 
                            variant="success" 
                            size="lg" 
                            className="fw-bold px-5"
                            onClick={() => navigate(sessionData?.classroomId ? `/classrooms/${sessionData.classroomId}` : `/classrooms`)}
                        >
                            Vissza az osztályterembe
                        </Button>
                    </Card>
                </Container>
            </div>
        );
    }

    return (
        <div className="min-vh-100 pb-5 text-light d-flex align-items-center">
            <Container>
                {/* Header Section */}
                <div className="mb-4">
                    <div className="d-flex justify-content-between align-items-center mb-2 fw-bold text-light opacity-75 small">
                        <span style={{ width: '60px' }}>{currentIndex + 1} / {exercises.length}</span>
                        {timeLeft !== null && (
                            <span className={`fs-6 px-3 py-1 bg-dark rounded-pill border ${timeLeft < 60000 ? 'border-danger text-danger' : 'border-secondary text-info'}`}>
                                ⏱️ {formatTimeLeft(timeLeft)}
                            </span>
                        )}
                        <span className="text-end" style={{ width: '60px' }}>{Math.round(progressPercentage)}%</span>
                    </div>
                    <ProgressBar now={progressPercentage} variant="info" style={{ height: '10px', backgroundColor: '#333' }} className="rounded-pill border border-secondary" />
                </div>

                <Card className="shadow-lg border-0 bg-dark text-light">
                    <Card.Body className="p-4 p-md-5 text-center">
                        <h4 className="mb-4 text-info fw-bold d-flex justify-content-center align-items-center gap-2">
                            {isTest ? '📝 Tesztkérdés' : '🧠 Gyakorló feladat'}
                        </h4>

                        {currentExercise.content?.question && (
                            <p className="fs-4 mb-4 border border-secondary rounded p-3 bg-black bg-opacity-25">
                                {currentExercise.content.question.replace('Translate: ', '').replace('Translate to English: ', '')}
                            </p>
                        )}

                        {/* DYNAMIC EXERCISE RENDERING */}
                        {currentExercise.type === 'IMAGE_CHOICE' && <ImageChoiceExercise exercise={currentExercise} currentAnswer={currentAnswer} onAnswer={setCurrentAnswer} disabled={isInputDisabled} />}
                        {currentExercise.type === 'WORD_BANK' && <WordBankExercise data={currentExercise.content} currentAnswer={currentAnswer} onAnswer={setCurrentAnswer} disabled={isInputDisabled} />}
                        {currentExercise.type === 'MULTIPLE_CHOICE' && <MultipleChoiceExercise data={currentExercise.content} currentAnswer={currentAnswer} onAnswer={setCurrentAnswer} disabled={isInputDisabled} />}
                        {currentExercise.type === 'TRANSLATION' && (
                            <Form.Group className="mb-5 text-start">
                                <Form.Control as="textarea" rows={3} placeholder="Írd be a választ..." value={currentAnswer} onChange={(e) => setCurrentAnswer(e.target.value)} className="fs-5 bg-dark text-light border-secondary shadow-none" autoFocus disabled={isInputDisabled} />
                            </Form.Group>
                        )}

                        {/* VISUAL FEEDBACK */}
                        {feedback && hasFeedback && (
                            <Alert variant={feedback.type} className="mt-4 fw-bold text-start fs-5 border-0 shadow-sm transition-all">
                                {feedback.msg}
                            </Alert>
                        )}
                        
                        <div className="d-flex justify-content-end mt-5">
                            <Button variant={buttonVariant} className="px-5 fw-bold text-dark" onClick={handleCheckOrNext} disabled={currentAnswer.trim().length === 0 || isChecking || isSubmitting}>
                                {(isChecking || isSubmitting) ? <Spinner size="sm" animation="border" /> : buttonText}
                            </Button>
                        </div>
                    </Card.Body>
                </Card>
            </Container>
        </div>
    );
};

export default AssignmentPlayer;