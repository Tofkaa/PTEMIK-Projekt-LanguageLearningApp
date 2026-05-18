import { Container, Button } from 'react-bootstrap';
import { useNavigate } from 'react-router-dom';

/**
 * Unauthorized Component (403)
 * Displays an access denied page for users trying to reach admin/restricted routes.
 */
const Unauthorized = () => {
    const navigate = useNavigate();

    return (
        <Container className="d-flex flex-column justify-content-center align-items-center min-vh-100 text-light text-center">
            <h1 className="display-1 fw-bold text-danger" style={{ fontSize: '6rem' }}>403</h1>
            <h2 className="mb-4 fw-bold">Hozzáférés megtagadva! 🛑</h2>
            <p className="text-light opacity-75 mb-4 fs-5" style={{ maxWidth: '600px' }}>
                Nincs megfelelő jogosultságod ennek az oldalnak a megtekintéséhez. 
                Ez a terület szigorúan csak Adminisztrátorok számára elérhető!
            </p>
            <Button 
                variant="outline-danger" 
                size="lg" 
                className="px-5 py-3 shadow rounded-pill fw-bold"
                onClick={() => navigate('/dashboard')}
            >
                Vissza a biztonságba
            </Button>
        </Container>
    );
};

export default Unauthorized;