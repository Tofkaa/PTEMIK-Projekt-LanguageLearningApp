import { useParams, useNavigate } from 'react-router-dom';
import { Container, Button, Card } from 'react-bootstrap';

/**
 * Lesson Component
 * Ez lesz az az oldal, ahol a felhasználó végigmegy a szavakon/kérdéseken.
 */
const Lesson = () => {
    // Kinyerjük az URL-ből a lecke azonosítóját (pl. /lesson/1 -> id: 1)
    const { id } = useParams();
    const navigate = useNavigate();

    return (
        <Container className="mt-5">
            <Button variant="outline-secondary" className="mb-4" onClick={() => navigate('/dashboard')}>
                ← Vissza a Dashboardra
            </Button>
            
            <Card className="shadow-sm border-0">
                <Card.Body className="p-5 text-center">
                    <h2 className="fw-bold mb-4">Lecke betöltése...</h2>
                    <p className="text-muted">
                        Itt fogjuk lekérni a backendről a(z) <strong>{id}. azonosítójú</strong> lecke adatait és kérdéseit!
                    </p>
                    {/* Ide jön majd a kvíz logika a 6. héten! */}
                </Card.Body>
            </Card>
        </Container>
    );
};

export default Lesson;