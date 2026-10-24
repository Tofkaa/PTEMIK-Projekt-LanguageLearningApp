import React, { useState } from 'react';
import { useSearchParams, Link } from 'react-router-dom';
import { Form, Button, Card, Container, Row, Col, Alert } from 'react-bootstrap';
import { authApi } from '../services/authApi';
import LearningFeedback from '../components/LearningFeedback';
import useStageFocus from '../hooks/useStageFocus';

const ResetPassword = () => {
    const [searchParams] = useSearchParams();
    const token = searchParams.get('token');

    const [password, setPassword] = useState('');
    const [confirmPassword, setConfirmPassword] = useState('');
    const [status, setStatus] = useState('idle'); // 'idle', 'loading', 'success', 'error'
    const [message, setMessage] = useState('');
    const headingRef = useStageFocus(!token ? 'missing-token' : status === 'success' ? 'success' : 'form');

    const handleSubmit = async (e) => {
        e.preventDefault();
        if (status === 'loading') return;
        
        if (password !== confirmPassword) {
            setStatus('error');
            setMessage('A két jelszó nem egyezik!');
            return;
        }

        setStatus('loading');
        setMessage('');

        try {
            const response = await authApi.resetPassword({ token, newPassword: password });
            setStatus('success');
            setMessage(response.data || 'Jelszó sikeresen frissítve!');
        } catch (error) {
            setStatus('error');
            
            const errData = error.response?.data;
            if (errData && errData.message) {
                setMessage(errData.message);
            } else if (typeof errData === 'string') {
                setMessage(errData);
            } else {
                setMessage('Érvénytelen vagy lejárt token.');
            }
        }
    };

    if (!token) {
        return (
            <Container className="d-flex justify-content-center align-items-center mt-5" style={{ minHeight: '60vh' }}>
                <Alert variant="danger" className="text-center p-4 rounded-4 shadow-lg">
                    <h1 ref={headingRef} tabIndex={-1} className="h4"><span aria-hidden="true">⚠️</span> Hiányzó azonosító</h1>
                    <p>Érvénytelen vagy hiányzó visszaállító link.</p>
                    <Link to="/forgot-password" className="app-accent-link d-block">Új visszaállító link kérése</Link>
                    <Link to="/login" className="btn btn-outline-danger mt-2">Vissza a belépéshez</Link>
                </Alert>
            </Container>
        );
    }

    return (
        <Container className="auth-page mt-5">
            <Row className="justify-content-center">
                <Col md={8} lg={5}>
                    <Card className="bg-transparent border-0 mt-5 text-light">
                        <Card.Body className="p-5">
                            <h1 ref={headingRef} tabIndex={-1} className="h2 text-center mb-4 fw-bold">Új jelszó beállítása</h1>
                            
                            <LearningFeedback error={status === 'error' ? message : ''} feedback={status === 'success' ? { type: 'success', msg: message } : null} />
                            {status === 'error' && <Link to="/forgot-password" className="app-accent-link d-block mb-3">Új visszaállító link kérése</Link>}
                            {status === 'success' && <Link to="/login" className="app-accent-link d-block mt-3">Vissza a belépéshez</Link>}

                            {status !== 'success' && (
                                <Form onSubmit={handleSubmit}>
                                    <Form.Group className="mb-3" controlId="formPassword">
                                        <Form.Label>Új jelszó</Form.Label>
                                        <Form.Control 
                                            type="password" 
                                            autoComplete="new-password"
                                            placeholder="Legalább 6 karakter" 
                                            value={password} 
                                            onChange={(e) => setPassword(e.target.value)} 
                                            required minLength={6} 
                                            readOnly={status === 'loading'}
                                        />
                                    </Form.Group>

                                    <Form.Group className="mb-4" controlId="formConfirmPassword">
                                        <Form.Label>Új jelszó megerősítése</Form.Label>
                                        <Form.Control 
                                            type="password" 
                                            autoComplete="new-password"
                                            placeholder="Jelszó újra" 
                                            value={confirmPassword} 
                                            onChange={(e) => setConfirmPassword(e.target.value)} 
                                            required minLength={6} 
                                            readOnly={status === 'loading'}
                                        />
                                    </Form.Group>

                                    <Button variant="primary" type="submit" className="w-100 mb-3 py-2 fw-bold" aria-disabled={status === 'loading'}>
                                        {status === 'loading' ? 'Feldolgozás...' : 'Jelszó mentése'}
                                    </Button>
                                </Form>
                            )}
                        </Card.Body>
                    </Card>
                </Col>
            </Row>
        </Container>
    );
};

export default ResetPassword;
