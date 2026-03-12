import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Form, Button, Card, Container, Row, Col, Alert } from 'react-bootstrap';
import api from '../services/api.jsx';

/**
 * Register Component
 * Responsible for handling new user registration.
 * Submits user details to the backend and navigates to the login page upon success.
 */
const Register = () => {
    // Component-level state for form inputs and feedback messages
    const [name, setName] = useState('');
    const [email, setEmail] = useState('');
    const [password, setPassword] = useState('');
    const [error, setError] = useState('');
    const [success, setSuccess] = useState('');
    
    const navigate = useNavigate();

    /**
     * Asynchronous handler for form submission.
     * Executes the registration API call and manages success/error states.
     * @param {React.FormEvent} e - The form submission event
     */
    const handleSubmit = async (e) => {
        e.preventDefault();
        
        // Clear previous feedback messages before a new attempt
        setError('');
        setSuccess('');
        
        console.log(`Initiating registration attempt for: ${name} (${email})`);

        try {
            // Send POST request to the backend registration endpoint
            const response = await api.post('/auth/register', { 
                name, 
                email, 
                password 
            });

            // Display success message and delay navigation to allow the user to read it
            setSuccess('Sikeres regisztráció! Irányítás a bejelentkezéshez...');
            setTimeout(() => {
                navigate('/login');
            }, 2000);

        } catch (err) {
            console.error("Registration error:", err);
            
            // Extract and display specific error message from the backend if available
            if (err.response && err.response.data) {
                setError(err.response.data.message || 'Hiba történt a regisztráció során. (Foglalt email?)');
            } else {
                // Fallback error message for network or unexpected issues
                setError('Nem sikerült csatlakozni a szerverhez.');
            }
        }
    };

    return (
        <Container className="mt-5">
            <Row className="justify-content-center">
                <Col md={8} lg={5}>
                    <Card className="bg-transparent border-0 mt-5 text-light">
                        <Card.Body className="p-5">
                            <h2 className="text-center mb-4 fw-bold">Új fiók létrehozása</h2>
                            
                            {/* Render success/error alerts based on component state */}
                            {error && <Alert variant="danger">{error}</Alert>}
                            {success && <Alert variant="success">{success}</Alert>}

                            <Form onSubmit={handleSubmit}>
                                <Form.Group className="mb-3" controlId="formName">
                                    <Form.Label>Teljes név</Form.Label>
                                    <Form.Control type="text" placeholder="Pl. Teszt Elek" value={name} onChange={(e) => setName(e.target.value)} required />
                                </Form.Group>

                                <Form.Group className="mb-3" controlId="formEmail">
                                    <Form.Label>Email cím</Form.Label>
                                    <Form.Control type="email" placeholder="pelda@email.com" value={email} onChange={(e) => setEmail(e.target.value)} required />
                                </Form.Group>

                                <Form.Group className="mb-4" controlId="formPassword">
                                    <Form.Label>Jelszó</Form.Label>
                                    <Form.Control type="password" placeholder="Legalább 6 karakter" value={password} onChange={(e) => setPassword(e.target.value)} required minLength={6} autoComplete='new-password'/>
                                </Form.Group>

                                {/* Disable the submit button if registration was successful to prevent duplicate submissions */}
                                <Button variant="success" type="submit" className="w-100 mb-3 py-2 fw-bold" disabled={!!success}>
                                    Regisztráció
                                </Button>
                            </Form>
                            
                            <div className="text-center mt-3">
                                <span className="text-light">Már van fiókod? </span>
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