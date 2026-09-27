import React, { useState } from 'react';
import { useSearchParams, useNavigate, Link } from 'react-router-dom';
import { Form, Button, Card, Container, Row, Col, Alert } from 'react-bootstrap';
import { authApi } from '../services/authApi';

const ResetPassword = () => {
    const [searchParams] = useSearchParams();
    const token = searchParams.get('token');
    const navigate = useNavigate();

    const [password, setPassword] = useState('');
    const [confirmPassword, setConfirmPassword] = useState('');
    const [status, setStatus] = useState('idle'); // 'idle', 'loading', 'success', 'error'
    const [message, setMessage] = useState('');

    const handleSubmit = async (e) => {
        e.preventDefault();
        
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
            setTimeout(() => navigate('/login'), 3000);
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
                    <h4>⚠️ Hiányzó azonosító</h4>
                    <p>Érvénytelen vagy hiányzó visszaállító link.</p>
                    <Link to="/login" className="btn btn-outline-danger mt-2">Vissza a belépéshez</Link>
                </Alert>
            </Container>
        );
    }

    return (
        <Container className="mt-5">
            <Row className="justify-content-center">
                <Col md={8} lg={5}>
                    <Card className="bg-transparent border-0 mt-5 text-light">
                        <Card.Body className="p-5">
                            <h2 className="text-center mb-4 fw-bold">Új jelszó beállítása</h2>
                            
                            {status === 'error' && <Alert variant="danger" className="text-center rounded-4 border-0 shadow-sm fw-bold">⚠️ {message}</Alert>}
                            {status === 'success' && <Alert variant="success" className="text-center rounded-4 border-0 shadow-sm fw-bold">✅ {message} <br/><small>Átirányítás...</small></Alert>}

                            {status !== 'success' && (
                                <Form onSubmit={handleSubmit}>
                                    <Form.Group className="mb-3" controlId="formPassword">
                                        <Form.Label>Új jelszó</Form.Label>
                                        <Form.Control 
                                            type="password" 
                                            placeholder="Legalább 6 karakter" 
                                            value={password} 
                                            onChange={(e) => setPassword(e.target.value)} 
                                            required minLength={6} 
                                            disabled={status === 'loading'}
                                        />
                                    </Form.Group>

                                    <Form.Group className="mb-4" controlId="formConfirmPassword">
                                        <Form.Label>Új jelszó megerősítése</Form.Label>
                                        <Form.Control 
                                            type="password" 
                                            placeholder="Jelszó újra" 
                                            value={confirmPassword} 
                                            onChange={(e) => setConfirmPassword(e.target.value)} 
                                            required minLength={6} 
                                            disabled={status === 'loading'}
                                        />
                                    </Form.Group>

                                    <Button variant="primary" type="submit" className="w-100 mb-3 py-2 fw-bold" disabled={status === 'loading'}>
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