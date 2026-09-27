import React, { useEffect, useState, useRef } from 'react';
import { useSearchParams, useNavigate } from 'react-router-dom';
import { Container, Card, Spinner, Button, Alert } from 'react-bootstrap';
import { authApi } from '../services/authApi';

const VerifyEmail = () => {
    const [searchParams] = useSearchParams();
    const token = searchParams.get('token');
    const navigate = useNavigate();
    
    const [status, setStatus] = useState(token ? 'loading' : 'error'); 
    const [message, setMessage] = useState(token ? '' : 'Érvénytelen vagy hiányzó megerősítő link.');
    
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
                setMessage(
                    error.response?.data?.message || 
                    typeof error.response?.data === 'string' ? error.response.data :
                    'A link lejárt vagy érvénytelen. Kérjük, próbáld újra.'
                );
            }
        };

        verifyAccount(); 
    }, [token]);

    return (
        <Container className="d-flex justify-content-center align-items-center" style={{ minHeight: '80vh' }}>
            <Card className="bg-dark text-light border-secondary shadow-lg p-4 text-center" style={{ maxWidth: '400px', width: '100%' }}>
                <Card.Body>
                    {status === 'loading' && (
                        <>
                            <Spinner animation="border" variant="info" className="mb-3" />
                            <h4 className="fw-bold">Fiók aktiválása...</h4>
                            <p className="text-secondary small">Kérjük, várj egy pillanatot.</p>
                        </>
                    )}

                    {status === 'success' && (
                        <>
                            <div className="text-success mb-3" style={{ fontSize: '3rem' }}>✓</div>
                            <h4 className="fw-bold text-success mb-3">Sikeres aktiválás!</h4>
                            <p className="text-light mb-4">{message}</p>
                            <Button variant="info" className="w-100 fw-bold" onClick={() => navigate('/login')}>
                                Tovább a Bejelentkezéshez
                            </Button>
                        </>
                    )}

                    {status === 'error' && (
                        <>
                            <div className="text-danger mb-3" style={{ fontSize: '3rem' }}>✗</div>
                            <h4 className="fw-bold text-danger mb-3">Sikertelen aktiválás</h4>
                            <Alert variant="danger" className="small">{message}</Alert>
                            <Button variant="outline-light" className="w-100 mt-2" onClick={() => navigate('/login')}>
                                Vissza a Főoldalra
                            </Button>
                        </>
                    )}
                </Card.Body>
            </Card>
        </Container>
    );
};

export default VerifyEmail;