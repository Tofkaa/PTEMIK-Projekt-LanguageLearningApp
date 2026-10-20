import { useState, useEffect, useRef } from 'react';
import { Toast, ToastContainer } from 'react-bootstrap';
import { useAuth } from '../context/AuthContext';

// Remount on logout/account change; errors and return-focus targets belong to one session.
const GlobalErrorToast = () => {
    const { user } = useAuth();
    return <SessionErrorToast key={user?.userId ?? 'guest'} />;
};

const SessionErrorToast = () => {
    const [show, setShow] = useState(false);
    const [message, setMessage] = useState('');
    const toastRef = useRef(null);
    const returnFocusRef = useRef(null);

    useEffect(() => {
        const handleErrorEvent = (e) => {
            if (typeof e.detail !== 'string' || !e.detail.trim()) return;
            const active = document.activeElement;
            if (active instanceof HTMLElement && active !== document.body && !toastRef.current?.contains(active)) {
                returnFocusRef.current = active;
            }
            setMessage(e.detail);
            setShow(true);
        };

        window.addEventListener('api-error', handleErrorEvent);
        return () => window.removeEventListener('api-error', handleErrorEvent);
    }, []);

    const close = () => {
        const focusedInside = toastRef.current?.contains(document.activeElement);
        setShow(false);
        if (!focusedInside) return;
        const target = returnFocusRef.current;
        if (target?.isConnected && !target.matches(':disabled')) target.focus();
        else document.getElementById('main-content')?.focus();
    };

    return (
        <ToastContainer position="bottom-end" className="p-4" style={{ zIndex: 9999, position: 'fixed' }}>
            <Toast 
                ref={toastRef}
                onClose={close}
                onKeyDown={event => {
                    if (event.key === 'Escape') {
                        event.preventDefault();
                        event.stopPropagation();
                        close();
                    }
                }}
                show={show} 
                className="global-error-toast shadow-lg rounded-4"
            >
                <Toast.Header closeLabel="Hibaüzenet bezárása" className="rounded-top-4">
                    <strong className="me-auto">⚠️ Rendszerüzenet</strong>
                </Toast.Header>
                <Toast.Body className="fw-bold">
                    {message}
                </Toast.Body>
            </Toast>
        </ToastContainer>
    );
};

export default GlobalErrorToast;
