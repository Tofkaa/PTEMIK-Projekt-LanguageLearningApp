import { safeError } from '../utils/safeError';
import { useState, useEffect, useId, useRef } from 'react';
import { Container, Card, Button, ProgressBar, Spinner } from 'react-bootstrap';
import { useNavigate } from 'react-router-dom';
import api from '../services/api.jsx';
import { useAuth } from '../context/AuthContext';
import useStageFocus from '../hooks/useStageFocus';
import { getAuthEpoch } from '../services/authSession';

const FlashcardPractice = () => {
    const { user } = useAuth();
    return <FlashcardSession key={JSON.stringify([getAuthEpoch(), user?.userId, user?.activeCourseCode])} />;
};

const FlashcardSession = () => {
    const navigate = useNavigate();
    
    const [words, setWords] = useState([]);
    const [currentIndex, setCurrentIndex] = useState(0);
    const [isLoading, setIsLoading] = useState(true);
    const [isFlipped, setIsFlipped] = useState(false);
    const [error, setError] = useState(null);
    const { user } = useAuth();
    const id = useId();
    const actionRef = useRef(null);
    const headingRef = useStageFocus(isLoading ? 'loading' : error ? 'error'
        : words.length ? `card-${currentIndex}` : 'empty');

    const reveal = () => {
        actionRef.current?.focus();
        setIsFlipped(true);
    };

    useEffect(() => {
        if (isFlipped) actionRef.current?.focus();
    }, [isFlipped]);

    const activateWithKeyboard = event => {
        if (event.key === 'Enter' || event.key === ' ') {
            event.preventDefault();
            if (!event.repeat) event.currentTarget.click();
        }
    };

    useEffect(() => {
        const controller = new AbortController();
        let active = true;
        const fetchAllWords = async () => {
            try {
                const response = await api.get('/vocabulary/hub', { signal: controller.signal });
                if (!active) return;

                const shuffled = [...response.data].sort(() => Math.random() - 0.5);
                setWords(shuffled);
            } catch (err) {
                if (!active) return;
                console.error("Hiba a szavak lekérésekor:", safeError(err));
                setError("Nem sikerült betölteni a szótárat.");
            } finally {
                if (active) setIsLoading(false);
            }
        };

        fetchAllWords();
        return () => { active = false; controller.abort(); };
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
                <Spinner animation="border" variant="info" className="mb-3" aria-hidden="true" />
                <h1 ref={headingRef} tabIndex={-1} className="h5">Kártyák keverése...</h1>
            </div>
        );
    }

    if (error || words.length === 0) {
        return (
            <Container className="min-vh-100 d-flex flex-column justify-content-center align-items-center text-center text-light">
                <h1 ref={headingRef} tabIndex={-1} className="h3 text-body mb-4">{error || "Nincsenek még szavaid a gyakorláshoz!"}</h1>
                <Button variant="outline-secondary" onClick={() => navigate('/hub')}>Vissza a Szótárba</Button>
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
                        <span><span aria-hidden="true">{currentIndex + 1} / {words.length} kártya</span><span lang="hu" className="visually-hidden">{currentIndex + 1}. kártya, összesen {words.length}.</span></span>
                        <span className="text-info text-uppercase" style={{ letterSpacing: '1px' }}>Szabad Mód</span>
                    </div>
                    <ProgressBar
                        style={{ height: '10px', backgroundColor: '#333' }} 
                        className="rounded-pill border border-secondary" 
                    >
                        <ProgressBar aria-label="Szókártyás gyakorlás előrehaladása" now={progressPercentage} variant="info" />
                    </ProgressBar>
                </div>

                {/* Card */}
                <Card className="flashcard-surface shadow-lg rounded-4 w-100 mb-5 text-center">
                    <Card.Body className="d-flex flex-column justify-content-center align-items-center p-5">
                        <span className="text-body-secondary fw-bold text-uppercase mb-4 d-block" style={{ letterSpacing: '2px', fontSize: '0.9rem' }}>
                            {isFlipped ? 'Jelentés' : 'Fedd fel a jelentést a kártyával vagy az alábbi gombbal!'}
                        </span>
                        
                        <h1 ref={headingRef} tabIndex={-1} className="flashcard-word display-4 fw-bold mb-4 text-capitalize">
                            <span lang="hu" className="visually-hidden">{currentIndex + 1}. kártya, összesen {words.length}. </span>
                            <span id={`${id}-word`} lang={user?.activeCourseCode}>{currentWord.word}</span>
                        </h1>
                        <button type="button" lang={user?.activeCourseCode} className="flashcard-hitarea"
                            onClick={reveal} onKeyDown={activateWithKeyboard} disabled={isFlipped}
                            aria-expanded={isFlipped} aria-controls={`${id}-translation`}
                            aria-labelledby={`${id}-word`} />

                        <div id={`${id}-translation`} role="status" aria-atomic="true" className="w-100">
                            {isFlipped && <div className="flashcard-translation mt-2 p-3 rounded-3">
                                <h2 lang="hu" className="h3 fw-bold text-capitalize mb-0">{currentWord.translation}</h2>
                            </div>}
                        </div>
                    </Card.Body>
                </Card>

                {/* Buttons */}
                <div className="w-100 d-flex flex-column gap-3" style={{ maxWidth: '600px' }}>
                    <Button 
                        key={isFlipped ? 'next' : 'reveal'}
                        type="button"
                        ref={actionRef}
                        variant={isFlipped ? "success" : "info"} 
                        size="lg" 
                        className="rounded-pill fw-bold py-3 shadow-sm"
                        aria-controls={!isFlipped ? `${id}-translation` : undefined}
                        aria-expanded={!isFlipped ? false : undefined}
                        onClick={isFlipped ? handleNextCard : reveal}
                        onKeyDown={activateWithKeyboard}
                    >
                        {isFlipped ? (currentIndex < words.length - 1 ? 'Következő kártya ➔' : 'Gyakorlás befejezése') : 'Megnézem a választ'}
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
