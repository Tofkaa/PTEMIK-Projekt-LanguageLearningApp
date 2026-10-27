import { postLoginDestination } from '../utils/invitationIntent';
import { useEffect, useRef } from 'react';
import axios from 'axios';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { Spinner } from 'react-bootstrap';

const OAuth2RedirectHandler = () => {
    const [searchParams] = useSearchParams();
    const navigate = useNavigate();
    const { completeOAuthLogin } = useAuth();
    const token = searchParams.get('token');
    const attempt = useRef(null);

    useEffect(() => {
        let active = true;
        if (!token) {
            navigate('/login', { state: { error: 'Hiányzó azonosító token.' }, replace: true });
            return;
        }
        // React StrictMode may replay this effect; keep one authentication attempt per callback.
        if (attempt.current?.token !== token) attempt.current = { token, destination: postLoginDestination(), promise: completeOAuthLogin(token) };
        const destination = attempt.current.destination;
        attempt.current.promise.then(() => {
            if (active) navigate(destination, { replace: true });
        }).catch(error => {
            if (active && !axios.isCancel(error)) navigate('/login', {
                state: { error: 'Sikertelen közösségi bejelentkezés.' }, replace: true,
            });
        });
        return () => { active = false; };
    }, [token, completeOAuthLogin, navigate]);

    return <div className="d-flex justify-content-center align-items-center vh-100 bg-dark text-light">
        <div className="text-center">
            <Spinner aria-hidden="true" animation="border" variant="info" className="mb-3" />
            <h1 className="h4 fw-bold">Bejelentkezés folyamatban...</h1>
            <p className="text-secondary">Hitelesítés ellenőrzése a szerverrel</p>
        </div>
    </div>;
};

export default OAuth2RedirectHandler;
