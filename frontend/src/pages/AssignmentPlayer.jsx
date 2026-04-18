import React, { useState, useEffect, useRef } from 'react';
import { useParams, useNavigate, useLocation } from 'react-router-dom';
import { Container, Card, Button, ProgressBar, Spinner, Form, Alert } from 'react-bootstrap';
import api from '../services/api';
import { assignmentApi } from '../services/assignmentApi';
import WordBankExercise from '../components/exercises/WordBankExercise.jsx';
import MultipleChoiceExercise from '../components/exercises/MultipleChoiceExercise.jsx';
import ImageChoiceExercise from '../components/exercises/ImageChoiceExercise.jsx';

const AssignmentPlayer = () => {
    const { sessionId } = useParams();
    const navigate = useNavigate();
    const location = useLocation();

    const sessionData = location.state?.sessionData;
    const details = location.state?.assignmentDetails;

    // --- AZ OKOS FLAGEK ---
    const hasFeedback = details?.hasFeedback === true || sessionData?.hasFeedback === true;
    const allowRetries = details?.allowRetries === true || sessionData?.allowRetries === true;
    const isTest = details?.test === true || details?.isTest === true;

    // --- STATE MANAGEMENT ---
    const [exercises, setExercises] = useState(sessionData?.exercises || []);
    const [currentIndex, setCurrentIndex] = useState(0);
    const [currentAnswer, setCurrentAnswer] = useState('');
    const [collectedAnswers, setCollectedAnswers] = useState([]);
    
    const [timeLeft, setTimeLeft] = useState(null);
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

    // --- IDŐZÍTŐ ---
    useEffect(() => {
        if (!details?.timeLimitMinutes || !sessionData?.startedAt) return;
        const limitMs = details?.timeLimitMinutes * 60 * 1000;
        const startMs = new Date(sessionData.startedAt).getTime();
        
        const timer = setInterval(() => {
            const now = Date.now();
            const remaining = Math.max(0, limitMs - (now - startMs));
            setTimeLeft(remaining);

            if (remaining <= 0 && !isAutoSubmitting.current) {
                clearInterval(timer);
                isAutoSubmitting.current = true;
                handleAutoSubmit();
            }
        }, 1000);
        return () => clearInterval(timer);
    }, [details, sessionData]);

    const formatTimeLeft = (ms) => {
        const totalSeconds = Math.floor(ms / 1000);
        const m = Math.floor(totalSeconds / 60);
        const s = totalSeconds % 60;
        return `${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}`;
    };

    // --- A JAVÍTOTT, CSENDES ELLENŐRZŐ LOGIKA ---
    const handleCheckOrNext = async () => {
        
        // 1. HA VAK TESZT ÉS NINCS MÁSODIK ESÉLY -> Spórolunk az API hívással, egyből mentjük.
        if (!hasFeedback && !allowRetries) {
            finalizeAnswerAndMove();
            return;
        }

        // 2. HA VAN VIZUÁLIS VISSZAJELZÉS, és már megkaptuk a színeket -> Lapozás
        if (hasFeedback && feedback && feedback.type !== 'warning') {
            finalizeAnswerAndMove();
            return;
        }

        // 3. JÖHET AZ API ELLENŐRZÉS (Vagy a színek, vagy a csendes klónozás miatt!)
        setIsChecking(true);
        let wasCloned = false; // Ezzel cselezzük ki a React State csúszását a lapozásnál

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
                    return; // Megállunk, mert a diák kijavíthatja!
                } else {
                    // Csendes teszt esetén a typo is hibának számít, tehát klónozzuk!
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

            // HA NINCS VIZUÁLIS VISSZAJELZÉS (tehát csak a klónozás miatt ellenőriztünk), 
            // AZONNAL LAPOZUNK A KÖVETKEZŐRE!
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

    // A paraméter biztosítja, hogy az utolsó kérdés klónozásánál se küldje be véletlenül a tesztet!
   const finalizeAnswerAndMove = (wasJustCloned = false) => {
        // Megkeressük, hogy van-e már válasz ehhez a feladathoz
        const existingIndex = collectedAnswers.findIndex(a => a.exerciseId === currentExercise.exerciseId);
        let finalAnswers = [...collectedAnswers];

        if (existingIndex >= 0) {
            // HA MÁR VOLT: Felülírjuk a legújabb (jó) válasszal, és beállítjuk a javítva flget!
            finalAnswers[existingIndex] = {
                ...finalAnswers[existingIndex],
                answer: currentAnswer.trim(),
                retried: true
            };
        } else {
            // HA MÉG NEM VOLT: Első próbálkozás
            finalAnswers.push({
                exerciseId: currentExercise.exerciseId,
                answer: currentAnswer.trim(),
                retried: false
            });
        }

        setCollectedAnswers(finalAnswers);
        setCurrentAnswer('');
        setFeedback(null);

        // Lapozás vagy Beküldés
        if (currentIndex < exercises.length - 1 || wasJustCloned) {
            setCurrentIndex(prev => prev + 1);
        } else {
            // A legvégén ezt a felokosított tömböt küldjük a backendnek
            submitAssignment(finalAnswers);
        }
    };

    const handleAutoSubmit = () => {
        alert("Az időd lejárt! A rendszer automatikusan beküldi az eddigi válaszaidat.");
        submitAssignment(collectedAnswers);
    };

    const submitAssignment = async (answersToSubmit) => {
        setIsSubmitting(true);
        try {
            const payload = answersToSubmit.map(ans => ({
                exerciseId: ans.exerciseId,
                answer: ans.answer,
                isRetry: ans.isRetry
            }));

            await assignmentApi.submitAssignment(sessionId, payload);
            navigate(`/classrooms`); 
        } catch (error) {
            alert("Nem sikerült beküldeni a tesztet. Kérlek, ellenőrizd a kapcsolatod!", error);
            setIsSubmitting(false);
        }
    };

    if (!exercises.length) return null;

    const currentExercise = exercises[currentIndex];
    const progressPercentage = (currentIndex / exercises.length) * 100;
    
    // Blokkoljuk a bevitelt, ha már van kiértékelt válasz
    const isInputDisabled = isSubmitting || (hasFeedback && feedback && feedback.type !== 'warning');

    // --- DINAMIKUS GOMB FELIRATOK ---
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

    return (
        <div className="min-vh-100 pb-5 text-light d-flex align-items-center">
            <Container>
                {/* Fejléc */}
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

                        {/* FELADATOK BEEMELÉSE */}
                        {currentExercise.type === 'IMAGE_CHOICE' && <ImageChoiceExercise exercise={currentExercise} currentAnswer={currentAnswer} onAnswer={setCurrentAnswer} disabled={isInputDisabled} />}
                        {currentExercise.type === 'WORD_BANK' && <WordBankExercise data={currentExercise.content} currentAnswer={currentAnswer} onAnswer={setCurrentAnswer} disabled={isInputDisabled} />}
                        {currentExercise.type === 'MULTIPLE_CHOICE' && <MultipleChoiceExercise data={currentExercise.content} currentAnswer={currentAnswer} onAnswer={setCurrentAnswer} disabled={isInputDisabled} />}
                        {currentExercise.type === 'TRANSLATION' && (
                            <Form.Group className="mb-5 text-start">
                                <Form.Control as="textarea" rows={3} placeholder="Írd be a választ..." value={currentAnswer} onChange={(e) => setCurrentAnswer(e.target.value)} className="fs-5 bg-dark text-light border-secondary shadow-none" autoFocus disabled={isInputDisabled} />
                            </Form.Group>
                        )}

                        {/* VISSZAJELZÉS */}
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