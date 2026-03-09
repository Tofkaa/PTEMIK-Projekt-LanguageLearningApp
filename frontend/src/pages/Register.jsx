import { useState } from 'react';
import { Link } from 'react-router-dom';
import { Form, Button, Card, Container, Row, Col } from 'react-bootstrap';

const Register = () => {
    // 1. States for registration (Name, Email, Password)
    const [name, setName] = useState('');
    const [email, setEmail] = useState('');
    const [password, setPassword] = useState('');

    const handleSubmit = (e) => {
        e.preventDefault();
        console.log('Regisztrációs kísérlet:', { name, email });
        // TODO POST auth/register call
    };

    return (
        <Container className="mt-5">
            <Row className="justify-content-center">
                <Col md={8} lg={5}>
                    <Card className="shadow-sm border-0 rounded-lg mt-5">
                        <Card.Body className="p-5">
                            <h2 className="text-center mb-4 fw-bold">Új fiók létrehozása</h2>
                            
                            <Form onSubmit={handleSubmit}>
                                <Form.Group className="mb-3" controlId="formName">
                                    <Form.Label>Teljes név (vagy Becenév)</Form.Label>
                                    <Form.Control
                                        type="text"
                                        placeholder="Pl. Teszt Elek"
                                        value={name}
                                        onChange={(e) => setName(e.target.value)}
                                        required
                                    />
                                </Form.Group>

                                <Form.Group className="mb-3" controlId="formEmail">
                                    <Form.Label>Email cím</Form.Label>
                                    <Form.Control
                                        type="email"
                                        placeholder="pelda@email.com"
                                        value={email}
                                        onChange={(e) => setEmail(e.target.value)}
                                        required
                                    />
                                </Form.Group>

                                <Form.Group className="mb-4" controlId="formPassword">
                                    <Form.Label>Jelszó</Form.Label>
                                    <Form.Control
                                        type="password"
                                        placeholder="Legalább 6 karakter"
                                        value={password}
                                        onChange={(e) => setPassword(e.target.value)}
                                        required
                                        minLength={6}
                                    />
                                </Form.Group>

                                <Button variant="success" type="submit" className="w-100 mb-3 py-2 fw-bold">
                                    Regisztráció
                                </Button>
                            </Form>
                            
                            <div className="text-center mt-3">
                                <span className="text-muted">Már van fiókod? </span>
                                <Link to="/login" className="text-decoration-none fw-bold">Lépj be itt!</Link>
                            </div>
                        </Card.Body>
                    </Card>
                </Col>
            </Row>
        </Container>
    );
};

export default Register;