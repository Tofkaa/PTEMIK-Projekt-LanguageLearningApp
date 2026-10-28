import { postLoginDestination } from '../utils/invitationIntent';
import { useEffect, useRef, useState } from 'react';
import axios from 'axios';
import { useNavigate, useLocation } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { Spinner } from 'react-bootstrap';

const OAuth2RedirectHandler = () => {
    const location = useLocation();
    const navigate = useNavigate();
    const { completeOAuthLogin } = useAuth();
    const [code] = useState(() => new URLSearchParams(location.hash.slice(1)).get('code'));
    const attempt = useRef(null);

    useEffect(() => {
        let active = true;
        // The fragment never reaches access logs; remove it before any further navigation.
        window.history.replaceState(window.history.state, '', window.location.pathname);
        if (!code) {
            navigate('/login', { state: { error: 'Hiányzó belépési jóváhagyás. Indíts új közösségi belépést.' }, replace: true });
            return;
        }
        // React StrictMode may replay this effect; keep one authentication attempt per callback.
        if (attempt.current?.code !== code) attempt.current = { code, destination: postLoginDestination(), promise: completeOAuthLogin(code) };
        const destination = attempt.current.destination;
        attempt.current.promise.then(() => {
            if (active) navigate(destination, { replace: true });
        }).catch(error => {
            if (active && !axios.isCancel(error)) navigate('/login', {
                state: { error: 'Sikertelen közösségi bejelentkezés.' }, replace: true,
            });
        });
        return () => { active = false; };
    }, [code, completeOAuthLogin, navigate]);

    return <div className="d-flex justify-content-center align-items-center vh-100 bg-dark text-light">
        <div className="text-center">
            <Spinner aria-hidden="true" animation="border" variant="info" className="mb-3" />
            <h1 className="h4 fw-bold">Bejelentkezés folyamatban...</h1>
            <p className="text-secondary">Hitelesítés ellenőrzése a szerverrel</p>
        </div>
    </div>;
};

export default OAuth2RedirectHandler;
