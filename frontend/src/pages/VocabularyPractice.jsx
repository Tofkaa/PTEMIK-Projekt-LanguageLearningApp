import React, { useState, useEffect } from 'react';
import { Container, Card, Button, ProgressBar, Spinner, Badge } from 'react-bootstrap';
import { useNavigate } from 'react-router-dom';
import { vocabularyApi } from '../services/vocabularyApi';
const VocabularyPractice = () => {
    const navigate = useNavigate();
    
    const [dueWords, setDueWords] = useState([]);
    const [currentIndex, setCurrentIndex] = useState(0);
    const [isLoading, setIsLoading] = useState(true);
    const [isFlipped, setIsFlipped] = useState(false);
    const [isSubmitting, setIsSubmitting] = useState(false);
    const [error, setError] = useState(null);
    const [isCompleted, setIsCompleted] = useState(false);

    useEffect(() => {
        const fetchDueWords = async () => {
            try {
                const response = await vocabularyApi.getDueVocabulary();
                setDueWords(response.data);
            } catch (err) {
                console.error("Hiba a szavak lekérésekor:", err);
                setError("Nem sikerült betölteni a napi szavakat.");
            } finally {
                setIsLoading(false);
            }
        };

        fetchDueWords();
    }, []);

    const handlePractice = async (isCorrect) => {
        setIsSubmitting(true);
        const currentWord = dueWords[currentIndex];

        try {
            await vocabularyApi.recordPractice(currentWord.vocabularyId, isCorrect);
            
            if (currentIndex < dueWords.length - 1) {
                setCurrentIndex(prev => prev + 1);
                setIsFlipped(false); 
            } else {
                setIsCompleted(true);
            }
        } catch (err) {
            console.error("Hiba az eredmény mentésekor:", err);
            // TODO toast error msg
        } finally {
            setIsSubmitting(false);
        }
    };

    if (isLoading) {
        return (
            <div className="min-vh-100 d-flex flex-column justify-content-center align-items-center text-light">
                <Spinner animation="border" variant="info" className="mb-3" />
                <h5>Szavak betöltése...</h5>
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


    if (dueWords.length === 0) {
        return (
            <Container className="min-vh-100 d-flex flex-column justify-content-center align-items-center text-center text-light">
                <div className="mb-4" style={{ fontSize: '4rem' }}>🎉</div>
                <h2 className="fw-bold mb-3">Minden szót kikérdeztünk mára!</h2>
                <p className="text-light opacity-75 mb-4">
                    Térj vissza holnap az újabb ismétlésekért, vagy tanulj új leckéket, hogy bővítsd a szótárad.
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
                <h2 className="fw-bold mb-3 text-success">Gyakorlás teljesítve!</h2>
                <p className="text-light opacity-75 mb-4">
                    Sikeresen átismételtél {dueWords.length} szót. A Leitner-motor beállította a következő időpontokat.
                </p>
                <Button variant="success" size="lg" className="rounded-pill fw-bold" onClick={() => navigate('/dashboard')}>
                    Befejezés
                </Button>
            </Container>
        );
    }

    // Active card rendering
    const currentWord = dueWords[currentIndex];
    const progressPercentage = ((currentIndex) / dueWords.length) * 100;

    return (
        <div className="min-vh-100 pb-5 text-light d-flex align-items-center">
            <Container className="d-flex flex-column align-items-center">
                
                {/* Header and Progress Bar */}
                <div className="w-100 mb-4" style={{ maxWidth: '600px' }}>
                    <div className="d-flex justify-content-between align-items-center mb-2 fw-bold text-light opacity-75 small">
                        <span>{currentIndex + 1} / {dueWords.length} szó</span>
                        <span>Szint: {currentWord.srsLevel}</span>
                    </div>
                    <ProgressBar 
                        now={progressPercentage} 
                        variant="info" 
                        style={{ height: '10px', backgroundColor: '#333' }} 
                        className="rounded-pill border border-secondary" 
                    />
                </div>

                {/* Card */}
                <Card 
                    className="shadow-lg border-0 bg-dark text-light rounded-4 w-100 mb-5 text-center" 
                    style={{ 
                        maxWidth: '600px', 
                        minHeight: '300px',
                        borderWidth: '2px',
                        boxShadow: isFlipped ? '0 0 20px rgba(25, 135, 84, 0.15)' : '0 0 20px rgba(13, 202, 240, 0.15)',
                        transition: 'all 0.3s ease-in-out'
                    }}
                >
                    <Card.Body className="d-flex flex-column justify-content-center align-items-center p-5">
                        <span className="text-secondary fw-bold text-uppercase mb-4 d-block" style={{ letterSpacing: '2px', fontSize: '0.9rem' }}>
                            {isFlipped ? 'Jelentés' : 'Mit jelent ez a szó?'}
                        </span>
                        
                   
                        <h1 className="display-4 fw-bold mb-4 text-info text-capitalize">
                            {currentWord.word}
                        </h1>

                
                        {isFlipped && (
                            <div className="mt-2 p-3 bg-black bg-opacity-25 rounded-3 w-100 border border-secondary">
                                <h2 className="fw-bold text-success text-capitalize mb-0">
                                    {currentWord.translation}
                                </h2>
                            </div>
                        )}
                    </Card.Body>
                </Card>

                {/* Buttons */}
                <div className="w-100 d-flex flex-column gap-3" style={{ maxWidth: '600px' }}>
                    {!isFlipped ? (
                        <Button 
                            variant="info" 
                            size="lg" 
                            className="rounded-pill fw-bold py-3 text-dark shadow-sm"
                            onClick={() => setIsFlipped(true)}
                        >
                            Megnézem a választ
                        </Button>
                    ) : (
                        <div className="d-flex gap-3">
                            <Button 
                                variant="danger" 
                                size="lg" 
                                className="w-50 rounded-pill fw-bold py-3 shadow-sm"
                                onClick={() => handlePractice(false)}
                                disabled={isSubmitting}
                            >
                                {isSubmitting ? <Spinner size="sm" animation="border" /> : 'Nem tudtam'}
                            </Button>
                            <Button 
                                variant="success" 
                                size="lg" 
                                className="w-50 rounded-pill fw-bold py-3 shadow-sm"
                                onClick={() => handlePractice(true)}
                                disabled={isSubmitting}
                            >
                                {isSubmitting ? <Spinner size="sm" animation="border" /> : 'Tudtam'}
                            </Button>
                        </div>
                    )}
                    
                    <Button 
                        variant="link" 
                        className="text-secondary text-decoration-none mt-2" 
                        onClick={() => navigate('/dashboard')}
                    >
                        Befejezés és kilépés
                    </Button>
                </div>

            </Container>
        </div>
    );
};

export default VocabularyPractice;