import React, { useState, useEffect } from 'react';
import { Toast, ToastContainer } from 'react-bootstrap';

const GlobalErrorToast = () => {
    const [show, setShow] = useState(false);
    const [message, setMessage] = useState('');

    useEffect(() => {
        const handleErrorEvent = (e) => {
            setMessage(e.detail);
            setShow(true);
        };

        window.addEventListener('api-error', handleErrorEvent);
        return () => window.removeEventListener('api-error', handleErrorEvent);
    }, []);

    return (
        <ToastContainer position="bottom-end" className="p-4" style={{ zIndex: 9999, position: 'fixed' }}>
            <Toast 
                onClose={() => setShow(false)} 
                show={show} 
                delay={5000} 
                autohide 
                bg="danger"
                className="shadow-lg rounded-4 border border-secondary"
            >
                <Toast.Header className="bg-dark text-danger border-secondary rounded-top-4">
                    <strong className="me-auto">⚠️ Rendszerüzenet</strong>
                </Toast.Header>
                <Toast.Body className="text-light fw-bold">
                    {message}
                </Toast.Body>
            </Toast>
        </ToastContainer>
    );
};

export default GlobalErrorToast;