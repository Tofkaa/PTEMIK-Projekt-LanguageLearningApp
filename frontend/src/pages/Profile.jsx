import { Container, Row, Col, Card, Badge, Button } from 'react-bootstrap';
import { useAuth } from '../context/AuthContext.jsx';
import { Link, useNavigate } from 'react-router-dom';
import AchievementsSection from '../components/profile/AchievementSection.jsx';
import RecentResultsSection from '../components/profile/RecentResultsSection.jsx';

const Profile = () => {
    const { user } = useAuth();
    const navigate = useNavigate();
    
    const roleLabels = {
        ADMIN: 'Rendszergazda',
        TEACHER: 'Tanár',
        STUDENT: 'Diák'
    };

    if (!user) return null;

    return (
        <Container className="mt-5 pt-4 text-light pb-5">
            <div className="d-flex flex-wrap gap-3 justify-content-between align-items-center mb-4">
                <h1 className="h2 fw-bold text-info mb-0">Felhasználói Profil</h1>
                <Button variant="outline-info" className="fw-bold rounded-pill px-4" onClick={() => navigate('/settings')}>
                    <span aria-hidden="true">⚙️ </span>Fiók beállításai
                </Button>
            </div>

            <Row className="g-4">
                {/* --- LEFT COLUMN: USER DATA --- */}
                <Col md={4}>
                    <div className="sticky-top" style={{ top: '20px', zIndex: 10 }}>
                        <Card className="shadow-lg border-0 bg-dark text-center rounded-4">
                            <Card.Body className="p-4 d-flex flex-column align-items-center">
                                
                                {/* Clickable Avatar (Redirects to the settings page) */}
                                <Link to="/settings" state={{ settingsTab: 'account' }} aria-label="Profilkép módosítása"
                                    className="position-relative mx-auto mb-3" 
                                    style={{ width: '120px', height: '120px', cursor: 'pointer' }}
                                >
                                    {user.profilePictureUrl ? (
                                        <img 
                                            src={user.profilePictureUrl} 
                                            alt=""
                                            className="w-100 h-100 rounded-circle object-fit-cover border border-2 border-info shadow"
                                        />
                                    ) : (
                                        <div className="w-100 h-100 rounded-circle bg-secondary bg-opacity-50 d-flex justify-content-center align-items-center border border-2 border-secondary shadow">
                                            <span aria-hidden="true" style={{ fontSize: '4rem' }}>👤</span>
                                        </div>
                                    )}
                                    <Badge aria-hidden="true" bg="info" pill className="position-absolute bottom-0 end-0 p-2 shadow">
                                        ✏️
                                    </Badge>
                                </Link>
                                
                                <h2 className="h4 fw-bold text-light mb-1">{user.name}</h2>
                                <p className="text-light mb-3">{user.email}</p>
                                <div className="text-center mt-3 mb-4">
                                    <p className="h3 text-light fw-bold mb-1">
                                        {user.name} <span className="text-info fs-5">#{user.userTag}</span>
                                    </p>
                                    <Badge bg="dark" border="secondary" className="border text-light font-monospace px-3 py-2 mt-2 fs-6">
                                        Barátkód: <span className="text-warning">{user.friendCode}</span>
                                    </Badge>
                                </div>
                                <Badge bg="info" text="dark" className="px-3 py-2 rounded-pill mb-4 fw-bold">
                                    {roleLabels[user.role] || 'Diák'}
                                </Badge>

                                <dl className="d-flex flex-column gap-3 w-100 mb-2">
                                    <div className="w-100 p-3 bg-black bg-opacity-25 rounded-3 border border-secondary text-start">
                                        <dt className="text-body-secondary small text-uppercase fw-bold">Összes XP</dt>
                                        <dd className="h3 text-warning fw-bold mb-0"><span aria-hidden="true">⭐ </span>{user.xp}</dd>
                                    </div>
                                    <div className="w-100 p-3 bg-black bg-opacity-25 rounded-3 border border-secondary d-flex flex-wrap gap-2 justify-content-between align-items-center">
                                        <dt className="text-body-secondary small text-uppercase fw-bold">Napi sorozat</dt>
                                        <dd className="h4 text-warning fw-bold mb-0"><span aria-hidden="true">🔥 </span>{user.streak || 0} nap</dd>
                                    </div>
                                </dl>
                            </Card.Body>
                        </Card>
                    </div>
                </Col>

                {/* --- RIGHT COLUMN: RESULTS AND ACHIEVEMENTS --- */}
                <Col md={8}>
                    <Card className="bg-dark text-light border-0 shadow-lg mb-4 rounded-4 p-2">
                        <Card.Body>
                            <h2 className="h4 fw-bold text-info mb-3 border-bottom border-secondary pb-2">
                                <span aria-hidden="true" className="me-2">📊</span> Legutóbbi Eredmények
                            </h2>
                            <RecentResultsSection />
                        </Card.Body>
                    </Card>

                    <Card className="bg-dark text-light border-0 shadow-lg rounded-4 p-2">
                        <Card.Body>
                            <h2 className="h4 fw-bold text-warning mb-3 border-bottom border-secondary pb-2">
                                <span aria-hidden="true" className="me-2">🎖️</span> Kitüntetéseim
                            </h2>
                            <AchievementsSection />
                        </Card.Body>
                    </Card>
                </Col>
            </Row>
        </Container>
    );
};

export default Profile;
