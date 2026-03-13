import { Container, Button } from 'react-bootstrap';
import { useNavigate } from 'react-router-dom';

/**
 * NotFound Component
 * Displays a 404 error page for unmatched routes.
 * Styled to match the global dark/cyan theme of the application.
 */
const NotFound = () => {
    const navigate = useNavigate();

    return (
        <Container className="d-flex flex-column justify-content-center align-items-center min-vh-100 text-light text-center">
            <h1 className="display-1 fw-bold" style={{ color: 'var(--primary-cyan)', fontSize: '6rem' }}>404</h1>
            <h2 className="mb-4 fw-bold">Hoppá, eltévedtél!</h2>
            <p className="text-light opacity-75 mb-4 fs-5">
                Az oldal, amit keresel, a sötét anyagba veszett, vagy sosem létezett.
            </p>
            <Button 
                variant="primary" 
                size="lg" 
                className="px-5 py-3 shadow"
                onClick={() => navigate('/dashboard')}
            >
                Vissza a bázisra
            </Button>
        </Container>
    );
};

export default NotFound;