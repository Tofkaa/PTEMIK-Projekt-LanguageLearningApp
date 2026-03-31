import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Form, Button, Card, Container, Row, Col, Alert } from 'react-bootstrap';
import api from '../services/api.jsx';

/**
 * Register Component
 * Responsible for handling new user registration.
 * Includes client-side validation to ensure passwords match before submitting.
 */
const Register = () => {
    // Component-level state for form inputs and feedback messages
    const [name, setName] = useState('');
    const [email, setEmail] = useState('');
    const [password, setPassword] = useState('');
    const [confirmPassword, setConfirmPassword] = useState('');
    
    // --- ÚJ STATE: Tanulási beállítások ---
    const [isDynamicMode, setIsDynamicMode] = useState(true);
    const [selectedLevel, setSelectedLevel] = useState('MEDIUM');
    
    const [error, setError] = useState('');
    const [success, setSuccess] = useState('');
    
    const navigate = useNavigate();

    /**
     * Asynchronous handler for form submission.
     * Validates inputs, executes the registration API call, and manages UI states.
     */
    const handleSubmit = async (e) => {
        e.preventDefault();
        
        // Clear previous feedback messages before a new attempt
        setError('');
        setSuccess('');

        // 1. Client-side validation: Do the passwords match?
        if (password !== confirmPassword) {
            setError('A két jelszó nem egyezik! Kérlek, próbáld újra.');
            return; // Abort the submission, do not send to backend
        }
        
        console.log(`Initiating registration attempt for: ${name} (${email})`);

        try {
            // select mode for backend
            const preferredDifficulty = isDynamicMode ? 'DYNAMIC' : selectedLevel;

            // 2. Send POST request to the backend registration endpoint
            await api.post('/auth/register', { 
                name, 
                email, 
                password,
                preferredDifficulty
            });

            if (isDynamicMode) {
                localStorage.setItem('dynamicStartingLevel', selectedLevel);
            }

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

                                {/* Confirm Password Field */}
                                <Form.Group className="mb-4" controlId="formConfirmPassword">
                                    <Form.Label>Jelszó újra</Form.Label>
                                    <Form.Control type="password" placeholder="Jelszó megerősítése" value={confirmPassword} onChange={(e) => setConfirmPassword(e.target.value)} required minLength={6} autoComplete='new-password'/>
                                </Form.Group>

                                {/* --- ÚJ UI SZEKCIÓ: TANULÁSI BEÁLLÍTÁSOK --- */}
                                <div className="mb-4 p-4 border border-secondary rounded bg-dark bg-opacity-50 shadow-sm">
                                    <h5 className="text-light fw-bold mb-3 border-bottom border-secondary pb-2">
                                        🧠 Tanulási Beállítások
                                    </h5>
                                    
                                    <Form.Group className="mb-3 text-light">
                                        <Form.Check 
                                            type="switch"
                                            id="dynamic-mode-switch"
                                            label="Adaptív Tanulási Mód (Dinamikus)"
                                            checked={isDynamicMode}
                                            onChange={(e) => setIsDynamicMode(e.target.checked)}
                                            className="fw-bold fs-6"
                                        />
                                        <Form.Text className="text-light opacity-75 small d-block mt-1">
                                            {isDynamicMode 
                                                ? "A rendszer a teljesítményed alapján automatikusan nehezíti vagy könnyíti a feladatokat."
                                                : "A rendszer fixen ezen a nehézségi szinten tart, amíg te magad meg nem változtatod."}
                                        </Form.Text>
                                    </Form.Group>

                                    <Form.Group>
                                        <Form.Label className="text-light fw-bold small">
                                            {isDynamicMode ? "Honnan induljon a rendszer kalkulációja?" : "Melyik fix szintet kéred?"}
                                        </Form.Label>
                                        <Form.Select 
                                            value={selectedLevel}
                                            onChange={(e) => setSelectedLevel(e.target.value)}
                                            className="bg-secondary text-light border-0 shadow-sm"
                                            style={{ cursor: 'pointer' }}
                                        >
                                            <option value="EASY">🟢 Kezdő (Lassabb tempó, alapok)</option>
                                            <option value="MEDIUM">🟡 Haladó (Normál kihívások)</option>
                                            <option value="HARD">🔴 Profi (Gyors tempó, nehéz feladatok)</option>
                                        </Form.Select>
                                    </Form.Group>
                                </div>
                                {/* ------------------------------------------ */}

                                {/* Disable the submit button if registration was successful to prevent duplicate submissions */}
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