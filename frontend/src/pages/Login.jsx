import { useState } from 'react';
import { Link } from 'react-router-dom';
import { Form, Button, Card, Container, Row, Col } from 'react-bootstrap';

const Login = () => {
    // 1. React states for inputs
    const [email, setEmail] = useState('');
    const [password, setPassword] = useState('');

    // 2. This function runs when the user clicks the sign in button
    const handleSubmit = (e) => {
        e.preventDefault(); // Prevents the page from reloading 
        console.log('Készen áll az API hívásra! Adatok:', { email });
        // TODO axios call
    };

    return (
        <Container className="mt-5">
            <Row className="justify-content-center">
                <Col md={8} lg={5}>
                    <Card className="shadow-sm border-0 rounded-lg mt-5">
                        <Card.Body className="p-5">
                            <h2 className="text-center mb-4 fw-bold">Bejelentkezés</h2>
                            
                            <Form onSubmit={handleSubmit}>
                                <Form.Group className="mb-3" controlId="formBasicEmail">
                                    <Form.Label>Email cím</Form.Label>
                                    <Form.Control
                                        type="email"
                                        placeholder="pelda@email.com"
                                        value={email}
                                        onChange={(e) => setEmail(e.target.value)}
                                        required
                                    />
                                </Form.Group>

                                <Form.Group className="mb-4" controlId="formBasicPassword">
                                    <Form.Label>Jelszó</Form.Label>
                                    <Form.Control
                                        type="password"
                                        placeholder="Titkos jelszó"
                                        value={password}
                                        onChange={(e) => setPassword(e.target.value)}
                                        required
                                    />
                                </Form.Group>

                                <Button variant="primary" type="submit" className="w-100 mb-3 py-2 fw-bold">
                                    Belépés
                                </Button>
                            </Form>
                            
                            <div className="text-center mt-3">
                                <span className="text-muted">Nincs még fiókod? </span>
                                <Link to="/register" className="text-decoration-none fw-bold">Regisztrálj itt!</Link>
                            </div>
                        </Card.Body>
                    </Card>
                </Col>
            </Row>
        </Container>
    );
};

export default Login;