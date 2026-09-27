import React, { useState } from 'react';
import { Link } from 'react-router-dom';
import { Form, Button, Card, Container, Row, Col, Alert } from 'react-bootstrap';
import { authApi } from '../services/authApi';

const ForgotPassword = () => {
    const [email, setEmail] = useState('');
    const [status, setStatus] = useState('idle'); // 'idle', 'loading', 'success', 'error'
    const [message, setMessage] = useState('');

    const handleSubmit = async (e) => {
        e.preventDefault();
        setStatus('loading');
        setMessage('');

        try {
            const response = await authApi.forgotPassword(email);
            setStatus('success');
            setMessage(response.data || 'Ha létezik fiók ezzel az e-mail címmel, elküldtük a visszaállító linket.');
        } catch (error) {
            setStatus('error');
            setMessage('Hálózati hiba történt. Kérlek, próbáld újra később.', error);
        }
    };

    return (
        <Container className="mt-5">
            <Row className="justify-content-center">
                <Col md={8} lg={5}>
                    <Card className="bg-transparent border-0 mt-5 text-light">
                        <Card.Body className="p-5">
                            <h2 className="text-center mb-4 fw-bold">Elfelejtett jelszó</h2>
                            <p className="text-center text-secondary mb-4">
                                Add meg a fiókodhoz tartozó e-mail címet, és küldünk egy linket a jelszavad visszaállításához.
                            </p>
                            
                            {status === 'error' && <Alert variant="danger" className="text-center rounded-4 border-0 shadow-sm fw-bold">⚠️ {message}</Alert>}
                            {status === 'success' && <Alert variant="success" className="text-center rounded-4 border-0 shadow-sm fw-bold">✅ {message}</Alert>}

                            {status !== 'success' && (
                                <Form onSubmit={handleSubmit}>
                                    <Form.Group className="mb-4" controlId="formEmail">
                                        <Form.Label>Email cím</Form.Label>
                                        <Form.Control 
                                            type="email" 
                                            placeholder="pelda@email.com" 
                                            value={email} 
                                            onChange={(e) => setEmail(e.target.value)} 
                                            required 
                                            disabled={status === 'loading'}
                                        />
                                    </Form.Group>

                                    <Button variant="primary" type="submit" className="w-100 mb-3 py-2 fw-bold" disabled={status === 'loading'}>
                                        {status === 'loading' ? 'Küldés folyamatban...' : 'Visszaállító link küldése'}
                                    </Button>
                                </Form>
                            )}
                            
                            <div className="text-center mt-3">
                                <span className="text-light">Eszembe jutott a jelszavam! </span>
                                <Link to="/login" className="text-decoration-none fw-bold text-info">Vissza a belépéshez</Link>
                            </div>
                        </Card.Body>
                    </Card>
                </Col>
            </Row>
        </Container>
    );
};

export default ForgotPassword;