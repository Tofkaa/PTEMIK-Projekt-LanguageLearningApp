import { useState, useEffect } from 'react';
import { Card, Button, Spinner, Alert, Badge, Row, Col } from 'react-bootstrap';
import api from '../services/api';

const FriendList = () => {
    const [friends, setFriends] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');

    useEffect(() => {
        fetchFriends();
    }, []);

    const fetchFriends = async () => {
        try {
            const response = await api.get('/friendships/accepted');
            setFriends(response.data || []);
        } catch (err) {
            setError('Nem sikerült betölteni a barátlistát.:', err);
        } finally {
            setLoading(false);
        }
    };

    if (loading) return <div className="text-center p-4"><Spinner animation="border" variant="info" /></div>;
    if (error) return <Alert variant="danger">{error}</Alert>;

    if (friends.length === 0) {
        return (
            <div className="p-5 text-center text-light">
                <h5 className="mb-3">Még nincsenek barátaid a listán.</h5>
                <p>Lépj át a Keresés fülre, és jelölj be valakit a kódja alapján!</p>
            </div>
        );
    }

    return (
        <div className="p-3">
            <h5 className="text-light mb-4">Barátaim ({friends.length})</h5>
            <Row>
                {friends.map((friend) => (
                    <Col md={6} key={friend.friendshipId} className="mb-3">
                        <Card className="bg-dark border-secondary h-100 shadow-sm friend-card">
                            <Card.Body className="d-flex flex-column justify-content-between">
                                <div className="d-flex justify-content-between align-items-start mb-3">
                                    <div>
                                        <h5 className="text-light mb-0 fw-bold">
                                            {friend.name}
                                        </h5>
                                        <div className="text-light small">
                                            #{friend.userTag}
                                        </div>
                                    </div>
                                    <Badge bg="secondary" text="light" className="font-monospace">
                                        {friend.friendCode}
                                    </Badge>
                                </div>
                                
                                <div className="d-flex gap-2 mt-auto">
                                    <Button 
                                        variant="outline-info" 
                                        size="sm" 
                                        className="w-100 fw-bold"
                                        disabled
                                    >
                                        ⚔️ Kihívás
                                    </Button>
                                    <Button 
                                        variant="outline-danger" 
                                        size="sm"
                                        title="Barát törlése"
                                        onClick={() => console.log('Törlés...', friend.friendshipId)}
                                    >
                                        ✖
                                    </Button>
                                </div>
                            </Card.Body>
                        </Card>
                    </Col>
                ))}
            </Row>
        </div>
    );
};

export default FriendList;