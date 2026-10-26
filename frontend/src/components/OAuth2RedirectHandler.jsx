import { postLoginDestination } from '../utils/invitationIntent';
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
        let current = true;
        const destination = postLoginDestination();
        const authenticateUser = async () => {
            if (token) {
                try {
                    localStorage.setItem('token', token);
            
                    const response = await api.get('/users/me');
                    if (!current) return;
                 
                    await login(token, response.data, true);
                    
                    if (current) navigate(destination, { replace: true });
                } catch {
                    if (!current) return;
                    if (localStorage.getItem('token') === token) localStorage.removeItem('token');
                    navigate('/login', { state: { error: 'Sikertelen közösségi bejelentkezés.' } });
                }
            } else {
                navigate('/login', { state: { error: 'Hiányzó azonosító token.' } });
            }
        };

        authenticateUser();
        return () => { current = false; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
    }, []);

    return (
        <div className="d-flex justify-content-center align-items-center vh-100 bg-dark text-light">
            <div className="text-center">
                <Spinner aria-hidden="true" animation="border" variant="info" className="mb-3" />
                <h1 className="h4 fw-bold">Bejelentkezés folyamatban...</h1>
                <p className="text-secondary">Hitelesítés ellenőrzése a szerverrel</p>
            </div>
        </div>
    );
};

export default OAuth2RedirectHandler;
