import { safeError } from '../utils/safeError';
import EarnedAchievements from '../components/EarnedAchievements';
import { useState, useEffect, useRef } from 'react';
import { Container, Card, Button, ProgressBar, Spinner, Form } from 'react-bootstrap';
import { useNavigate } from 'react-router-dom';
import api from '../services/api.jsx';
import { useNotifications } from '../context/NotificationContext.jsx';
import MultipleChoiceExercise from '../components/exercises/MultipleChoiceExercise';
import WordBankExercise from '../components/exercises/WordBankExercise';
import LearningFeedback from '../components/LearningFeedback';
import useStageFocus from '../hooks/useStageFocus';
import LanguageText from '../components/LanguageText';
import { exerciseTypeLabel } from '../utils/exerciseTypeLabel';
import { useAuth } from '../context/AuthContext';
import { getAuthEpoch } from '../services/authSession';

const VocabularyPractice = () => {
    const { user } = useAuth();
    return <VocabularySession key={JSON.stringify([getAuthEpoch(), user?.userId, user?.activeCourseCode, user?.activeCourseId, user?.activeCourseRevision])} courseId={user?.activeCourseId} />;
};

const VocabularySession = ({ courseId }) => {
    const navigate = useNavigate();
    const { refreshNotifications } = useNotifications();
    const [exercises, setExercises] = useState([]);
    const [attemptId, setAttemptId] = useState(null);
    const [currentIndex, setCurrentIndex] = useState(0);
    const [isLoading, setIsLoading] = useState(true);
    const [currentAnswer, setCurrentAnswer] = useState('');
    const [isChecking, setIsChecking] = useState(false);
    const [feedback, setFeedback] = useState(null);
    const [error, setError] = useState(null);
    const [results, setResults] = useState([]);
    const [isCompleted, setIsCompleted] = useState(false);
    const [sessionSummary, setSessionSummary] = useState(null);
    const [saveError, setSaveError] = useState(null);
    const actionRef = useRef(null);
    const alive = useRef(false);
    const submission = useRef(null);
    const checking = useRef(false);
    const headingRef = useStageFocus(isLoading ? 'loading' : error ? 'error'
        : isCompleted ? 'result' : exercises.length ? `question-${currentIndex}` : 'empty');

    useEffect(() => {
        const controller = new AbortController();
        let active = true;
        alive.current = true;
        const fetchDynamicExercises = async () => {
            try {
                const response = await api.post('/vocabulary/practice/start', { courseId }, { signal: controller.signal, skipGlobalError: true });
                if (!active) return;
                setAttemptId(response.data.attemptId);
                if (response.data.exercises.length === 0) {
                    setExercises([]);
                } else {
                    setExercises(response.data.exercises);
                }
            } catch (err) {
                if (!active) return;
                console.error("Hiba a dinamikus gyakorlás lekérésekor:", safeError(err));
                setError(err.response?.status === 409 ? "A kurzus megváltozott. Nyisd meg újra a gyakorlást." : "Nem sikerült betölteni a gyakorló feladatokat.");
            } finally {
                if (active) setIsLoading(false);
            }
        };

        fetchDynamicExercises();
        return () => { active = false; alive.current = false; controller.abort(); submission.current?.abort(); };
    }, [courseId]);

    const handleCheckAnswer = async () => {
        if (feedback || isChecking || checking.current || !currentAnswer.trim()) return;
        checking.current = true;
        actionRef.current?.focus();
        const currentEx = exercises[currentIndex];
        const controller = new AbortController();
        submission.current = controller;
        setIsChecking(true);
        setSaveError(null);
        try {
            const response = await api.post('/vocabulary/practice/check', {
                attemptId, exerciseId: currentEx.exerciseId, answer: currentAnswer,
            }, { signal: controller.signal, skipGlobalError: true });
            if (!alive.current || controller.signal.aborted) return;
            const checked = response.data;
            setCurrentAnswer(checked.answer);
            setFeedback(checked.correct
                ? { type: 'success', msg: <>Tökéletes! <span aria-hidden="true">✅</span></> }
                : { type: 'danger', msg: <>Helytelen! A helyes válasz: <span lang={currentEx.answerLang || 'hu'}>{checked.correctAnswer}</span> <span aria-hidden="true">❌</span></> });
            setResults(prev => [...prev.filter(item => item.exerciseId !== currentEx.exerciseId),
                { exerciseId: currentEx.exerciseId, answer: checked.answer }]);
        } catch (err) {
            if (!alive.current || controller.signal.aborted) return;
            if (err.response?.status === 409) { setError('A kurzus megváltozott. Nyisd meg újra a gyakorlást.'); return; }
            console.error('Hiba a válasz ellenőrzésekor:', safeError(err));
            setSaveError('Nem sikerült ellenőrizni a választ. A válaszod megmaradt, próbáld újra az ellenőrzést.');
        } finally {
            checking.current = false;
            if (alive.current && !controller.signal.aborted) setIsChecking(false);
        }
    };

    const handleNext = async () => {
        if (!feedback || isChecking) return;
        if (currentIndex < exercises.length - 1) {
            setCurrentIndex(prev => prev + 1);
            setCurrentAnswer('');
            setFeedback(null);
            return;
        }
        setIsChecking(true);
        setSaveError(null);
        const controller = new AbortController();
        submission.current = controller;
        try {
            const response = await api.post('/vocabulary/practice/submit', {
                attemptId,
                results
            }, { signal: controller.signal, skipGlobalError: true });
            if (!alive.current || controller.signal.aborted) return;
            setSessionSummary(response.data);
            setIsCompleted(true);
            refreshNotifications?.();
        } catch (err) {
            if (!alive.current || controller.signal.aborted) return;
            if (err.response?.status === 409) { setError('A kurzus megváltozott. Nyisd meg újra a gyakorlást.'); return; }
            console.error('Hiba az eredmények mentésekor:', safeError(err));
            setSaveError('Nem sikerült menteni az eredményeket. A válaszaid megmaradtak, próbáld újra a mentést.');
        } finally {
            if (alive.current) setIsChecking(false);
        }
    };

    if (isLoading) {
        return (
            <div className="min-vh-100 d-flex flex-column justify-content-center align-items-center text-light">
                <Spinner animation="border" variant="info" className="mb-3" />
                <h1 ref={headingRef} tabIndex={-1} className="h5">Dinamikus gyakorlás generálása...</h1>
            </div>
        );
    }

    if (error) {
        return (
            <Container className="min-vh-100 d-flex flex-column justify-content-center align-items-center text-center text-light">
                <h1 ref={headingRef} tabIndex={-1} className="h3 text-danger mb-4">{error}</h1>
                <Button variant="outline-secondary" onClick={() => navigate('/dashboard')}>Vissza a Dashboardra</Button>
            </Container>
        );
    }

    if (exercises.length === 0) {
        return (
            <Container className="min-vh-100 d-flex flex-column justify-content-center align-items-center text-center text-light">
                <div aria-hidden="true" className="mb-4" style={{ fontSize: '4rem' }}>🎉</div>
                <h1 ref={headingRef} tabIndex={-1} className="h2 fw-bold mb-3">Nincs elég szó a gyakorláshoz!</h1>
                <p className="text-light opacity-75 mb-4">
                    Gyűjts még szavakat a leckékből vagy a szótárból, hogy indíthass egy dinamikus sessiont.
                </p>
                <Button variant="info" size="lg" className="rounded-pill fw-bold" onClick={() => navigate('/hub')}>
                    Vissza a Szótárba
                </Button>
            </Container>
        );
    }

    if (isCompleted) {
        return (
            <Container className="min-vh-100 d-flex flex-column justify-content-center align-items-center text-center text-light">
                <div aria-hidden="true" className="mb-4" style={{ fontSize: '4rem' }}>🧠</div>
                <h1 ref={headingRef} tabIndex={-1} className="h2 fw-bold mb-3">Dinamikus gyakorlás teljesítve!</h1>
                
                {sessionSummary ? (
                    <div className="bg-dark p-4 rounded-4 border border-secondary mb-4 w-100 shadow-lg" style={{ maxWidth: '400px' }}>
                        <h2 className="h4 text-info fw-bold mb-4">Eredmények</h2>
                        <p className="fs-5 mb-2 d-flex justify-content-between">
                            <span><span aria-hidden="true">✅</span> Helyes:</span>
                            <span className="text-success fw-bold">
                                <span aria-hidden="true">{sessionSummary.correctCount} / {sessionSummary.totalQuestions}</span>
                                <span lang="hu" className="visually-hidden">{sessionSummary.totalQuestions} feladatból {sessionSummary.correctCount} helyes válasz</span>
                            </span>
                        </p>
                        <p className="fs-5 mb-2 d-flex justify-content-between">
                            <span><span aria-hidden="true">⭐</span> Szerzett XP:</span>
                            <span className="text-warning fw-bold">+{sessionSummary.earnedXp}</span>
                        </p>
                        <hr className="border-secondary" />
                        <p className="fs-5 mb-0 d-flex justify-content-between">
                            <span><span aria-hidden="true">🔥</span> Napi Streak:</span>
                            <span className="text-danger fw-bold">{sessionSummary.newStreak} nap</span>
                        </p>
                    </div>
                ) : (
                    <p className="text-light opacity-75 mb-4">
                        Sikeresen végigmentél a vegyes tesztsorozaton. Remek munkát végeztél!
                    </p>
                )}

                <EarnedAchievements awards={sessionSummary?.newAchievements} />
                <Button variant="success" size="lg" className="rounded-pill fw-bold shadow-sm" onClick={() => navigate('/hub')}>
                    Vissza a Szótárba
                </Button>
            </Container>
        );
    }

    const currentEx = exercises[currentIndex];
    const progressPercentage = (currentIndex / exercises.length) * 100;
    const questionLang = currentEx.questionLang || 'hu';
    // Focus announcements may flatten mixed-language descendants into one voice.
    // Keep instructions separate and make the explicitly authored foreign text reachable by Tab.
    const mixedQuestion = Array.isArray(currentEx.questionParts)
        && currentEx.questionParts.every(part => typeof part?.text === 'string' && typeof part?.lang === 'string')
        && currentEx.questionParts.map(part => part.text).join('') === currentEx.question
        && currentEx.questionParts.some(part => part.lang !== questionLang && part.text.trim());

    return (
        <div className="min-vh-100 pb-5 text-light d-flex align-items-center">
            <Container className="d-flex flex-column align-items-center">
                
                {/* Progress bar */}
                <div className="w-100 mb-4" style={{ maxWidth: '600px' }}>
                    <div className="d-flex justify-content-between align-items-center mb-2 fw-bold text-light opacity-75 small">
                        <span>
                            <span aria-hidden="true">Feladat: {currentIndex + 1} / {exercises.length}</span>
                            <span lang="hu" className="visually-hidden">{currentIndex + 1}. feladat, összesen {exercises.length}.</span>
                        </span>
                        <span className="text-uppercase text-info">{exerciseTypeLabel(currentEx.type)}</span>
                    </div>
                    <ProgressBar 
                        now={progressPercentage} 
                        variant="info" 
                        style={{ height: '10px', backgroundColor: '#333' }} 
                        className="rounded-pill border border-secondary" 
                    >
                        <ProgressBar aria-label="Szókincsgyakorlás előrehaladása" now={progressPercentage} variant="info" />
                    </ProgressBar>
                </div>

                {/* Card container */}
                <Card className="shadow-lg border-0 bg-dark text-light rounded-4 w-100 mb-4 p-4 text-center" style={{ maxWidth: '600px' }}>
                    <Card.Body>
                        <h1 ref={headingRef} tabIndex={-1} lang="hu" className={mixedQuestion ? 'h6 fw-bold text-body mb-3' : 'h4 fw-bold text-body mb-4'}>
                            {mixedQuestion ? <>
                                <span>{currentEx.type === 'MULTIPLE_CHOICE' ? 'Válaszd ki a helyes opciót' : currentEx.answerLang === 'hu' ? 'Fordítsd le magyarra' : 'Fordítás'}.</span>
                                <span lang="hu" className="visually-hidden"> {currentIndex + 1}. feladat, összesen {exercises.length}. Az idegen nyelvű szöveg Tab billentyűvel is elérhető.</span>
                            </> : <>
                                <span lang="hu" className="visually-hidden">{currentEx.type === 'MULTIPLE_CHOICE' ? 'Válaszd ki a helyes opciót' : currentEx.type === 'WORD_BANK' ? 'Rakd sorba a szavakat' : 'Fordítás'}. {currentIndex + 1}. feladat, összesen {exercises.length}. </span>
                                <LanguageText text={currentEx.question} lang={questionLang} parts={currentEx.questionParts} />
                            </>}
                        </h1>
                        {mixedQuestion && <h2 lang={questionLang} className="h4 fw-bold text-body mb-4">
                            <LanguageText text={currentEx.question} lang={questionLang} parts={currentEx.questionParts} focusForeignParts />
                        </h2>}

                        {/* RENDER BASED ON EXERCISE TYPE */}
                        {currentEx.type === 'MULTIPLE_CHOICE' && (
                            <MultipleChoiceExercise data={{ options: currentEx.options, answerLang: currentEx.answerLang || 'hu' }} currentAnswer={currentAnswer}
                                onAnswer={setCurrentAnswer} disabled={isChecking || !!feedback} />
                        )}

                        {currentEx.type === 'WORD_BANK' && (
                            <div className="my-4">
                                <WordBankExercise data={{ options: currentEx.options, answerLang: currentEx.answerLang }} currentAnswer={currentAnswer}
                                    onAnswer={setCurrentAnswer} disabled={isChecking || !!feedback} />
                            </div>
                        )}

                        {currentEx.type === 'TRANSLATION' && (
                            <Form.Group className="my-4" controlId="vocabulary-answer">
                                <Form.Label id="vocabulary-answer-label" lang="hu">Válaszod</Form.Label>
                                <Form.Control 
                                    type="text"
                                    aria-labelledby="vocabulary-answer-label"
                                    value={currentAnswer}
                                    onChange={(e) => setCurrentAnswer(e.target.value)}
                                    className="bg-black text-info text-center fs-4 py-3 rounded-pill border-secondary shadow-none fw-bold"
                                    lang={currentEx.answerLang || 'hu'}
                                    disabled={isChecking || !!feedback}
                                    onKeyDown={(e) => {
                                        if (e.key === 'Enter' && currentAnswer.trim() && !isChecking) {
                                            handleCheckAnswer();
                                        }
                                    }}
                                />
                            </Form.Group>
                        )}

                        <LearningFeedback feedback={feedback} error={saveError} />
                    </Card.Body>
                </Card>

                {/* Action button */}
                <div className="w-100 d-flex flex-column gap-3" style={{ maxWidth: '600px' }}>
                        <Button 
                            ref={actionRef}
                            variant="info" 
                            size="lg" 
                            className="rounded-pill fw-bold py-3 text-dark shadow-sm"
                            onClick={feedback ? handleNext : handleCheckAnswer}
                            disabled={!feedback && !currentAnswer.trim()}
                            aria-disabled={isChecking || (!feedback && !currentAnswer.trim())} aria-busy={isChecking}
                        >
                            {isChecking ? <><Spinner size="sm" animation="border" aria-hidden="true" /> {feedback ? 'Mentés folyamatban...' : 'Ellenőrzés folyamatban...'}</>
                                : !feedback ? 'Ellenőrzés' : saveError ? 'Mentés újrapróbálása'
                                : currentIndex < exercises.length - 1 ? 'Következő feladat' : 'Befejezés és mentés'}
                        </Button>
                    <Button 
                        variant="link" 
                        className="text-secondary text-decoration-none" 
                        onClick={() => navigate('/hub')}
                    >
                        Kilépés a Szótárba
                    </Button>
                </div>

            </Container>
        </div>
    );
};

export default VocabularyPractice;
