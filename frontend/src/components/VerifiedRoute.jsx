import React from 'react';
import { Navigate, useNavigate } from 'react-router-dom';
import { Container, Card, Button } from 'react-bootstrap';
import { useAuth } from '../context/AuthContext.jsx';

const VerifiedRoute = ({ children }) => {
    const { user } = useAuth();
    const navigate = useNavigate();

    if (!user) {
        return <Navigate to="/login" replace />;
    }

    if (!user.verified && !user.isVerified) {
        return (
            <Container className="d-flex justify-content-center align-items-center mt-5" style={{ minHeight: '60vh' }}>
                <Card className="bg-dark text-light border-warning shadow-lg p-4 text-center" style={{ maxWidth: '500px', width: '100%' }}>
                    <Card.Body>
                        <div className="text-warning mb-3" style={{ fontSize: '4rem' }}>🔒</div>
                        <h3 className="fw-bold text-warning mb-3">Hozzáférés Megtagadva</h3>
                        <p className="mb-4 fs-5">
                            Ennek a funkciónak a használatához előbb meg kell erősítened az e-mail címedet!
                        </p>
                        <p className="small text-secondary mb-4">
                            Kérjük, keresd meg a postafiókodban a regisztrációkor küldött aktiváló linket.
                        </p>
                        <Button variant="outline-light" onClick={() => navigate('/dashboard')} className="w-100 fw-bold">
                            Vissza a Dashboardra
                        </Button>
                    </Card.Body>
                </Card>
            </Container>
        );
    }

    return children;
};

export default VerifiedRoute;