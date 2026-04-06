import { useState } from 'react';
import { Form, Button, Alert, InputGroup, Badge } from 'react-bootstrap';
import api from '../services/api';
import { useAuth } from '../context/AuthContext';

const FriendSearch = () => {
    const { user } = useAuth();
    const [identifier, setIdentifier] = useState('');
    const [status, setStatus] = useState({ type: '', message: '' }); // type: 'success' vagy 'danger'
    const [loading, setLoading] = useState(false);

    const handleSearch = async (e) => {
        e.preventDefault();
        if (!identifier.trim()) return;

        setLoading(true);
        setStatus({ type: '', message: '' }); // Korábbi üzenetek törlése

        try {
            const response = await api.post('/friendships/request', { targetIdentifier: identifier });
            // Ha sikeres, zöld üzenetet mutatunk és kiürítjük a mezőt
            setStatus({ type: 'success', message: response.data.message || 'Kérelem elküldve!' });
            setIdentifier(''); 
        } catch (err) {
            // A Backendünk által dobott BadRequestException magyar üzenetének kiolvasása
            const errorMsg = err.response?.data?.message || 'Hiba történt a küldés során.';
            setStatus({ type: 'danger', message: errorMsg });
        } finally {
            setLoading(false);
        }
    };

    return (
        <div className="p-4 bg-dark bg-opacity-50 rounded border border-secondary shadow-sm">
            {/* ÚJ RÉSZ: Saját azonosítók kijelzése */}
            {user && (
                <div className="mb-4 p-3 bg-secondary bg-opacity-25 rounded border border-secondary d-flex justify-content-between align-items-center">
                    <div>
                        <small className="text-light d-block fw-bold mb-1">A te azonosítóid (ezt oszd meg másokkal):</small>
                        <div className="fs-5">
                            <span className="text-light fw-bold">{user.name}</span>
                            <span className="text-info">#{user.userTag}</span>
                        </div>
                    </div>
                    <div className="text-end">
                        <small className="text-light d-block fw-bold mb-1">Barátkód:</small>
                        <Badge bg="warning" text="dark" className="fs-6 font-monospace px-3 py-2 shadow-sm">
                            {user.friendCode}
                        </Badge>
                    </div>
                </div>
            )}
            <h5 className="text-light mb-2">Hozzáadás azonosító alapján</h5>
            <p className="text-light small mb-4">
                Keress név alapján (pl. <strong className="text-info">Username#1234</strong>) vagy barátkóddal (pl. <strong className="text-info">A7B-9X2</strong>).
            </p>
            
            {/* Visszajelző üzenet (Sikeres küldés vagy Hiba) */}
            {status.message && (
                <Alert variant={status.type} onClose={() => setStatus({ type: '', message: '' })} dismissible>
                    {status.message}
                </Alert>
            )}

            <Form onSubmit={handleSearch}>
                <InputGroup className="mb-3 shadow-sm">
                    <Form.Control
                        placeholder="Név#Tag vagy Barátkód..."
                        value={identifier}
                        onChange={(e) => setIdentifier(e.target.value)}
                        className="bg-secondary text-light border-secondary"
                        disabled={loading}
                    />
                    <Button variant="primary" type="submit" disabled={loading || !identifier.trim()} className="px-4 fw-bold">
                        {loading ? 'Küldés...' : 'Kérelem Küldése'}
                    </Button>
                </InputGroup>
            </Form>
        </div>
    );
};

export default FriendSearch;