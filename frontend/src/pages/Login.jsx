import { useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { Form, Button, Card, Container, Row, Col } from 'react-bootstrap';
import api from '../services/api.jsx';
import { useAuth } from '../context/AuthContext.jsx';
import ThemeToggle from '../components/ThemeToggle.jsx';
import { useTheme } from '../context/ThemeContext.jsx';
import LearningFeedback from '../components/LearningFeedback';

/**
 * Login Component
 * Responsible for user authentication, JWT token management, 
 * and initializing the global state (AuthContext) upon successful login.
 */
const Login = () => {
    const { theme } = useTheme();
    const location = useLocation();
    // Component-level state for input fields and error handling
    const [email, setEmail] = useState('');
    const [password, setPassword] = useState('');
    const [rememberMe, setRememberMe] = useState(false);
    const [error, setError] = useState(() => typeof location.state?.error === 'string' ? location.state.error : '');
    const [isLoading, setIsLoading] = useState(false);

    const navigate = useNavigate();
    const { login } = useAuth();

    /**
     * Asynchronous handler for form submission.
     * Executes API calls and redirects the client to the protected route upon successful authentication.
     */
    const handleSubmit = async (e) => {
        e.preventDefault();
        if (isLoading) return;
        setError(''); 
        setIsLoading(true);
        
        console.log(`Initiating login attempt for: ${email}`);

        try {
            // 1. Send authentication request to the backend
           const response = await api.post('/auth/login', { 
                email, 
                password,
                rememberMe
            });
            
            const token = response.data.accessToken;
            console.log("Token received!");

            localStorage.removeItem('token');
            sessionStorage.removeItem('token');

            // 2. Immediately store the token in LocalStorage.
            // This ensures the Axios interceptor can attach it to the subsequent (/users/me) request
            // before the Context is fully updated.
           if (rememberMe) {
                localStorage.setItem('token', token);
            } else {
                sessionStorage.setItem('token', token);
            }

            // 3. Fetch the authenticated user's profile
            const userResponse = await api.get('/users/me');
            console.log("Profile data fetched:", userResponse.data);

            // 4. Synchronize the global authentication state (Context)
           login(token, userResponse.data, rememberMe);

            // 5. Redirect to the protected Dashboard view
            navigate('/dashboard');

        } catch (err) {
            console.error("Login error:", err);
            // Display a generic error message to the user for security reasons
            setError('Hibás email cím vagy jelszó!');
        } finally {
            setIsLoading(false);
        }
    };

   return (
       <Container className="auth-page mt-5">
           <div className="d-flex justify-content-end px-3">
               <ThemeToggle />
           </div>

           <Row className="justify-content-center">
               <Col md={8} lg={5}>
                   <Card className="bg-transparent border-0 mt-5 text-light">
                       <Card.Body className="p-5">
                           <h1 className="h2 text-center mb-4 fw-bold text-white">Bejelentkezés</h1>
                           
                           {/* Render error alert upon failed authentication */}
                           <LearningFeedback error={error} feedback={typeof location.state?.successMessage === 'string'
                               ? { type: 'success', msg: location.state.successMessage } : null} />

                           <Form onSubmit={handleSubmit}>
                               <Form.Group className="mb-3" controlId="formBasicEmail">
                                   <Form.Label>Email cím</Form.Label>
                                   <Form.Control 
                                        type="email" 
                                        autoComplete="username"
                                        placeholder="pelda@email.com" 
                                        value={email} 
                                        onChange={(e) => setEmail(e.target.value)} 
                                        required 
                                        disabled={isLoading}
                                    />
                               </Form.Group>

                               <Form.Group className="mb-3" controlId="formBasicPassword">
                                   <Form.Label>Jelszó</Form.Label>
                                   <Form.Control 
                                        type="password" 
                                        placeholder="Titkos jelszó" 
                                        value={password} 
                                        onChange={(e) => setPassword(e.target.value)} 
                                        required 
                                        autoComplete="current-password"
                                        disabled={isLoading}
                                    />
                               </Form.Group>
                               
                               <Form.Group className="mb-4 d-flex justify-content-between align-items-center">
                                   <Form.Check 
                                       type="checkbox" 
                                       id="rememberMeCheckbox"
                                       label="Jegyezz meg" 
                                       className="text-light opacity-75 custom-checkbox mb-0"
                                       checked={rememberMe}
                                       onChange={(e) => setRememberMe(e.target.checked)}
                                       disabled={isLoading}
                                   />
                                   <Link to="/forgot-password" className="small app-accent-link fw-bold">
                                       Elfelejtetted a jelszavad?
                                   </Link>
                               </Form.Group>

                               <Button variant="primary" type="submit" className="w-100 mb-3 py-2 fw-bold" disabled={isLoading}>
                                    {isLoading ? 'Bejelentkezés folyamatban...' : 'Belépés'}
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
                                    variant={theme === 'dark' ? 'outline-light' : 'outline-dark'}
                                    className="fw-bold py-2 d-flex align-items-center justify-content-center gap-2"
                                    onClick={() => window.location.href = 'http://localhost:8080/oauth2/authorization/google'}
                                    disabled={isLoading}
                                >
                                    <img src="https://upload.wikimedia.org/wikipedia/commons/c/c1/Google_%22G%22_logo.svg" alt="" width="20" />
                                    Belépés Google fiókkal
                                </Button>
                                
                                <Button 
                                    variant={theme === 'dark' ? 'outline-light' : 'outline-dark'}
                                    className="fw-bold py-2 d-flex align-items-center justify-content-center gap-2"
                                    onClick={() => window.location.href = 'http://localhost:8080/oauth2/authorization/discord'}
                                    disabled={isLoading}
                                >
                                    <img src="https://assets-global.website-files.com/6257adef93867e50d84d30e2/636e0a69f118df70ad7828d4_icon_clyde_blurple_RGB.svg" alt="" width="24" />
                                    Belépés Discord fiókkal
                                </Button>
                            </div>
                           
                           <div className="text-center mt-3">
                               <span className="text-light">Nincs még fiókod? </span>
                                   <Link to="/register" className="app-accent-link fw-bold">Regisztrálj itt!</Link>
                           </div>
                       </Card.Body>
                   </Card>
               </Col>
           </Row>
       </Container>
   );
};

export default Login;
