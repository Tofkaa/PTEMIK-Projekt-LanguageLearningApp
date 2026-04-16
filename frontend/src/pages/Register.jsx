import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Form, Button, Card, Container, Row, Col, Alert, ButtonGroup, ToggleButton } from 'react-bootstrap';
import api from '../services/api.jsx';

/**
 * Register Component
 * Responsible for handling new user registration.
 * Includes client-side validation to ensure passwords match before submitting.
 */
const Register = () => {
    const [name, setName] = useState('');
    const [email, setEmail] = useState('');
    const [password, setPassword] = useState('');
    const [confirmPassword, setConfirmPassword] = useState('');
    const [preferredDifficulty, setPreferredDifficulty] = useState('DYNAMIC');
    const [role, setRole] = useState('STUDENT');
    
    const [error, setError] = useState('');
    const [success, setSuccess] = useState('');
    const navigate = useNavigate();

    /**
     * Asynchronous handler for form submission.
     * Validates inputs, executes the registration API call, and manages UI states.
     */
    const handleSubmit = async (e) => {
        e.preventDefault();
        
        setError('');
        setSuccess('');

        if (password !== confirmPassword) {
            setError('A két jelszó nem egyezik! Kérlek, próbáld újra.');
            return; 
        }
        
        console.log(`Initiating registration attempt for: ${name} (${email}) as ${role}`);

        try {
            await api.post('/auth/register', { 
                name, 
                email, 
                password,
                preferredDifficulty,
                role
            });

            setSuccess('Sikeres regisztráció! Irányítás a bejelentkezéshez...');
            setTimeout(() => {
                navigate('/login');
            }, 2000);

        } catch (err) {
            console.error("Registration error:", err);
            
            if (err.response && err.response.data) {
                setError(err.response.data.message || 'Hiba történt a regisztráció során. (Foglalt email?)');
            } else {
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
                            
                            {error && <Alert variant="danger" className="text-center rounded-4 border-0 shadow-sm fw-bold">⚠️ {error}</Alert>}
                            {success && <Alert variant="success" className="text-center rounded-4 border-0 shadow-sm fw-bold">✅ {success}</Alert>}

                            <Form onSubmit={handleSubmit}>
                                <Form.Group className="mb-3" controlId="formName">
                                    <Form.Label>Teljes név</Form.Label>
                                    <Form.Control type="text" placeholder="Pl. Teszt Elek" value={name} onChange={(e) => setName(e.target.value)} required />
                                </Form.Group>

                                <Form.Group className="mb-3" controlId="formEmail">
                                    <Form.Label>Email cím</Form.Label>
                                    <Form.Control type="email" placeholder="pelda@email.com" value={email} onChange={(e) => setEmail(e.target.value)} required />
                                </Form.Group>

                                <Form.Group className="mb-3" controlId="formPassword">
                                    <Form.Label>Jelszó</Form.Label>
                                    <Form.Control type="password" placeholder="Legalább 6 karakter" value={password} onChange={(e) => setPassword(e.target.value)} required minLength={6} autoComplete='new-password'/>
                                </Form.Group>

                                <Form.Group className="mb-4" controlId="formConfirmPassword">
                                    <Form.Label>Jelszó újra</Form.Label>
                                    <Form.Control type="password" placeholder="Jelszó megerősítése" value={confirmPassword} onChange={(e) => setConfirmPassword(e.target.value)} required minLength={6} autoComplete='new-password'/>
                                </Form.Group>
                                
                                <Form.Group className="mb-4 text-center">
                                    <Form.Label className="d-block mb-2 text-light fw-bold">Fiók típusa</Form.Label>
                                    <ButtonGroup className="w-100 shadow-sm">
                                        <ToggleButton
                                            id="role-student"
                                            type="radio"
                                            variant="outline-info"
                                            name="role"
                                            value="STUDENT"
                                            checked={role === 'STUDENT'}
                                            onChange={(e) => setRole(e.currentTarget.value)}
                                            className="fw-bold"
                                        >
                                            👨‍🎓 Tanuló
                                        </ToggleButton>
                                        <ToggleButton
                                            id="role-teacher"
                                            type="radio"
                                            variant="outline-info"
                                            name="role"
                                            value="TEACHER"
                                            checked={role === 'TEACHER'}
                                            onChange={(e) => setRole(e.currentTarget.value)}
                                            className="fw-bold"
                                        >
                                            👩‍🏫 Tanár
                                        </ToggleButton>
                                    </ButtonGroup>
                                </Form.Group>

                                <Form.Group className="mb-4 p-3 border border-secondary rounded bg-dark bg-opacity-50 shadow-sm">
                                    <Form.Label className="text-light fw-bold">🧠 Tanulási Mód</Form.Label>
                                    <Form.Select 
                                        value={preferredDifficulty}
                                        onChange={(e) => setPreferredDifficulty(e.target.value)}
                                        className="bg-secondary text-light border-0 shadow-sm"
                                        style={{ cursor: 'pointer' }}
                                    >
                                        <option value="DYNAMIC">🚀 Dinamikus (Ajánlott)</option>
                                        <option value="EASY">🟢 Fix: Kezdő (Csak EASY feladatok)</option>
                                        <option value="MEDIUM">🟡 Fix: Haladó (Csak MEDIUM feladatok)</option>
                                        <option value="HARD">🔴 Fix: Profi (Csak HARD feladatok)</option>
                                    </Form.Select>
                                    <Form.Text className="text-light opacity-50 small mt-2 d-block">
                                        A beállítást később a profilodban bármikor módosíthatod.
                                    </Form.Text>
                                </Form.Group>

                                <Button variant="primary" type="submit" className="w-100 mb-3 py-2 fw-bold" disabled={!!success}>
                                    Regisztráció
                                </Button>
                            </Form>
                            
                            <div className="text-center mt-3">
                                <span className="text-light">Már van fiókod? </span>
                                <Link to="/login" className="text-decoration-none fw-bold text-info">Lépj be itt!</Link>
                            </div>
                        </Card.Body>
                    </Card>
                </Col>
            </Row>
        </Container>
    );
};

export default Register;