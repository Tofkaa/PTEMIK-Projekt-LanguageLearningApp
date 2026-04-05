import { useState, useEffect } from 'react';
import { Card, Button, Spinner, Alert } from 'react-bootstrap';
import api from '../services/api';
import { useNotifications } from '../context/NotificationContext';

const PendingRequests = () => {
    const [requests, setRequests] = useState([]);
    const [loading, setLoading] = useState(true);
    const [message, setMessage] = useState({ type: '', text: '' });
    const {notifications} = useNotifications();

    useEffect(() => {
        fetchPendingRequests(true); 
    }, [notifications.PendingFriends]);

    const fetchPendingRequests = async (isInitialLoad = false) => {
        if (isInitialLoad) setLoading(true);

        try {
            const response = await api.get('/friendships/requests/pending');
            // Biztosítjuk, hogy ha a backend üreset ad, akkor is tömb legyen
            setRequests(response.data || []);
        } catch (err) {
            if (isInitialLoad) setMessage({ type: 'danger', text: 'Nem sikerült betölteni a kérelmeket.', err });
        } finally {
            if (isInitialLoad) setLoading(false);
        }
    };

    const handleAction = async (friendshipId, action) => {
        try {
            const endpoint = `/friendships/requests/${friendshipId}/${action}`;
            const response = await api.post(endpoint);
            
            // Sikeres üzenet kiírása
            setMessage({ type: 'success', text: response.data.message });
            
            // Eltávolítjuk a kártyát a UI-ról azonnal, oldalfrissítés nélkül
            setRequests(prevRequests => prevRequests.filter(req => req.friendshipId !== friendshipId));
        } catch (err) {
            const errorMsg = err.response?.data?.message || 'Hiba történt a művelet során.';
            setMessage({ type: 'danger', text: errorMsg });
        }
    };

    if (loading) return <div className="text-center p-4"><Spinner animation="border" variant="info" /></div>;

    return (
        <div className="p-3">
            {message.text && (
                <Alert variant={message.type} onClose={() => setMessage({ type: '', text: '' })} dismissible>
                    {message.text}
                </Alert>
            )}

            {/* Üres állapot (Empty State) gyönyörű kezelése hiba nélkül */}
            {(!requests || requests.length === 0) ? (
                <div className="p-5 text-center text-light">
                    <h5 className="mb-3">Minden csendes...</h5>
                    <p>Jelenleg nincsenek függőben lévő barátkérelmeid.</p>
                </div>
            ) : (
                <>
                    <h5 className="text-light mb-4">Beérkező kérelmek ({requests.length})</h5>
                    {requests.map((request) => (
                        <Card key={request.friendshipId} className="bg-dark border-secondary mb-3 shadow-sm">
                            <Card.Body className="d-flex justify-content-between align-items-center">
                                <div>
                                    <h6 className="text-light mb-1 fw-bold">
                                        {request.senderName} <span className="text-info opacity-75">#{request.senderTag}</span>
                                    </h6>
                                    <small className="text-light">
                                        {new Date(request.sentAt).toLocaleDateString('hu-HU')}
                                    </small>
                                </div>
                                <div className="d-flex gap-2">
                                    <Button 
                                        variant="success" 
                                        size="sm" 
                                        className="fw-bold"
                                        onClick={() => handleAction(request.friendshipId, 'accept')}
                                    >
                                        Elfogad
                                    </Button>
                                    <Button 
                                        variant="outline-danger" 
                                        size="sm" 
                                        onClick={() => handleAction(request.friendshipId, 'reject')}
                                    >
                                        Elutasít
                                    </Button>
                                </div>
                            </Card.Body>
                        </Card>
                    ))}
                </>
            )}
        </div>
    );
};

export default PendingRequests;