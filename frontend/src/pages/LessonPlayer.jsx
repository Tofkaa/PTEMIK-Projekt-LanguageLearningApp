import { useState, useEffect } from 'react';
import { useParams, useNavigate, useSearchParams } from 'react-router-dom';
import { Container, Card, Button, ProgressBar, Spinner, Form, Row, Col } from 'react-bootstrap';
import api from '../services/api.jsx';
import { useAuth } from '../context/AuthContext.jsx';
import WordBankExercise from '../components/exercises/WordBankExercise.jsx';
import MultipleChoiceExercise from '../components/exercises/MultipleChoiceExercise.jsx';
import ImageChoiceExercise from '../components/exercises/ImageChoiceExercise.jsx';
import { useNotifications } from '../context/NotificationContext.jsx';
import CryptoJS from 'crypto-js';
import ClickableText from '../components/ClickableText.jsx';
import LearningFeedback from '../components/LearningFeedback.jsx';
import LanguageText from '../components/LanguageText.jsx';
import TranslationHint from '../components/TranslationHint.jsx';
import DurationText from '../components/DurationText.jsx';
import useStageFocus from '../hooks/useStageFocus';


const LANGUAGE_NAMES = {
        'hu': 'Magyar',
        'en': 'Angol',
        'es': 'Spanyol',
        'de': 'Német'
    };

const SALT = import.meta.env.VITE_APP_SECURITY_EXERCISE_SALT;
const generateHash = (input) => {
    if (!input) return "";
   
    const normalized = input.toLowerCase().trim();
   
    return CryptoJS.SHA256(normalized + SALT).toString(CryptoJS.enc.Hex);
};
/**
 * LessonPlayer Component
 * Manages the interactive learning experience (Quiz Engine).
 * Full lifecycle: Data Fetching -> Quiz Engine -> POST Submission -> Result Screen
 */
const LessonPlayer = () => {
    const { id: lessonId } = useParams();
    const navigate = useNavigate();
    const { user, updateProfile } = useAuth();

    const [searchParams] = useSearchParams();
    const challengeId = searchParams.get('challengeId');
    
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
    const [checkError, setCheckError] = useState(null);
    const [submissionError, setSubmissionError] = useState(null);
    const headingRef = useStageFocus(lessonResult ? 'result' : submissionError ? 'submission-error' : error ? 'error'
        : isLoading ? 'loading' : isSubmitting ? 'submitting' : `question-${currentIndex}`);

    const [renderedWords, setRenderedWords] = useState([]);
    const [clickedWords, setClickedWords] = useState(new Set());

    const { refreshNotifications } = useNotifications();
   
    // --- PHASE 1: DATA FETCHING ---
    useEffect(() => {
        const fetchExercises = async () => {
            try {
                const endpoint = challengeId 
                    ? `/lessons/${lessonId}/exercises?challengeId=${challengeId}` 
                    : `/lessons/${lessonId}/exercises`;
                const response = await api.get(endpoint);
                if (response.data.length === 0) {
                    setError('Ehhez a leckéhez nem található feladat.');
                } else {
                    setExercises(response.data);
                    setStartTime(Date.now());
                }
            } catch (err) {
                console.error("Error fetching exercises:", err);
                setError('Nem sikerült betölteni a feladatokat.');
            } finally {
                setIsLoading(false);
            }
        };

        fetchExercises();
    }, [challengeId, lessonId]);

    useEffect(() => {
        let timer;
        if (startTime && !isSubmitting && !lessonResult) {
            timer = setInterval(() => {
                setElapsedTime(Math.floor((Date.now() - startTime) / 1000));
            }, 1000);
        }
        return () => clearInterval(timer);
    }, [startTime, isSubmitting, lessonResult]);


    const progressPercentage = exercises.length > 0 ? (currentIndex / exercises.length) * 100 : 0;
    const currentExercise = exercises[currentIndex];
    
    const isInputDisabled = isChecking || isSubmitting || !!(feedback && feedback.type !== 'warning');

    const handleAutoAddWords = () => {
        const unclicked = renderedWords.filter(w => !clickedWords.has(w));
        
        if (unclicked.length > 0) {
            api.post('/vocabulary/known-batch', { words: unclicked })
                .catch(err => console.error("Auto-add sikertelen:", err));
        }
    };

   // --- PHASE 2: IMMEDIATE FEEDBACK & NEXT QUESTION ---
    const handleCheckOrNext = async () => {
        if (isChecking || isSubmitting || !currentAnswer.trim()) return;
        setCheckError(null);
        if (!feedback || feedback.type === 'warning') {
            setIsChecking(true);
            
            // EXERCISE TYPE?
            if (currentExercise.type === 'TRANSLATION') {
                // CASE #1: Translation (typing!!) -> Call backend (typo handling ect.)
                try {
                    const response = await api.post(`/exercises/${currentExercise.exerciseId}/check`, {
                        answer: currentAnswer.trim()
                    });

                    const { correct, almostCorrect, feedbackMessage } = response.data;

                    if (correct) {
                        setFeedback({ type: 'success', msg: feedbackMessage });
                       handleAutoAddWords();
                    } else if (almostCorrect) {
                        setFeedback({ type: 'warning', msg: feedbackMessage });
                    } else {
                        setFeedback({ type: 'danger', msg: feedbackMessage });
                        
                        if (!currentExercise.isRetry) {
                            setExercises(prev => [...prev, { ...currentExercise, isRetry: true }]);
                        }
                    }
                } catch (err) {
                    console.error("Hiba a backend ellenőrzés során:", err);
                    setCheckError('Nem sikerült ellenőrizni a választ. A válaszod megmaradt, próbáld újra.');
                } finally {
                    setIsChecking(false);
                }
            } else {
                // CASE #2: Everything else (Multiple choice, Images, Wordbank) -> Client side Hash validating
                try {
                    const localHash = generateHash(currentAnswer);
                    if (localHash === currentExercise.answerHash) {
                        // Perfect match
                        setFeedback({ type: 'success', msg: <>Tökéletes! <span aria-hidden="true">✅</span></> });
                       handleAutoAddWords();
                    } else {
                        // Faulty answer
                        setFeedback({ type: 'danger', msg: <>Helytelen! Semmi baj, menjünk tovább. <span aria-hidden="true">❌</span></> });
                        
                        if (!currentExercise.isRetry) {
                            setExercises(prev => [...prev, { ...currentExercise, isRetry: true }]);
                        }
                    }
                } catch (err) {
                    console.error("Hiba a lokális hash ellenőrzés során:", err);
                    setCheckError('Nem sikerült ellenőrizni a választ. A válaszod megmaradt, próbáld újra.');
                } finally {
                    setIsChecking(false);
                }
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
        setRenderedWords([]);
        setClickedWords(new Set());

        if (currentIndex < exercises.length - 1) {
            setCurrentIndex(prevIndex => prevIndex + 1);
        } else {
            submitLesson(finalAnswers);
        }
    };

    // --- PHASE 3: SUBMIT TO BACKEND ---
    const submitLesson = async (finalAnswers) => {
        setIsSubmitting(true);
        setSubmissionError(null);
        const payload = {
            timeTakenSeconds: elapsedTime,
            answers: finalAnswers
        };

       try {
            const endpoint = challengeId 
            ? `/lessons/${lessonId}/submit?challengeId=${challengeId}` 
            : `/lessons/${lessonId}/submit`;
            
            const response = await api.post(endpoint, payload);
            setLessonResult(response.data);

            if (refreshNotifications) {
                refreshNotifications();
            }
            
            try {
                const userResponse = await api.get('/users/me'); 
                await updateProfile(userResponse.data);
            } catch (fetchErr) {
                console.warn("Failed to fetch fresh profile, initiating fallback update:", fetchErr);
                const updatedUser = { 
                    ...user, 
                    xp: user.xp + (response.data.xpEarned || 0),
                    streak: response.data.newStreak !== undefined ? response.data.newStreak : user.streak
                };
                await updateProfile(updatedUser);
            }
        } catch (err) {
            console.error("Submission error:", err);
            setSubmissionError('Nem sikerült beküldeni a válaszokat.');
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
                <div className="text-center mb-4">
                    <div 
                        className="mb-3 d-inline-flex justify-content-center align-items-center rounded-circle shadow-lg"
                        style={{ 
                            width: '120px', height: '120px', fontSize: '4rem', 
                            backgroundColor: isPassed ? 'rgba(25, 135, 84, 0.2)' : 'rgba(220, 53, 69, 0.2)',
                            border: `4px solid ${isPassed ? '#198754' : '#dc3545'}`
                        }}
                    >
                        <span aria-hidden="true">{isPassed ? '🏆' : '💔'}</span>
                    </div>
                    <h1 ref={headingRef} tabIndex={-1} className="fw-bold display-5 mb-2">{isPassed ? 'Sikeres Lecke!' : 'Gyakorolj még egy kicsit!'}</h1>
                    <p className="text-light opacity-75 fs-5 fst-italic">"{lessonResult.feedback}"</p>
                </div>

                {(lessonResult.isLevelUp || lessonResult.levelUp) && (
                    <div 
                        className="mb-4 p-3 rounded-4 border border-warning w-100 d-flex align-items-center justify-content-center gap-3 pulse-warning-banner"
                        style={{ 
                            maxWidth: '600px', 
                            backgroundColor: 'rgba(255, 193, 7, 0.15)'
                        }}
                    >
                        <span aria-hidden="true" className="fs-1">🔥</span>
                        <div className="text-start">
                            <h2 className="h4 text-warning fw-bold mb-1">Szintet léptél!</h2>
                            <p className="text-light opacity-75 mb-0 small">
                                Kiválóan teljesítesz! A rendszer <strong className="text-warning">{lessonResult.newDifficulty}</strong> szintre kapcsolt.
                            </p>
                        </div>
                    </div>
                )}

                {(lessonResult.isLevelDown || lessonResult.levelDown) && (
                    <div 
                        className="mb-4 p-3 rounded-4 w-100 d-flex align-items-center justify-content-center gap-3 breathing-demote-banner"
                        style={{ 
                            maxWidth: '600px', 
                            backgroundColor: 'rgba(255, 255, 255, 0.05)'
                        }}
                    >
                        <span aria-hidden="true" className="fs-1">📉</span>
                        <div className="text-start">
                            <h2 className="h4 text-secondary fw-bold mb-1">Kicsit visszavettünk a tempóból</h2>
                            <p className="text-light opacity-75 mb-0 small">
                                A rendszer <strong className="text-light">{lessonResult.newDifficulty}</strong> szintre állított, hogy nyugodtan gyakorolhass.
                            </p>
                        </div>
                    </div>
                )}

                <Card className="shadow-lg border-0 bg-dark text-light p-3 rounded-4 w-100 mb-4" style={{ maxWidth: '600px' }}>
                    <Card.Body>
                        <Row className="lesson-result-stats text-center mb-4 g-3">
                            <Col>
                                <dl className="p-3 bg-secondary bg-opacity-25 rounded-4 border border-secondary h-100 mb-0 d-flex flex-column justify-content-center">
                                    <dt className="text-body-secondary text-uppercase fw-bold mb-2" style={{ fontSize: '0.75rem', letterSpacing: '1px' }}>Pontosság</dt>
                                    <dd className={`h3 fw-bold mb-0 ${displayAccuracy >= 60 ? 'text-success' : 'text-warning'}`}>
                                        {displayAccuracy}%
                                    </dd>
                                </dl>
                            </Col>
                            <Col>
                                <dl className="p-3 bg-secondary bg-opacity-25 rounded-4 border border-secondary h-100 mb-0 d-flex flex-column justify-content-center">
                                    <dt className="text-body-secondary text-uppercase fw-bold mb-2" style={{ fontSize: '0.75rem', letterSpacing: '1px' }}>Idő</dt>
                                    <dd className="h3 text-info fw-bold mb-0">
                                        <DurationText seconds={elapsedTime} />
                                    </dd>
                                </dl>
                            </Col>
                            <Col>
                                <dl className="p-3 bg-secondary bg-opacity-25 rounded-4 border border-secondary h-100 mb-0 d-flex flex-column justify-content-center">
                                    <dt className="text-body-secondary text-uppercase fw-bold mb-2" style={{ fontSize: '0.75rem', letterSpacing: '1px' }}>XP</dt>
                                    <dd className="mb-0">
                                    {isAlreadyCompleted ? (
                                        <>
                                            <p className="h4 text-secondary fw-bold mb-0">0<span aria-hidden="true">⭐</span></p>
                                            <span className="text-info mt-1 d-block" style={{ fontSize: '0.70rem', fontWeight: 'bold' }}>Már teljesítve</span>
                                        </>
                                    ) : (
                                        <>
                                            <p className="h3 text-warning fw-bold mb-0">+{lessonResult.xpEarned}<span aria-hidden="true">⭐</span></p>
                                            {lostXp > 0 && <span className="text-danger mt-1 d-block" style={{ fontSize: '0.75rem', fontWeight: 'bold' }}>-{lostXp} XP (hibák)</span>}
                                        </>
                                    )}
                                    </dd>
                                </dl>
                            </Col>
                        </Row>

                        <div className="d-flex flex-wrap gap-2 justify-content-between align-items-center p-3 mb-4 bg-black bg-opacity-25 rounded-3 border border-secondary">
                            <span className="text-light fw-bold">Helyes válaszok (próbálkozásokkal)</span>
                            <span className="fs-5 fw-bold text-info">
                                <span aria-hidden="true">{totalCorrectAnswers} <span className="text-body-secondary fs-6">/ {totalAttempts}</span></span>
                                <span lang="hu" className="visually-hidden">{totalAttempts} próbálkozásból {totalCorrectAnswers} helyes válasz</span>
                            </span>
                        </div>

                        {lessonResult.mistakes && lessonResult.mistakes.length > 0 && (
                            <div className="mt-4 text-start">
                                <h2 className="h5 text-warning fw-bold mb-3 border-bottom border-secondary pb-2">Hibák áttekintése:</h2>
                                {lessonResult.mistakes.map((mistake, idx) => {
                                    const mistakeContent = exercises.find(exercise => exercise.exerciseId === mistake.exerciseId)?.content || {};
                                    return (
                                        <div key={idx} className="bg-black bg-opacity-50 p-3 rounded-3 mb-3 border border-secondary">
                                            <p className="mb-2 text-light fw-bold"><LanguageText text={mistake.question}
                                                lang={mistakeContent.questionLang} parts={mistakeContent.questionParts} /></p>
                                            <p className="mb-1 text-danger small fw-bold">
                                                <span aria-hidden="true">❌</span> Te válaszod: <span lang={mistake.submittedAnswer ? mistakeContent.answerLang : 'hu'} className="fw-normal">{mistake.submittedAnswer ? mistake.submittedAnswer.replace(/[[\]]/g, '') : '(Üresen hagyva)'}</span>
                                            </p>
                                            <p className="mb-0 text-success small fw-bold">
                                                <span aria-hidden="true">✅</span> Helyes válasz: <span lang={mistakeContent.answerLang} className="fw-normal">{mistake.correctAnswer}</span>
                                            </p>
                                        </div>
                                    );
                                })}
                            </div>
                        )}

                        <div className="d-grid gap-3 mt-4">
                            {isPassed ? (
                                <Button variant="info" size="lg" className="fw-bold rounded-pill text-dark py-3" onClick={() => navigate(challengeId ? '/friends' : '/dashboard')}>
                                    Visszatérés
                                </Button>
                            ) : (
                                <>
                                    <Button variant="outline-info" size="lg" className="fw-bold rounded-pill py-3" onClick={() => window.location.reload()}>Újrapróbálom</Button>
                                    <Button variant="outline-secondary" onClick={() => navigate(challengeId ? '/friends' : '/dashboard')}>Később folytatom</Button>
                                </>
                            )}
                        </div>
                    </Card.Body>
                </Card>
            </div>
        );
    }
    if (isLoading || isSubmitting) {
        return (
            <div className="min-vh-100 d-flex flex-column justify-content-center align-items-center text-light">
                <Spinner animation="border" variant="info" className="mb-3" />
                <h1 ref={headingRef} tabIndex={-1} className="h5">{isSubmitting ? 'Válaszok értékelése...' : 'Feladatok betöltése...'}</h1>
            </div>
        );
    }

    if (submissionError) {
        return <Container className="mt-5 text-center">
            <h1 ref={headingRef} tabIndex={-1} className="h3">{submissionError}</h1>
            <p>A válaszaid ezen az oldalon megmaradtak. Ellenőrizd a kapcsolatod, majd próbáld újra a mentést.</p>
            <Button onClick={() => submitLesson(collectedAnswers)}>Mentés újrapróbálása</Button>
        </Container>;
    }

    if (error) {
        return (
            <Container className="mt-5 text-center text-light">
                <h1 ref={headingRef} tabIndex={-1} className="h3 text-danger">{error}</h1>
                <Button variant="outline-secondary" className="mt-3" onClick={() => navigate('/dashboard')}>Vissza az áttekintéshez</Button>
            </Container>
        );
    }

   // --- PHASE 7: RENDER ACTIVE QUIZ ENGINE ---
    const renderExercise = () => {
        if (!currentExercise) return null;

        const { type, content } = currentExercise;
        
        const questionText = content?.question || '';
        
        const getTitle = () => {
            if (type === 'TRANSLATION') {
                const qLang = LANGUAGE_NAMES[content?.questionLang] || content?.questionLang?.toUpperCase() || 'Ismeretlen';
                const aLang = LANGUAGE_NAMES[content?.answerLang] || content?.answerLang?.toUpperCase() || 'Ismeretlen';
                return `Fordítás (${qLang} ➔ ${aLang})`;
            }
            switch (type) {
                case 'MULTIPLE_CHOICE': return 'Válaszd ki a helyes opciót';
                case 'WORD_BANK': return 'Rakd sorba a szavakat';
                case 'IMAGE_CHOICE': return 'Válaszd ki a képhez illő szót';
                default: return 'Feladat';
            }
        };
        const allowDictionary = content?.questionLang !== 'hu';
        const Instruction = 'h1';

        return (
            <div className="d-flex flex-column align-items-center w-100">
                
                {/* 1. Task Type */}
                <Instruction id="lesson-task-instruction" lang="hu" ref={headingRef} tabIndex={0} className="text-body-secondary fw-bold text-uppercase mb-3 d-block" style={{ letterSpacing: '2px', fontSize: '0.9rem' }}>
                    {getTitle()}
                    <span className="visually-hidden">. {currentIndex + 1}. feladat, összesen {exercises.length}.</span>
                </Instruction>
                
                {/* Question 2 Text */}
                {questionText && (
                    <div 
                        className="mb-5 p-4 rounded-4 border border-info border-opacity-50 text-center w-100"
                        style={{ 
                            maxWidth: '800px', 
                            backgroundColor: 'rgba(13, 202, 240, 0.05)',
                            borderWidth: '2px',
                            boxShadow: '0 0 20px rgba(13, 202, 240, 0.15)'
                        }}
                    >
                      <h2 tabIndex={0} lang={content?.questionLang || 'hu'} className="fw-bold text-light mb-0" style={{ lineHeight: '1.4', fontSize: '1.8rem' }}>

                                <ClickableText 
                                    text={questionText} 
                                    lang={content?.questionLang}
                                    languageParts={content?.questionParts}
                                    disabled={!!challengeId || !allowDictionary}
                                    hint={content?.hint} 
                                    hintDirection={content?.hintDirection}
                                    source="LESSON" 
                                    onWordsFound={setRenderedWords}
                                    onWordClick={(word) => setClickedWords(prev => new Set(prev).add(word))}
                                />
                        </h2>
                        {allowDictionary && !challengeId && <p lang="hu" className="small text-body-secondary mt-3 mb-0">Az aláhúzott szavak fordítása Enterrel vagy Szóközzel megnyitható.</p>}

                       {!allowDictionary && content?.hint && (
                            <div tabIndex={0} role="note" aria-labelledby="lesson-hint-title" aria-describedby="lesson-hint-content" className="d-inline-block text-start mt-2 px-3 py-2 rounded" style={{ backgroundColor: 'rgba(13, 202, 240, 0.1)', borderLeft: '4px solid #0dcaf0' }}>
                                <span id="lesson-hint-title" lang="hu" className="small text-light fw-bold text-uppercase" style={{ letterSpacing: '1px' }}>
                                    <i className="bi bi-lightbulb me-2 text-warning"></i>Tipp a fordításhoz
                                </span>
                                <div id="lesson-hint-content" className="mt-1 small fw-medium text-body">
                                    <TranslationHint content={content} />
                                </div>
                            </div>
                        )}
                    </div>
                )}

                {/* 3. Sub-components container */}
                <div className="w-100" style={{ maxWidth: '700px' }}>
                    {type === 'IMAGE_CHOICE' && (
                        <ImageChoiceExercise exercise={currentExercise} currentAnswer={currentAnswer} onAnswer={setCurrentAnswer} disabled={isInputDisabled} />
                    )}

                    {type === 'WORD_BANK' && (
                        <WordBankExercise data={content} onAnswer={setCurrentAnswer} currentAnswer={currentAnswer} disabled={isInputDisabled} />
                    )}

                    {type === 'MULTIPLE_CHOICE' && (
                        <MultipleChoiceExercise data={content} currentAnswer={currentAnswer} onAnswer={setCurrentAnswer} disabled={isInputDisabled} />
                    )}

                    {type === 'TRANSLATION' && (
                        <Form.Group className="mb-4 text-start" controlId="lesson-answer">
                            <Form.Label id="lesson-answer-label" lang="hu">Válaszod</Form.Label>
                            <Form.Control 
                                as="textarea" 
                                rows={3} 
                                aria-labelledby="lesson-answer-label"
                                value={currentAnswer} 
                                onChange={(e) => setCurrentAnswer(e.target.value)} 
                                className="fs-4 p-4 rounded-4 shadow-none text-info fw-bold" 
                                style={{
                                    backgroundColor: 'rgba(0, 0, 0, 0.3)',
                                    border: '2px solid rgba(13, 202, 240, 0.2)',
                                    resize: 'none'
                                }}
                                lang={content?.answerLang}
                                disabled={isInputDisabled}
                            />
                        </Form.Group>
                    )}
                </div>
            </div>
        );
    };

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

    if (currentIndex === exercises.length - 1 && feedback && feedback.type !== 'warning') {
        buttonText = 'Befejezés és Értékelés';
    }

    return (
        <div className="min-vh-100 pb-5 text-light d-flex align-items-center">
            <Container>
                {/* Progress Bar Header */}
                <div className="mb-4">
                    <div className="d-flex justify-content-between align-items-center mb-2 fw-bold text-light opacity-75 small">
                        <span style={{ width: '60px' }}><span aria-hidden="true">{currentIndex + 1} / {exercises.length}</span><span lang="hu" className="visually-hidden">{currentIndex + 1}. feladat, összesen {exercises.length}.</span></span>
                        
                        <span role="timer" aria-live="off" aria-labelledby="lesson-elapsed-time" className="text-info fs-6 px-3 py-1 bg-dark rounded-pill border border-secondary">
                            <span aria-hidden="true">⏱️ </span><DurationText id="lesson-elapsed-time" seconds={elapsedTime} label="Eltelt idő" />
                        </span>
                        
                        <span className="text-end" style={{ width: '60px' }}>{Math.round(progressPercentage)}%</span>
                    </div>
                    <ProgressBar style={{ height: '10px', backgroundColor: '#333' }} className="rounded-pill border border-secondary">
                        <ProgressBar aria-label="Lecke előrehaladása" now={progressPercentage} variant="info" />
                    </ProgressBar>
                </div>

                {/* Quiz Card */}
                <Card className="shadow-lg border-0 bg-dark text-light rounded-4">
                    <Card.Body className="p-4 p-md-5 text-center d-flex flex-column align-items-center">
                        
                        {renderExercise()}

                        {/* IMMEDIATE FEEDBACK BOX */}
                        <LearningFeedback feedback={feedback} error={checkError} />
                        
                        <div className="d-flex justify-content-between mt-5 w-100" style={{ maxWidth: '700px' }}>
                            <Button variant="outline-secondary" className="rounded-pill px-4" onClick={() => navigate('/dashboard')}>
                                Később folytatom
                            </Button>
                            
                            <Button 
                                variant={buttonVariant} 
                                size="lg"
                                className="px-5 fw-bold rounded-pill shadow-sm"
                                onClick={handleCheckOrNext} 
                                disabled={currentAnswer.trim().length === 0}
                                aria-disabled={isChecking || isSubmitting || !currentAnswer.trim()}
                                aria-busy={isChecking || isSubmitting}
                            >
                                {isChecking ? <><Spinner size="sm" animation="border" aria-hidden="true" /> Ellenőrzés folyamatban...</> : buttonText}
                            </Button>
                        </div>
                    </Card.Body>
                </Card>
            </Container>
        </div>
    );
};

export default LessonPlayer;
