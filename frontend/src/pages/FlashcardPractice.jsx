import React, { useState, useEffect } from 'react';
import { Container, Card, Button, ProgressBar, Spinner } from 'react-bootstrap';
import { useNavigate } from 'react-router-dom';
import api from '../services/api.jsx';

const FlashcardPractice = () => {
    const navigate = useNavigate();
    
    const [words, setWords] = useState([]);
    const [currentIndex, setCurrentIndex] = useState(0);
    const [isLoading, setIsLoading] = useState(true);
    const [isFlipped, setIsFlipped] = useState(false);
    const [error, setError] = useState(null);

    useEffect(() => {
        const fetchAllWords = async () => {
            try {
                const response = await api.get('/vocabulary/hub');

                const shuffled = [...response.data].sort(() => Math.random() - 0.5);
                setWords(shuffled);
            } catch (err) {
                console.error("Hiba a szavak lekérésekor:", err);
                setError("Nem sikerült betölteni a szótárat.");
            } finally {
                setIsLoading(false);
            }
        };

        fetchAllWords();
    }, []);

    const handleNextCard = () => {
        if (currentIndex < words.length - 1) {
            setCurrentIndex(prev => prev + 1);
            setIsFlipped(false);
        } else {
            navigate('/hub');
        }
    };

    if (isLoading) {
        return (
            <div className="min-vh-100 d-flex flex-column justify-content-center align-items-center text-light">
                <Spinner animation="border" variant="info" className="mb-3" />
                <h5>Kártyák keverése...</h5>
            </div>
        );
    }

    if (error || words.length === 0) {
        return (
            <Container className="min-vh-100 d-flex flex-column justify-content-center align-items-center text-center text-light">
                <h3 className="text-danger mb-4">{error || "Nincsenek még szavaid a gyakorláshoz!"}</h3>
                <Button variant="outline-light" onClick={() => navigate('/hub')}>Vissza a Szótárba</Button>
            </Container>
        );
    }

    const currentWord = words[currentIndex];
    const progressPercentage = ((currentIndex) / words.length) * 100;

    return (
        <div className="min-vh-100 pb-5 text-light d-flex align-items-center">
            <Container className="d-flex flex-column align-items-center">
                
                {/* Header and Progress Bar */}
                <div className="w-100 mb-4" style={{ maxWidth: '600px' }}>
                    <div className="d-flex justify-content-between align-items-center mb-2 fw-bold text-light opacity-75 small">
                        <span>{currentIndex + 1} / {words.length} kártya</span>
                        <span className="text-info text-uppercase" style={{ letterSpacing: '1px' }}>Szabad Mód</span>
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
                        transition: 'all 0.3s ease-in-out',
                        cursor: 'pointer'
                    }}
                    onClick={() => !isFlipped && setIsFlipped(true)}
                >
                    <Card.Body className="d-flex flex-column justify-content-center align-items-center p-5">
                        <span className="text-secondary fw-bold text-uppercase mb-4 d-block" style={{ letterSpacing: '2px', fontSize: '0.9rem' }}>
                            {isFlipped ? 'Jelentés' : 'Kattints a kártyára a fordításért!'}
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
                    <Button 
                        variant={isFlipped ? "success" : "info"} 
                        size="lg" 
                        className="rounded-pill fw-bold py-3 shadow-sm text-dark"
                        onClick={isFlipped ? handleNextCard : () => setIsFlipped(true)}
                    >
                        {isFlipped ? 'Következő kártya ➔' : 'Megnézem a választ'}
                    </Button>
                    
                    <Button 
                        variant="link" 
                        className="text-secondary text-decoration-none mt-2" 
                        onClick={() => navigate('/hub')}
                    >
                        Vissza a Szótárba
                    </Button>
                </div>
            </Container>
        </div>
    );
};

export default FlashcardPractice;