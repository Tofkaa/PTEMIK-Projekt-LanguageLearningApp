import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Form, Button, Card, Container, Row, Col, Alert } from 'react-bootstrap';
import api from '../services/api.jsx';
import { useAuth } from '../context/AuthContext.jsx';

/**
 * Login Component
 * Responsible for user authentication, JWT token management, 
 * and initializing the global state (AuthContext) upon successful login.
 */
const Login = () => {
    // Component-level state for input fields and error handling
    const [email, setEmail] = useState('');
    const [password, setPassword] = useState('');
    const [error, setError] = useState('');

    const navigate = useNavigate();
    const { login } = useAuth();

    /**
     * Asynchronous handler for form submission.
     * Executes API calls and redirects the client to the protected route upon successful authentication.
     * * @param {React.FormEvent} e - The form submission event
     */
    const handleSubmit = async (e) => {
        e.preventDefault();
        setError(''); 
        
        console.log(`Initiating login attempt for: ${email}`);

        try {
            // 1. Send authentication request to the backend
            const response = await api.post('/auth/login', { 
                email, 
                password 
            });
            
            const token = response.data.accessToken;
            console.log("Token received!");

            // 2. Immediately store the token in LocalStorage.
            // This ensures the Axios interceptor can attach it to the subsequent (/users/me) request
            // before the Context is fully updated.
            localStorage.setItem('token', token);

            // 3. Fetch the authenticated user's profile
            const userResponse = await api.get('/users/me');
            console.log("Profile data fetched:", userResponse.data);

            // 4. Synchronize the global authentication state (Context)
            login(token, userResponse.data); 

            // 5. Redirect to the protected Dashboard view
            navigate('/dashboard');

        } catch (err) {
            console.error("Login error:", err);
            // Display a generic error message to the user for security reasons
            setError('Hibás email cím vagy jelszó!');
        }
    };

   return (
       <Container className="mt-5">
           <Row className="justify-content-center">
               <Col md={8} lg={5}>
                   <Card className="shadow-sm border-0 rounded-lg mt-5">
                       <Card.Body className="p-5">
                           <h2 className="text-center mb-4 fw-bold">Bejelentkezés</h2>
                           
                           {/* Render error alert upon failed authentication */}
                           {error && <Alert variant="danger">{error}</Alert>}

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
                                        autoComplete="current-password"
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