import React, { useState, useEffect } from 'react';
import { Container, Card, Button, ProgressBar, Spinner, Form } from 'react-bootstrap';
import { useNavigate } from 'react-router-dom';
import api from '../services/api.jsx';

const VocabularyPractice = () => {
    const navigate = useNavigate();
    
    const [exercises, setExercises] = useState([]);
    const [currentIndex, setCurrentIndex] = useState(0);
    const [isLoading, setIsLoading] = useState(true);
    const [currentAnswer, setCurrentAnswer] = useState('');
    const [isChecking, setIsChecking] = useState(false);
    const [feedback, setFeedback] = useState(null);
    const [error, setError] = useState(null);
    const [isCompleted, setIsCompleted] = useState(false);

    useEffect(() => {
        const fetchDynamicExercises = async () => {
            try {
                // Lekérdjük az új dinamikus feladatsort a backendről
                const response = await api.get('/vocabulary/practice/generate');
                if (response.data.length === 0) {
                    setExercises([]);
                } else {
                    setExercises(response.data);
                }
            } catch (err) {
                console.error("Hiba a dinamikus gyakorlás lekérésekor:", err);
                setError("Nem sikerült betölteni a gyakorló feladatokat.");
            } finally {
                setIsLoading(false);
            }
        };

        fetchDynamicExercises();
    }, []);

    const handleCheckAnswer = async () => {
        const currentEx = exercises[currentIndex];
        const userClean = currentAnswer.trim().toLowerCase();
        const correctClean = currentEx.correctAnswer.trim().toLowerCase();

        const isCorrect = userClean === correctClean;

        if (isCorrect) {
            setFeedback({ type: 'success', msg: 'Tökéletes! ✅' });
        } else {
            setFeedback({ type: 'danger', msg: `Helytelen! A helyes válasz: ${currentEx.correctAnswer} ❌` });
        }

        setIsChecking(true);
        
        // Itt opcionálisan hívhatod a recordPractice-t is, ha a backend SRS-t frissít időközben
        setTimeout(() => {
            setIsChecking(false);
            if (currentIndex < exercises.length - 1) {
                setCurrentIndex(prev => prev + 1);
                setCurrentAnswer('');
                setFeedback(null);
            } else {
                setIsCompleted(true);
            }
        }, 1500);
    };

    if (isLoading) {
        return (
            <div className="min-vh-100 d-flex flex-column justify-content-center align-items-center text-light">
                <Spinner animation="border" variant="info" className="mb-3" />
                <h5>Dinamikus gyakorlás generálása...</h5>
            </div>
        );
    }

    if (error) {
        return (
            <Container className="min-vh-100 d-flex flex-column justify-content-center align-items-center text-center text-light">
                <h3 className="text-danger mb-4">{error}</h3>
                <Button variant="outline-light" onClick={() => navigate('/dashboard')}>Vissza a Dashboardra</Button>
            </Container>
        );
    }

    if (exercises.length === 0) {
        return (
            <Container className="min-vh-100 d-flex flex-column justify-content-center align-items-center text-center text-light">
                <div className="mb-4" style={{ fontSize: '4rem' }}>🎉</div>
                <h2 className="fw-bold mb-3">Nincs elég szó a gyakorláshoz!</h2>
                <p className="text-light opacity-75 mb-4">
                    Gyűjts még szavakat a leckékből vagy a szótárból, hogy indíthass egy dinamikus sessiont.
                </p>
                <Button variant="info" size="lg" className="rounded-pill fw-bold" onClick={() => navigate('/dashboard')}>
                    Vissza a Dashboardra
                </Button>
            </Container>
        );
    }

    if (isCompleted) {
        return (
            <Container className="min-vh-100 d-flex flex-column justify-content-center align-items-center text-center text-light">
                <div className="mb-4" style={{ fontSize: '4rem' }}>🧠</div>
                <h2 className="fw-bold mb-3 text-success">Dinamikus gyakorlás teljesítve!</h2>
                <p className="text-light opacity-75 mb-4">
                    Sikeresen végigmentél a vegyes tesztsorozaton. Remek munkát végeztél!
                </p>
                <Button variant="success" size="lg" className="rounded-pill fw-bold" onClick={() => navigate('/dashboard')}>
                    Befejezés
                </Button>
            </Container>
        );
    }

    const currentEx = exercises[currentIndex];
    const progressPercentage = (currentIndex / exercises.length) * 100;

    return (
        <div className="min-vh-100 pb-5 text-light d-flex align-items-center">
            <Container className="d-flex flex-column align-items-center">
                
                {/* Progress bar */}
                <div className="w-100 mb-4" style={{ maxWidth: '600px' }}>
                    <div className="d-flex justify-content-between align-items-center mb-2 fw-bold text-light opacity-75 small">
                        <span>Feladat: {currentIndex + 1} / {exercises.length}</span>
                        <span className="text-uppercase text-info">{currentEx.type}</span>
                    </div>
                    <ProgressBar 
                        now={progressPercentage} 
                        variant="info" 
                        style={{ height: '10px', backgroundColor: '#333' }} 
                        className="rounded-pill border border-secondary" 
                    />
                </div>

                {/* Card container */}
                <Card className="shadow-lg border-0 bg-dark text-light rounded-4 w-100 mb-4 p-4 text-center" style={{ maxWidth: '600px' }}>
                    <Card.Body>
                        <h4 className="fw-bold text-light mb-4">{currentEx.question}</h4>

                        {/* RENDER BASED ON EXERCISE TYPE */}
                        {currentEx.type === 'MULTIPLE_CHOICE' && (
                            <div className="d-grid gap-3 my-4">
                                {currentEx.options.map((opt, idx) => (
                                    <Button 
                                        key={idx}
                                        variant={currentAnswer === opt ? "info" : "outline-secondary"}
                                        className="py-3 fw-bold rounded-pill text-light"
                                        onClick={() => setCurrentAnswer(opt)}
                                        disabled={isChecking || feedback}
                                    >
                                        {opt}
                                    </Button>
                                ))}
                            </div>
                        )}

                        {currentEx.type === 'WORD_BANK' && (
                            <div className="my-4">
                                <div className="d-flex flex-wrap justify-content-center gap-2 mb-4">
                                    {currentEx.options.map((word, idx) => (
                                        <Button 
                                            key={idx} 
                                            variant="outline-info" 
                                            className="fw-bold px-3 py-2 rounded-pill"
                                            onClick={() => setCurrentAnswer(prev => prev ? `${prev} ${word}` : word)}
                                            disabled={isChecking || feedback}
                                        >
                                            {word}
                                        </Button>
                                    ))}
                                </div>
                                <Form.Control 
                                    type="text" 
                                    readOnly 
                                    value={currentAnswer} 
                                    placeholder="Kattints a szavakra fent..." 
                                    className="bg-black text-info text-center fw-bold fs-5 border-secondary"
                                />
                                <div className="mt-2">
                                    <Button size="sm" variant="outline-danger" onClick={() => setCurrentAnswer('')}>Törlés</Button>
                                </div>
                            </div>
                        )}

                        {currentEx.type === 'TRANSLATION' && (
                            <Form.Group className="my-4">
                                <Form.Control 
                                    type="text"
                                    placeholder="Írd be a fordítást..."
                                    value={currentAnswer}
                                    onChange={(e) => setCurrentAnswer(e.target.value)}
                                    className="bg-black text-info text-center fs-4 py-3 rounded-pill border-secondary shadow-none fw-bold"
                                    autoFocus
                                    disabled={isChecking || feedback}
                                />
                            </Form.Group>
                        )}

                        {feedback && (
                            <div className={`mt-3 p-3 rounded-3 fw-bold fs-5 bg-${feedback.type} bg-opacity-25 border border-${feedback.type}`}>
                                {feedback.msg}
                            </div>
                        )}
                    </Card.Body>
                </Card>

                {/* Action button */}
                <div className="w-100 d-flex flex-column gap-3" style={{ maxWidth: '600px' }}>
                    {!feedback && (
                        <Button 
                            variant="info" 
                            size="lg" 
                            className="rounded-pill fw-bold py-3 text-dark shadow-sm"
                            onClick={handleCheckAnswer}
                            disabled={!currentAnswer.trim() || isChecking}
                        >
                            {isChecking ? <Spinner size="sm" animation="border" /> : 'Ellenőrzés'}
                        </Button>
                    )}
                    <Button 
                        variant="link" 
                        className="text-secondary text-decoration-none" 
                        onClick={() => navigate('/dashboard')}
                    >
                        Kilépés a Dashboardra
                    </Button>
                </div>

            </Container>
        </div>
    );
};

export default VocabularyPractice;