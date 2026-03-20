import { useState } from 'react';
import { Container, Row, Col, Card, Form, Button, Badge, Spinner, Alert } from 'react-bootstrap';
import { useAuth } from '../context/AuthContext.jsx';
import api from '../services/api.jsx';
import Navigation from '../components/NavigationBar.jsx'; 
import AchievementsSection from '../components/profile/AchievementSection.jsx';
import RecentResultsSection from '../components/profile/RecentResultsSection.jsx';

/**
 * Profile Component
 * * Displays user statistics (XP, Role, Details) and allows the user to update
 * their learning preferences (e.g., preferred difficulty) by interacting with the backend API.
 */
const Profile = () => {
    const { user, login } = useAuth();
    
    // --- STATE MANAGEMENT ---
    const [difficulty, setDifficulty] = useState(user?.preferredDifficulty || 'MEDIUM');
    const [isUpdating, setIsUpdating] = useState(false);
    const [message, setMessage] = useState({ type: '', text: '' });
    
    // Placeholder state for future achievement/history fetching
    //const [isLoadingStats, setIsLoadingStats] = useState(false);

    // --- EVENT HANDLERS ---
    
    /**
     * Handles the submission of user preferences to the backend.
     * Updates local context and displays a self-dismissing success alert.
     * * @param {Event} e - Form submission event
     */
    const handleSavePreferences = async (e) => {
        e.preventDefault();
        setIsUpdating(true);
        setMessage({ type: '', text: '' });

        try {
            // Persist the new difficulty preference to the database
            await api.put('/users/me/preferences', { 
                preferredDifficulty: difficulty 
            });
            
            // Update the global authentication context with the new user state
            const updatedUser = { ...user, preferredDifficulty: difficulty };
            login(localStorage.getItem('token'), updatedUser);

            // Display success feedback
            setMessage({ type: 'success', text: 'Beállítások sikeresen mentve! ✅' });
            
            // Automatically clear the message after 3 seconds
            setTimeout(() => {
                setMessage({ type: '', text: '' });
            }, 3000);

        } catch (err) {
            console.error("Error saving preferences:", err);
            setMessage({ type: 'danger', text: 'Hiba történt a beállítások mentésekor.' });
        } finally {
            setIsUpdating(false);
        }
    };

    // Prevent rendering if user context is not yet populated
    if (!user) return null;

    return (
        <>
            <Navigation /> 

            <Container className="mt-5 pt-4 text-light pb-5">
                <h2 className="fw-bold mb-4 text-info">Felhasználói Profil</h2>

                <Row className="g-4">
                    {/* --- LEFT COLUMN: USER INFO --- */}
                    <Col md={4}>
                        <Card className="shadow-lg border-0 bg-dark text-center rounded-4 h-100">
                            <Card.Body className="p-4 d-flex flex-column align-items-center justify-content-center">
                                {/* User Avatar */}
                                <div 
                                    className="rounded-circle bg-secondary bg-opacity-50 d-flex align-items-center justify-content-center mb-3 shadow"
                                    style={{ width: '100px', height: '100px', fontSize: '3rem' }}
                                >
                                    👤
                                </div>
                                
                                <h4 className="fw-bold mb-1">{user.name}</h4>
                                <p className="text-muted mb-3">{user.email}</p>
                                
                                <Badge bg="info" text="dark" className="px-3 py-2 rounded-pill mb-4 fw-bold">
                                    {user.role === 'ADMIN' ? 'Rendszergazda' : 'Diák'}
                                </Badge>

                                {/* Total Experience Points */}
                                <div className="w-100 p-3 bg-black bg-opacity-25 rounded-3 border border-secondary text-start">
                                    <span className="text-light opacity-75 small text-uppercase fw-bold">Összes XP</span>
                                    <h3 className="text-warning fw-bold mb-0">⭐ {user.xp}</h3>
                                </div>
                            </Card.Body>
                        </Card>
                    </Col>

                    {/* --- RIGHT COLUMN: SETTINGS & HISTORY --- */}
                    <Col md={8}>
                        
                        {/* Settings Card */}
                        <Card className="shadow-lg border-0 bg-dark rounded-4 mb-4">
                            <Card.Body className="p-4">
                                <h4 className="text-info fw-bold mb-3">⚙️ Tanulási Beállítások</h4>
                                <p className="text-light opacity-75 mb-4">
                                    Itt felülírhatod az adaptív algoritmust, és beállíthatod, hogy milyen nehézségű leckéket szeretnél kapni.
                                </p>

                                {/* Fixed-height container to prevent layout shift during alerts */}
                                <div style={{ minHeight: '60px' }} className="mb-2">
                                    {message.text && (
                                        <Alert variant={message.type} className="rounded-3 border-0 fw-bold m-0 shadow-sm">
                                            {message.text}
                                        </Alert>
                                    )}
                                </div>

                                {/* Preferences Form */}
                                <Form onSubmit={handleSavePreferences}>
                                    <Form.Group className="mb-4" controlId="difficultySelect">
                                        <Form.Label className="fw-bold text-light opacity-75">Célzott Nehézség</Form.Label>
                                        <Form.Select 
                                            className="bg-secondary bg-opacity-25 text-light border-secondary fs-5 p-3 shadow-none"
                                            value={difficulty}
                                            onChange={(e) => setDifficulty(e.target.value)}
                                        >
                                            <option value="DYNAMIC" className="text-dark">🔵 Dinamikus (DYNAMIC) - Nehézség a teljesítményed alapján</option>
                                            <option value="EASY" className="text-dark">🟢 Kezdő (EASY) - Több kártyás feladat</option>
                                            <option value="MEDIUM" className="text-dark">🟡 Haladó (MEDIUM) - Vegyes feladatok</option>
                                            <option value="HARD" className="text-dark">🔴 Profi (HARD) - Csak gépelés</option>
                                        </Form.Select>
                                    </Form.Group>

                                    <div className="text-end">
                                        <Button 
                                            variant="info" 
                                            type="submit" 
                                            className="fw-bold px-4 rounded-pill text-dark"
                                            disabled={isUpdating || difficulty === user.preferredDifficulty}
                                        >
                                            {isUpdating ? <Spinner size="sm" /> : 'Beállítások Mentése'}
                                        </Button>
                                    </div>
                                </Form>
                            </Card.Body>
                        </Card>

                        {/* Results */}
                        <Card className="bg-dark text-light border-0 shadow-lg mb-4 rounded-4 p-2">
                                <Card.Body>
                                    <h4 className="fw-bold text-info mb-3 border-bottom border-secondary pb-2">
                                        <span className="me-2">📊</span> Legutóbbi Eredmények
                                    </h4>
                                    
                                    <RecentResultsSection />
                                    
                                </Card.Body>
                        </Card>

                        {/* Trophies */}
                        <Card className="bg-dark text-light border-0 shadow-lg rounded-4 p-2">
                            <Card.Body>
                                <h4 className="fw-bold text-warning mb-3 border-bottom border-secondary pb-2">
                                    <span className="me-2">🎖️</span> Kitüntetéseim
                                </h4>

                                <AchievementsSection />
                                
                            </Card.Body>
                        </Card>
                    </Col>
                </Row>
            </Container>
        </>
    );
};

export default Profile;