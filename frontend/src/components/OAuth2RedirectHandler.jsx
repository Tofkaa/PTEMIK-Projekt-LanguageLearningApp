import { useEffect } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import api from '../services/api';
import { Spinner } from 'react-bootstrap';

const OAuth2RedirectHandler = () => {
    const [searchParams] = useSearchParams();
    const navigate = useNavigate();
    const { login } = useAuth();
    const token = searchParams.get('token');

    useEffect(() => {
        const authenticateUser = async () => {
            if (token) {
                try {
                    localStorage.setItem('token', token);
            
                    const response = await api.get('/users/me');
                 
                    await login(token, response.data, true);
                    
                    navigate('/dashboard', { replace: true });
                } catch (error) {
                    console.error('Hiba az OAuth2 token feldolgozásakor:', error);
                    localStorage.removeItem('token');
                    navigate('/login', { state: { error: 'Sikertelen közösségi bejelentkezés.' } });
                }
            } else {
                navigate('/login', { state: { error: 'Hiányzó azonosító token.' } });
            }
        };

        authenticateUser();
    // eslint-disable-next-line react-hooks/exhaustive-deps
    }, []);

    return (
        <div className="d-flex justify-content-center align-items-center vh-100 bg-dark text-light">
            <div className="text-center">
                <Spinner animation="border" variant="info" className="mb-3" />
                <h4 className="fw-bold">Bejelentkezés folyamatban...</h4>
                <p className="text-secondary">Hitelesítés ellenőrzése a szerverrel</p>
            </div>
        </div>
    );
};

export default OAuth2RedirectHandler;