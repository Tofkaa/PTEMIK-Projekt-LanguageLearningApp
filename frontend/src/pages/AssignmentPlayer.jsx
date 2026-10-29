import { safeError } from '../utils/safeError';
import { VocabularyCourseProvider } from '../context/VocabularyCourseContext';
import { useAuth } from '../context/AuthContext';
import { getAuthEpoch } from '../services/authSession';
/**
 * @file AssignmentPlayer.jsx
 * @description Manages the interactive quiz engine for classroom assignments.
 * Handles countdown timers, immediate feedback mapping, client-side progression, and final API submission.
 */

import { useState, useEffect, useRef, useCallback, useMemo } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { Container, Card, Button, ProgressBar, Spinner, Form } from 'react-bootstrap';
import api from '../services/api';
import { assignmentApi } from '../services/assignmentApi';
import WordBankExercise from '../components/exercises/WordBankExercise.jsx';
import MultipleChoiceExercise from '../components/exercises/MultipleChoiceExercise.jsx';
import ImageChoiceExercise from '../components/exercises/ImageChoiceExercise.jsx';
import ClickableText from '../components/ClickableText.jsx';
import { parseServerDate } from '../utils/dateUtils.js';
import LearningFeedback from '../components/LearningFeedback.jsx';
import TranslationHint from '../components/TranslationHint.jsx';
import DurationText from '../components/DurationText.jsx';
import useStageFocus from '../hooks/useStageFocus';
import AssignmentReview from '../components/AssignmentReview';
import useAssignmentDraft from '../hooks/useAssignmentDraft';

/**
 * @component
 * @returns {React.ReactElement} The active assignment quiz interface.
 */
const AssignmentPlayer = () => {
    const { sessionId } = useParams();
    const { user } = useAuth() || {};
    return <AssignmentLoader key={`${getAuthEpoch()}:${user?.userId}:${sessionId}`} sessionId={sessionId} />;
};

const AssignmentLoader = ({ sessionId }) => {
    const [attempt, setAttempt] = useState(null);
    const [error, setError] = useState('');
    const [reload, setReload] = useState(0);
    useEffect(() => {
        let active = true;
        assignmentApi.getSession(sessionId).then(({ data }) => {
            if (active) { setAttempt(data); setError(''); }
        }).catch(err => {
            if (active) setError(err.response?.data?.message || 'A próbálkozás betöltése nem sikerült. A szerveren mentett válaszaid megmaradtak.');
        });
        return () => { active = false; };
    }, [sessionId, reload]);
    if (!attempt || error) return <Container className="py-5">
        <h1 className="h3">{error ? 'A vizsga nem tölthető be' : 'Vizsga betöltése…'}</h1>
        <LearningFeedback error={error} />
        {error && <Button onClick={() => setReload(value => value + 1)}>Betöltés újrapróbálása</Button>}
    </Container>;
    return <VocabularyCourseProvider courseId={attempt.sessionData?.courseId} courseCode={attempt.sessionData?.courseCode}>
    <AssignmentPlayerSession key={`${sessionId}:${reload}`} attempt={attempt} onReload={() => {
        setAttempt(null); setReload(value => value + 1);
    }} /></VocabularyCourseProvider>;
};

const AssignmentPlayerSession = ({ attempt, onReload }) => {
    const { sessionId } = useParams();
    const navigate = useNavigate();
    const sessionData = attempt.sessionData;
    const details = attempt.assignmentDetails;
    const draft = attempt.draft;

    // --- LOGIC FLAGS ---
    const hasFeedback = details?.hasFeedback === true || sessionData?.hasFeedback === true;
    const allowRetries = hasFeedback && (details?.allowRetries === true || sessionData?.allowRetries === true);

    // --- STATE MANAGEMENT ---
    const [exercises, setExercises] = useState(() => draft ? draft.queue.map((id, index) => ({
        ...sessionData.exercises.find(exercise => exercise.exerciseId === id), isRetry: index >= sessionData.exercises.length,
    })) : sessionData.exercises);
    const [currentIndex, setCurrentIndex] = useState(draft?.currentIndex || 0);
    const [currentAnswer, setCurrentAnswer] = useState(draft?.currentAnswer || '');
    const [collectedAnswers, setCollectedAnswers] = useState(draft?.answers || []);
    
    const [timeLeft, setTimeLeft] = useState(null);
    const [showSummary, setShowSummary] = useState(sessionData.timing?.finished === true);
    const [reviewing, setReviewing] = useState(draft?.reviewing || false);
    const [isSubmitting, setIsSubmitting] = useState(false);

    const [feedback, setFeedback] = useState(null);
    const [isChecking, setIsChecking] = useState(false);
    const [checkError, setCheckError] = useState(null);
    const [submissionError, setSubmissionError] = useState(null);
    const headingRef = useStageFocus(showSummary ? 'summary' : reviewing ? 'review' : `question-${currentIndex}`);
    const submissionInFlight = useRef(false);

    const isAutoSubmitting = useRef(false);
    const latestAutoSubmit = useRef(null);
    const expiryCheckPending = useRef(false);
    const nextExpiryCheck = useRef(0);
    const [timing, setTiming] = useState(sessionData?.timing || null);
    const [serverOffset, setServerOffset] = useState(() => sessionData?.timing?.serverNow
        ? parseServerDate(sessionData.timing.serverNow).getTime() - Date.now() : 0);
    const [timingError, setTimingError] = useState('');
    const [timingNotice, setTimingNotice] = useState('');
    const previousDeadline = useRef(parseServerDate(sessionData?.timing?.deadline)?.getTime() ?? null);
    const latestTimingValue = useRef(sessionData?.timing);
    const latestServerTime = useRef(parseServerDate(sessionData?.timing?.serverNow)?.getTime() || 0);
    const snapshot = useMemo(() => ({ queue: exercises.map(exercise => exercise.exerciseId),
        answers: collectedAnswers, currentIndex, currentAnswer, reviewing }),
    [exercises, collectedAnswers, currentIndex, currentAnswer, reviewing]);
    const initialDraft = useMemo(() => draft ? { queue: draft.queue, answers: draft.answers,
        currentIndex: draft.currentIndex, currentAnswer: draft.currentAnswer, reviewing: draft.reviewing } : null, [draft]);
    const finishRecovered = useCallback(() => setShowSummary(true), []);
    const saving = useAssignmentDraft({ sessionId, snapshot, revision: attempt.revision, initialDraft,
        enabled: !showSummary && !isSubmitting, onFinished: finishRecovered });
    const saveStatus = <div className="mb-3">
        <p role="status" aria-label="Válaszok mentési állapota" aria-live="polite" className="small text-body-secondary mb-1">{saving.status === 'saved'
            ? 'A válaszaid mentve vannak a szerveren.' : saving.status === 'saving' ? 'Válaszok mentése…'
                : 'Vannak még nem mentett változtatások.'}</p>
        <LearningFeedback error={saving.error} />
        {saving.conflict ? <><p>A szerveres állapot betöltése elveti az itteni nem mentett változtatásokat.</p>
            <Button variant="outline-info" onClick={onReload}>Szerveren mentett állapot betöltése</Button></>
            : saving.status === 'error' && <Button variant="outline-info" onClick={() => { void saving.flush(); }}>Mentés újrapróbálása</Button>}
    </div>;

    const readTiming = useCallback(async () => {
        const response = await assignmentApi.getSessionTiming(sessionId);
        const value = response.data;
        const serverTime = parseServerDate(value.serverNow)?.getTime() || 0;
        if (serverTime < latestServerTime.current) return latestTimingValue.current;
        latestServerTime.current = serverTime;
        latestTimingValue.current = value;
        setTiming(value);
        if (value.serverNow) setServerOffset(parseServerDate(value.serverNow).getTime() - Date.now());
        const nextDeadline = parseServerDate(value.deadline)?.getTime() ?? null;
        if (nextDeadline !== previousDeadline.current) setTimingNotice('A tanár frissítette az időbeállításaidat.');
        previousDeadline.current = nextDeadline;
        setTimingError('');
        if (value.finished) setShowSummary(true);
        return value;
    }, [sessionId]);

    useEffect(() => {
        if (!sessionData?.timing || showSummary) return;
        const refresh = () => readTiming().catch(() => setTimingError('Az időbeállítások frissítése nem sikerült. Újrapróbáljuk; a válaszaid megmaradnak.'));
        const timer = setInterval(refresh, 5000);
        const onVisibility = () => { if (document.visibilityState === 'visible') refresh(); };
        document.addEventListener('visibilitychange', onVisibility);
        return () => { clearInterval(timer); document.removeEventListener('visibilitychange', onVisibility); };
    }, [sessionData?.timing, showSummary, readTiming]);

   // --- TIMER ---
    const allowLate = timing ? timing.allowLateSubmission : details?.allowLateSubmission === true;
    const deadlineMs = timing ? parseServerDate(timing.deadline)?.getTime() ?? null
        : details?.timeLimitMinutes && sessionData?.startedAt
            ? parseServerDate(sessionData.startedAt).getTime() + details.timeLimitMinutes * 60000 : null;

    useEffect(() => {
        if (deadlineMs === null || showSummary) return;
        const tick = async () => {
            const remaining = deadlineMs - (Date.now() + serverOffset);
            setTimeLeft(remaining);
            if (remaining <= 0 && !isAutoSubmitting.current && !allowLate && !expiryCheckPending.current && Date.now() >= nextExpiryCheck.current) {
                expiryCheckPending.current = true;
                try {
                    // Recheck with the server before submitting: a teacher may just have extended the attempt.
                    if (sessionData?.timing) {
                        const fresh = await readTiming();
                        if (fresh.finished || fresh.allowLateSubmission || !fresh.deadline
                            || parseServerDate(fresh.deadline).getTime() > parseServerDate(fresh.serverNow).getTime()) return;
                    }
                    isAutoSubmitting.current = true;
                    latestAutoSubmit.current?.();
                } catch {
                    setTimingError('A lejárat szerveroldali ellenőrzése nem sikerült. A válaszaid megmaradnak, újrapróbáljuk.');
                    nextExpiryCheck.current = Date.now() + 5000;
                } finally { expiryCheckPending.current = false; }
            }
        };
        const timer = setInterval(tick, 1000);
        return () => clearInterval(timer);
    }, [deadlineMs, serverOffset, showSummary, allowLate, sessionData?.timing, readTiming]);


    /**
     * Evaluates the current answer or progresses to the next question based on the assignment's rules.
     * Performs API calls for validation if immediate feedback is required.
     * 
     * @async
     * @function handleCheckOrNext
     */
    const handleCheckOrNext = async () => {
        if (isChecking || isSubmitting || saving.conflict || (!allowLate && timeLeft !== null && timeLeft <= 0) || !currentAnswer.trim()) return;
        setCheckError(null);
        
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
            const response = await api.post(`/assignments/sessions/${sessionId}/exercises/${currentExercise.exerciseId}/check`, {
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
            console.error("Hiba az ellenőrzés során:", safeError(err));
            setCheckError('Nem sikerült ellenőrizni a választ. A válaszod megmaradt, próbáld újra.');
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
            setReviewing(true);
        }
    };

    /**
     * Handles automatic submission when the countdown timer reaches zero.
     */
    const handleAutoSubmit = () => {
        setTimingNotice('Az idő lejárt. A vizsga a határidőig szerverre mentett válaszokkal zárul.');
        const answers = [...collectedAnswers];
        const current = exercises[currentIndex];
        if (current && currentAnswer.trim()) {
            const index = answers.findIndex(a => a.exerciseId === current.exerciseId);
            const answer = { exerciseId: current.exerciseId, answer: currentAnswer.trim(), retried: index >= 0 };
            if (index >= 0) answers[index] = answer;
            else answers.push(answer);
        }
        submitAssignment(answers);
    };

    // The timer always uses the latest answers rather than the closure from its first tick.
    useEffect(() => { latestAutoSubmit.current = handleAutoSubmit; });

    /**
     * Submits the compiled array of answers to the backend API.
     * 
     * @async
     * @function submitAssignment
     * @param {Array<Object>} answersToSubmit - Array containing the user's finalized answers.
     */
    const submitAssignment = async (answersToSubmit) => {
        if (submissionInFlight.current || showSummary) return;
        submissionInFlight.current = true;
        setIsSubmitting(true);
        setSubmissionError(null);
        try {
            const saved = await saving.flush();
            if (!saved.ok || saved.finished) return;
            const payload = answersToSubmit.map(ans => ({
                exerciseId: ans.exerciseId,
                answer: ans.answer,
                isRetry: ans.isRetry,
                retried: ans.retried
            }));

            await assignmentApi.submitAssignment(sessionId, payload, saved.revision);

            setShowSummary(true);
            
           } catch (error) {
            console.error('Nem sikerült beküldeni a tesztet.', safeError(error));
            if (error.response?.status === 409) saving.markConflict();
            setSubmissionError(error.response?.data?.message || 'Nem sikerült beküldeni a tesztet. Ellenőrizd a kapcsolatod, majd próbáld újra.');
        } finally {
            submissionInFlight.current = false;
            setIsSubmitting(false);
        }
    };

    if (!exercises.length && !showSummary) return <Container className="py-5">
        <LearningFeedback error="Ehhez a próbálkozáshoz nem érhető el feladat. Kérj segítséget a tanártól." />
    </Container>;

    const currentExercise = exercises[currentIndex];
    const progressPercentage = (currentIndex / exercises.length) * 100;
    
    // Disable inputs while verifying or if a definitive feedback is shown
    const expired = !allowLate && timeLeft !== null && timeLeft <= 0;
    const isInputDisabled = isChecking || isSubmitting || saving.conflict || expired || !!(hasFeedback && feedback && feedback.type !== 'warning');

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
            buttonText = 'Válaszok áttekintése';
            buttonVariant = 'success';
        }
    }

    // --- SUMMARY SCREEN ---
    if (showSummary) {
        return (
            <div className="min-vh-100 pb-5 text-light d-flex align-items-center bg-darker animate-fade-in">
                <Container>
                    <Card className="text-center p-5 bg-dark text-light border-success shadow-lg" style={{ maxWidth: '600px', margin: '0 auto' }}>
                        <div aria-hidden="true" className="display-1 mb-4">🎉</div>
                        <h1 ref={headingRef} tabIndex={-1} className="h2 fw-bold text-success mb-3">Sikeres Beküldés!</h1>
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

    if (reviewing) {
        const hasEmptyAnswer = exercises.some(exercise => !collectedAnswers.find(answer => answer.exerciseId === exercise.exerciseId)?.answer.trim());
        return <Container className="py-4 text-light">
            <h1 ref={headingRef} tabIndex={-1} className="h2">Válaszok áttekintése</h1>
            {saveStatus}
            <p>A beadás előtt ellenőrizd a válaszaidat. Bármelyik feladat válaszát javíthatod. Az esetleges időkorlát az áttekintés alatt is érvényes.</p>
            {deadlineMs !== null && timeLeft !== null && <p role="timer" aria-live="off"><DurationText
                seconds={timeLeft / 1000} overtime={timeLeft < 0} label={timeLeft < 0 ? 'Időtúllépés' : 'Hátralévő idő'} /></p>}
            <div role="status" aria-atomic="true">{timingNotice}</div>
            <AssignmentReview exercises={exercises} answers={collectedAnswers} submitting={isSubmitting || saving.conflict || expired}
                timingNotice={timingNotice} error={isSubmitting ? null : submissionError || timingError}
                timeLeft={deadlineMs !== null ? timeLeft : null} onAnswerChange={(exerciseId, answer) => {
                    if (!isSubmitting && !saving.conflict && !expired) setCollectedAnswers(previous => previous.map(item => item.exerciseId === exerciseId ? { ...item, answer } : item));
                }} />
            <LearningFeedback error={isSubmitting ? null : submissionError || timingError} />
            {hasEmptyAnswer && <p>Beadás előtt töltsd ki az üresen hagyott válaszokat.</p>}
            <Button variant="success" aria-disabled={isSubmitting || hasEmptyAnswer || saving.conflict} aria-busy={isSubmitting}
                onClick={() => { if (!isSubmitting && !hasEmptyAnswer && !saving.conflict) submitAssignment(collectedAnswers); }}>
                {isSubmitting ? 'Beküldés folyamatban...' : submissionError ? 'Beadás újrapróbálása' : 'Végleges beadás'}
            </Button>
        </Container>;
    }

    const TaskHeading = 'h1';
    return (
        <div className="min-vh-100 pb-5 text-light d-flex align-items-center">
            <Container>
                {saveStatus}
                {/* Header Section */}
                <div aria-live="polite" aria-atomic="true">{timingNotice}</div>
                <div className="mb-4">
                    <div className="d-flex justify-content-between align-items-center mb-2 fw-bold text-light opacity-75 small">
                        <span style={{ width: '60px' }}><span aria-hidden="true">{currentIndex + 1} / {exercises.length}</span><span lang="hu" className="visually-hidden">{currentIndex + 1}. feladat, összesen {exercises.length}.</span></span>
                        {deadlineMs !== null && timeLeft !== null && (
                            <span role="timer" aria-live="off" aria-labelledby="assignment-time-left" className={`fs-6 px-3 py-1 bg-dark rounded-pill border ${timeLeft < 0 ? 'border-danger text-light bg-danger' : (timeLeft < 60000 ? 'border-danger text-danger' : 'border-secondary text-info')}`}>
                                <span aria-hidden="true">⏱️ {timeLeft < 0 ? 'Túllépés: ' : ''}</span><DurationText id="assignment-time-left" seconds={timeLeft / 1000} overtime={timeLeft < 0} label={timeLeft < 0 ? 'Időtúllépés' : 'Hátralévő idő'} />
                            </span>
                        )}
                        <span className="text-end" style={{ width: '60px' }}>{Math.round(progressPercentage)}%</span>
                    </div>
                    <ProgressBar style={{ height: '10px', backgroundColor: '#333' }} className="rounded-pill border border-secondary">
                        <ProgressBar aria-label="Feladatsor előrehaladása" now={progressPercentage} variant="info" />
                    </ProgressBar>
                </div>

                <Card className="shadow-lg border-0 bg-dark text-light">
                    <Card.Body className="p-4 p-md-5 text-center">
                        <TaskHeading id="assignment-task-instruction" lang="hu" ref={headingRef} tabIndex={0} className="h4 mb-4 text-body fw-bold d-flex justify-content-center align-items-center gap-2">
                            {currentExercise.type === 'WORD_BANK' ? 'Rakd sorba a szavakat' : currentExercise.type === 'MULTIPLE_CHOICE' ? 'Válaszd ki a helyes opciót' : currentExercise.type === 'IMAGE_CHOICE' ? 'Válaszd ki a képhez illő szót' : 'Fordítás'}
                            <span className="visually-hidden">. {currentIndex + 1}. feladat, összesen {exercises.length}.</span>
                        </TaskHeading>

                        {currentExercise.content?.question && (
                            (() => {
                                const cleanQuestion = currentExercise.content.question.replace('Translate: ', '').replace('Translate to English: ', '');
                                const isEnglishQuestion = currentExercise.content?.questionLang === 'en';
                                const isDictAllowedByAssignment = details?.allowDictionary ?? true; 
                                
                                const showDictionary = isEnglishQuestion && isDictAllowedByAssignment;
                                const showHint = !isEnglishQuestion && currentExercise.content?.hint && isDictAllowedByAssignment;

                                return (
                                    <div className="mb-4 text-center">
                                        <h2 tabIndex={0} lang={currentExercise.content?.questionLang || 'hu'} className="fs-4 fw-bold p-3 border border-secondary rounded bg-black bg-opacity-25 mx-auto text-body" style={{ maxWidth: '800px' }}>
                                                <ClickableText 
                                                    text={cleanQuestion} 
                                                    lang={currentExercise.content?.questionLang}
                                                    languageParts={currentExercise.content?.questionParts}
                                                    disabled={!showDictionary}
                                                    source="LESSON" 
                                                    hint={currentExercise.content.hint} 
                                                    hintDirection={currentExercise.content.hintDirection}
                                                />
                                        </h2>
                                        {showDictionary && <p lang="hu" className="small text-body-secondary mt-3">Az aláhúzott szavak fordítása Enterrel vagy Szóközzel megnyitható.</p>}

                                        {showHint && (
                                            <div tabIndex={0} role="note" aria-labelledby="assignment-hint-title" aria-describedby="assignment-hint-content" className="d-inline-block text-start mt-2 px-3 py-2 rounded shadow-sm" style={{ backgroundColor: 'rgba(13, 202, 240, 0.1)', borderLeft: '4px solid #0dcaf0' }}>
                                                <span id="assignment-hint-title" lang="hu" className="small text-info fw-bold text-uppercase" style={{ letterSpacing: '1px' }}>
                                                    <i className="bi bi-lightbulb me-2 text-warning"></i>Tipp a fordításhoz
                                                </span>
                                                <div id="assignment-hint-content" className="mt-1 small fw-medium text-body">
                                                    <TranslationHint content={currentExercise.content} />
                                                </div>
                                            </div>
                                        )}
                                    </div>
                                );
                            })()
                        )}

                        {/* DYNAMIC EXERCISE RENDERING */}
                        {currentExercise.type === 'IMAGE_CHOICE' && <ImageChoiceExercise exercise={currentExercise} currentAnswer={currentAnswer} onAnswer={setCurrentAnswer} disabled={isInputDisabled} />}
                        {currentExercise.type === 'WORD_BANK' && <WordBankExercise data={currentExercise.content} currentAnswer={currentAnswer} onAnswer={setCurrentAnswer} disabled={isInputDisabled} />}
                        {currentExercise.type === 'MULTIPLE_CHOICE' && <MultipleChoiceExercise data={currentExercise.content} currentAnswer={currentAnswer} onAnswer={setCurrentAnswer} disabled={isInputDisabled} />}
                        {currentExercise.type === 'TRANSLATION' && (
                            <Form.Group className="mb-5 text-start" controlId="assignment-answer">
                                <Form.Label id="assignment-answer-label" lang="hu">Válaszod</Form.Label>
                                <Form.Control as="textarea" rows={3} aria-labelledby="assignment-answer-label" lang={currentExercise.content?.answerLang} value={currentAnswer} onChange={(e) => setCurrentAnswer(e.target.value)} className="fs-5 bg-dark text-light border-secondary shadow-none" disabled={isInputDisabled} />
                            </Form.Group>
                        )}

                        {/* VISUAL FEEDBACK */}
                        <LearningFeedback feedback={hasFeedback ? feedback : null} error={isSubmitting ? null : checkError || submissionError || timingError} />
                        {submissionError && <Button autoFocus variant="outline-danger" aria-disabled={isSubmitting}
                            aria-busy={isSubmitting} onClick={() => { if (!isSubmitting) submitAssignment(collectedAnswers); }}>
                            {isSubmitting ? 'Beküldés folyamatban...' : 'Beadás újrapróbálása'}
                        </Button>}
                        
                        <div className="d-flex justify-content-end mt-5">
                            <Button variant={buttonVariant} className="px-5 fw-bold" onClick={handleCheckOrNext} disabled={currentAnswer.trim().length === 0 && !isSubmitting}
                                aria-disabled={isChecking || isSubmitting || !currentAnswer.trim()} aria-busy={isChecking || isSubmitting}>
                                {(isChecking || isSubmitting) ? <><Spinner size="sm" animation="border" aria-hidden="true" /> {isSubmitting ? 'Beküldés folyamatban...' : 'Ellenőrzés folyamatban...'}</> : buttonText}
                            </Button>
                        </div>
                    </Card.Body>
                </Card>
            </Container>
        </div>
    );
};

export default AssignmentPlayer;
