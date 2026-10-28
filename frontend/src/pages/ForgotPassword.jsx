import { rateLimitMessage } from '../utils/rateLimitMessage';
import React, { useState } from 'react';
import { Link } from 'react-router-dom';
import { Form, Button, Card, Container, Row, Col } from 'react-bootstrap';
import { authApi } from '../services/authApi';
import LearningFeedback from '../components/LearningFeedback';
import useStageFocus from '../hooks/useStageFocus';

const ForgotPassword = () => {
    const [email, setEmail] = useState('');
    const [status, setStatus] = useState('idle'); // 'idle', 'loading', 'success', 'error'
    const [message, setMessage] = useState('');
    const headingRef = useStageFocus(status === 'success' ? 'success' : 'form');

    const handleSubmit = async (e) => {
        e.preventDefault();
        if (status === 'loading') return;
        setStatus('loading');
        setMessage('');

        try {
            const response = await authApi.forgotPassword(email);
            setStatus('success');
            setMessage(response.data || 'Ha létezik fiók ezzel az e-mail címmel, elküldtük a visszaállító linket.');
        } catch (error) {
            setStatus('error');
            setMessage(error.response?.status === 429 ? rateLimitMessage(error) : 'Hálózati hiba történt. Kérlek, próbáld újra később.');
        }
    };

    return (
        <Container className="auth-page mt-5">
            <Row className="justify-content-center">
                <Col md={8} lg={5}>
                    <Card className="bg-transparent border-0 mt-5 text-light">
                        <Card.Body className="p-5">
                            <h1 ref={headingRef} tabIndex={-1} className="h2 text-center mb-4 fw-bold">Elfelejtett jelszó</h1>
                            <p className="text-center text-secondary mb-4">
                                Add meg a fiókodhoz tartozó e-mail címet, és küldünk egy linket a jelszavad visszaállításához.
                            </p>
                            <p id="reset-link-help" className="text-secondary">
                                A link a kérés indításától számított 1 óráig érvényes. Ha lejárt, új linket kérhetsz.
                            </p>
                            
                            <LearningFeedback error={status === 'error' ? message : ''} feedback={status === 'success' ? { type: 'success', msg: message } : null} />

                            {status !== 'success' && (
                                <Form onSubmit={handleSubmit}>
                                    <Form.Group className="mb-4" controlId="formEmail">
                                        <Form.Label>Email cím</Form.Label>
                                        <Form.Control 
                                            type="email" 
                                            autoComplete="email"
                                            aria-describedby="reset-link-help"
                                            placeholder="pelda@email.com" 
                                            value={email} 
                                            onChange={(e) => setEmail(e.target.value)} 
                                            required 
                                            readOnly={status === 'loading'}
                                        />
                                    </Form.Group>

                                    <Button variant="primary" type="submit" className="w-100 mb-3 py-2 fw-bold" aria-disabled={status === 'loading'}>
                                        {status === 'loading' ? 'Küldés folyamatban...' : 'Visszaállító link küldése'}
                                    </Button>
                                </Form>
                            )}
                            {status === 'success' && (
                                <Button type="button" variant="outline-info" className="w-100 mt-3"
                                    onClick={() => { setMessage(''); setStatus('idle'); }}>
                                    Új visszaállító link kérése
                                </Button>
                            )}
                            
                            <div className="text-center mt-3">
                                <span className="text-light">Eszembe jutott a jelszavam! </span>
                                <Link to="/login" className="app-accent-link fw-bold">Vissza a belépéshez</Link>
                            </div>
                        </Card.Body>
                    </Card>
                </Col>
            </Row>
        </Container>
    );
};

export default ForgotPassword;
