import React from 'react';
import { Container } from 'react-bootstrap';

const AdminDashboard = () => {
    return (
        <Container className="py-5 text-light">
            <div className="d-flex justify-content-between align-items-center mb-4">
                <h1 className="fw-bold m-0 text-danger">🛡️ Adminisztrációs Panel</h1>
            </div>
            
            <div className="p-5 border border-secondary rounded bg-dark text-center shadow-lg">
                <h3 className="text-secondary fw-bold mb-3">A kezelőfelület moduljai holnap érkeznek! 🚀</h3>
                <p className="text-muted">Itt fogjuk kezelni a felhasználókat, az osztálytermeket és a rendszernaplókat.</p>
            </div>
        </Container>
    );
};

export default AdminDashboard;