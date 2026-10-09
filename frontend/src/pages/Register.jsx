import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Form, Button, Card, Container, Row, Col, Alert, ButtonGroup, ToggleButton } from 'react-bootstrap';
import api from '../services/api.jsx';
import ThemeToggle from '../components/ThemeToggle.jsx';

const AVAILABLE_COURSES = {
    'en': { name: 'Angol', flag: '🇬🇧' },
    'es': { name: 'Spanyol', flag: '🇪🇸' },
    'de': { name: 'Német', flag: '🇩🇪' }
};

const Register = () => {
    const [name, setName] = useState('');
    const [email, setEmail] = useState('');
    const [password, setPassword] = useState('');
    const [confirmPassword, setConfirmPassword] = useState('');
    const [preferredDifficulty, setPreferredDifficulty] = useState('DYNAMIC');
    const [role, setRole] = useState('STUDENT');
    
    const [courseCode, setCourseCode] = useState('en');
    
    const [error, setError] = useState('');
    const [isSubmitting, setIsSubmitting] = useState(false);
    const [isRegistered, setIsRegistered] = useState(false);

    const navigate = useNavigate();

    const handleSubmit = async (e) => {
        e.preventDefault();
        setError('');

        if (password !== confirmPassword) {
            setError('A két jelszó nem egyezik! Kérlek, próbáld újra.');
            return; 
        }
        
        setIsSubmitting(true);
        console.log(`Initiating registration attempt for: ${name} (${email}) as ${role} for course ${courseCode}`);

        try {
            await api.post('/auth/register', { 
                name, 
                email, 
                password,
                preferredDifficulty,
                role,
                courseCode 
            });

            setIsRegistered(true);

        } catch (err) {
            console.error("Registration error:", err);
            
            if (err.response && err.response.data) {
                setError(err.response.data.message || 'Hiba történt a regisztráció során. (Foglalt email?)');
            } else {
                setError('Nem sikerült csatlakozni a szerverhez.');
            }
        } finally {
            setIsSubmitting(false);
        }
    };

   if (isRegistered) {
        return (
            <Container className="mt-5 position-relative">
                <div className="position-absolute top-0 end-0 p-3" style={{ zIndex: 1000 }}>
                    <ThemeToggle />
                </div>

                <Row className="justify-content-center">
                    <Col md={8} lg={5}>
                        <Card className="bg-dark text-light border-info shadow-lg mt-5 text-center p-4">
                            <Card.Body>
                                <div className="text-info mb-3" style={{ fontSize: '3rem' }}>✉️</div>
                                <h3 className="fw-bold text-info mb-3">Ellenőrizd a postafiókodat!</h3>
                                <p className="mb-3">
                                    Sikeresen regisztráltál! Küldtünk egy megerősítő linket a(z) <strong className="text-light">{email}</strong> címre.
                                </p>
                                <p className="small text-secondary mb-4">
                                    A fiókod használatához előbb rá kell kattintanod a levélben található linkre. 
                                    Ha nem találod a levelet, nézd meg a Spam mappában is!
                                </p>
                                <Button variant="outline-info" onClick={() => navigate('/login')} className="w-100 fw-bold">
                                    Vissza a bejelentkezéshez
                                </Button>
                            </Card.Body>
                        </Card>
                    </Col>
                </Row>
            </Container>
        );
    }

   return (
        <Container className="mt-5 position-relative">
            <div className="position-absolute top-0 end-0 p-3" style={{ zIndex: 1000 }}>
                <ThemeToggle />
            </div>

            <Row className="justify-content-center">
                <Col md={8} lg={5}>
                    <Card className="bg-transparent border-0 mt-5 text-light">
                        <Card.Body className="p-5">
                            <h2 className="text-center mb-4 fw-bold">Új fiók létrehozása</h2>
                            
                            {error && <Alert variant="danger" className="text-center rounded-4 border-0 shadow-sm fw-bold">⚠️ {error}</Alert>}

                            <Form onSubmit={handleSubmit}>
                                <Form.Group className="mb-3" controlId="formName">
                                    <Form.Label>Teljes név</Form.Label>
                                    <Form.Control type="text" placeholder="Pl. Teszt Elek" value={name} onChange={(e) => setName(e.target.value)} required disabled={isSubmitting} />
                                </Form.Group>

                                <Form.Group className="mb-3" controlId="formEmail">
                                    <Form.Label>Email cím</Form.Label>
                                    <Form.Control type="email" placeholder="pelda@email.com" value={email} onChange={(e) => setEmail(e.target.value)} required disabled={isSubmitting} />
                                </Form.Group>

                                <Form.Group className="mb-3" controlId="formPassword">
                                    <Form.Label>Jelszó</Form.Label>
                                    <Form.Control type="password" placeholder="Legalább 6 karakter" value={password} onChange={(e) => setPassword(e.target.value)} required minLength={6} autoComplete='new-password' disabled={isSubmitting} />
                                </Form.Group>

                                <Form.Group className="mb-4" controlId="formConfirmPassword">
                                    <Form.Label>Jelszó újra</Form.Label>
                                    <Form.Control type="password" placeholder="Jelszó megerősítése" value={confirmPassword} onChange={(e) => setConfirmPassword(e.target.value)} required minLength={6} autoComplete='new-password' disabled={isSubmitting} />
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
                                            disabled={isSubmitting}
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
                                            disabled={isSubmitting}
                                        >
                                            👩‍🏫 Tanár
                                        </ToggleButton>
                                    </ButtonGroup>
                                </Form.Group>

                                <Form.Group className="mb-4 p-3 border border-secondary rounded bg-dark bg-opacity-50 shadow-sm">
                                    <Form.Label className="text-light fw-bold">🌍 Melyik nyelvet szeretnéd tanulni?</Form.Label>
                                    <Form.Select 
                                        value={courseCode}
                                        onChange={(e) => setCourseCode(e.target.value)}
                                        className="bg-secondary text-light border-0 shadow-sm"
                                        style={{ cursor: 'pointer' }}
                                        disabled={isSubmitting}
                                    >
                                        {Object.entries(AVAILABLE_COURSES).map(([code, data]) => (
                                            <option key={code} value={code}>
                                                {data.flag} {data.name}
                                            </option>
                                        ))}
                                    </Form.Select>
                                </Form.Group>

                                <Form.Group className="mb-4 p-3 border border-secondary rounded bg-dark bg-opacity-50 shadow-sm">
                                    <Form.Label className="text-light fw-bold">🧠 Tanulási Mód</Form.Label>
                                    <Form.Select 
                                        value={preferredDifficulty}
                                        onChange={(e) => setPreferredDifficulty(e.target.value)}
                                        className="bg-secondary text-light border-0 shadow-sm"
                                        style={{ cursor: 'pointer' }}
                                        disabled={isSubmitting}
                                    >
                                        <option value="DYNAMIC">🚀 Dinamikus (Ajánlott)</option>
                                        <option value="EASY">🟢 Fix: Kezdő (Csak EASY feladatok)</option>
                                        <option value="MEDIUM">🟡 Fix: Haladó (Csak MEDIUM feladatok)</option>
                                        <option value="HARD">🔴 Fix: Profi (Csak HARD feladatok)</option>
                                    </Form.Select>
                                    <Form.Text className="text-light opacity-50 small mt-2 d-block">
                                        Ezeket a beállításokat később a profilodban bármikor módosíthatod.
                                    </Form.Text>
                                </Form.Group>

                                <Button variant="primary" type="submit" className="w-100 mb-3 py-2 fw-bold" disabled={isSubmitting}>
                                    {isSubmitting ? 'Regisztráció folyamatban...' : 'Regisztráció'}
                                </Button>
                            </Form>

                            <div className="text-center my-4 position-relative">
                                <hr className="border-secondary opacity-25" />
                                <span className="bg-transparent text-secondary px-3 position-absolute top-50 start-50 translate-middle" style={{ backgroundColor: 'var(--bs-dark)' }}>
                                    VAGY
                                </span>
                            </div>

                            <div className="d-flex flex-column gap-3 mb-4">
                                <Button 
                                    variant="outline-light" 
                                    className="fw-bold py-2 d-flex align-items-center justify-content-center gap-2"
                                    onClick={() => window.location.href = 'http://localhost:8080/oauth2/authorization/google'}
                                    disabled={isSubmitting}
                                >
                                    <img src="https://upload.wikimedia.org/wikipedia/commons/c/c1/Google_%22G%22_logo.svg" alt="Google" width="20" />
                                    Belépés Google fiókkal
                                </Button>
                                
                                <Button 
                                    variant="outline-light" 
                                    className="fw-bold py-2 d-flex align-items-center justify-content-center gap-2"
                                    style={{ borderColor: '#5865F2', color: '#5865F2' }}
                                    onClick={() => window.location.href = 'http://localhost:8080/oauth2/authorization/discord'}
                                    disabled={isSubmitting}
                                >
                                    <img src="https://assets-global.website-files.com/6257adef93867e50d84d30e2/636e0a69f118df70ad7828d4_icon_clyde_blurple_RGB.svg" alt="Discord" width="24" />
                                    Belépés Discord fiókkal
                                </Button>
                            </div>
                            
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