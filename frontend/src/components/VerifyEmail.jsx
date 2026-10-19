import React, { useEffect, useState, useRef } from 'react';
import { useSearchParams, useNavigate } from 'react-router-dom';
import { Container, Card, Spinner, Button } from 'react-bootstrap';
import { authApi } from '../services/authApi';
import LearningFeedback from './LearningFeedback';
import useStageFocus from '../hooks/useStageFocus';

const VerifyEmail = () => {
    const [searchParams] = useSearchParams();
    const token = searchParams.get('token');
    const navigate = useNavigate();
    
    const [status, setStatus] = useState(token ? 'loading' : 'error'); 
    const [message, setMessage] = useState(token ? '' : 'Érvénytelen vagy hiányzó megerősítő link.');
    const headingRef = useStageFocus(status);
    
    const hasAttempted = useRef(false);

    useEffect(() => {
        
        if (!token) return;

        if (hasAttempted.current) return;
        hasAttempted.current = true;

        const verifyAccount = async () => {
            try {
                const response = await authApi.verifyEmail(token);
                setStatus('success');
                setMessage(response.data || 'Fiókodat sikeresen aktiváltuk!');
            } catch (error) {
                setStatus('error');
                const data = error.response?.data;
                setMessage(typeof data?.message === 'string' && data.message.trim() ? data.message
                    : typeof data === 'string' && data.trim() ? data
                    : 'A link lejárt vagy érvénytelen. Kérjük, próbáld újra.');
            }
        };

        verifyAccount(); 
    }, [token]);

    return (
        <Container className="d-flex justify-content-center align-items-center" style={{ minHeight: '80vh' }}>
            <Card className="bg-dark text-light border-secondary shadow-lg p-4 text-center" style={{ maxWidth: '400px', width: '100%' }}>
                <Card.Body>
                    <h1 ref={headingRef} tabIndex={-1} className="h4 fw-bold mb-3">
                        {status === 'loading' ? 'Fiók aktiválása...' : status === 'success' ? 'Sikeres aktiválás!' : 'Sikertelen aktiválás'}
                    </h1>
                    <LearningFeedback error={status === 'error' ? message : ''}
                        feedback={status === 'success' ? { type: 'success', msg: message } : null} />
                    {status === 'loading' && (
                        <>
                            <Spinner aria-hidden="true" animation="border" variant="info" className="mb-3" />
                            <p className="text-secondary small">Kérjük, várj egy pillanatot.</p>
                        </>
                    )}

                    {status === 'success' && (
                        <>
                            <div aria-hidden="true" className="text-success mb-3" style={{ fontSize: '3rem' }}>✓</div>
                            <Button variant="info" className="w-100 fw-bold" onClick={() => navigate('/login')}>
                                Tovább a Bejelentkezéshez
                            </Button>
                        </>
                    )}

                    {status === 'error' && (
                        <>
                            <div aria-hidden="true" className="text-danger mb-3" style={{ fontSize: '3rem' }}>✗</div>
                            <Button variant="outline-secondary" className="w-100 mt-2" onClick={() => navigate('/login')}>
                                Vissza a bejelentkezéshez
                            </Button>
                        </>
                    )}
                </Card.Body>
            </Card>
        </Container>
    );
};

export default VerifyEmail;
