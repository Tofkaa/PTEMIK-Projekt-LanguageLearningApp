import { Navbar, Container, Nav, Badge, NavDropdown } from 'react-bootstrap';
import { useAuth } from '../context/AuthContext.jsx';
import { useNavigate } from 'react-router-dom';
import { useNotifications } from '../context/NotificationContext.jsx';
import { useEffect, useState } from 'react'; 

const NavigationBar = () => {
    const { user, logout } = useAuth();
    const navigate = useNavigate();
    const { notifications } = useNotifications();
    
    const [, setLocalUpdate] = useState(0);

    // Kiterjesztett eseményfigyelő: Reagál a saját ablakban történő kattintásokra is!
    useEffect(() => {
        const handleStorage = () => setLocalUpdate(prev => prev + 1);
        window.addEventListener('storage', handleStorage);
        window.addEventListener('local-storage-update', handleStorage); 
        return () => {
            window.removeEventListener('storage', handleStorage);
            window.removeEventListener('local-storage-update', handleStorage);
        };
    }, []);

    const handleLogout = () => {
        localStorage.removeItem('viewedAssignments');
        localStorage.removeItem('viewedResults');
        logout();
        navigate('/login');
    };

   if (!user) return null;

    const viewedAssignments = JSON.parse(localStorage.getItem('viewedAssignments') || '[]');
    const viewedResults = JSON.parse(localStorage.getItem('viewedResults') || '[]');
    const viewedTeacherPending = JSON.parse(localStorage.getItem('viewedTeacherPending') || '[]');
    const viewedTeacherUngraded = JSON.parse(localStorage.getItem('viewedTeacherUngraded') || '[]');

    const unseenAssignments = (notifications?.studentActiveAssignmentIds || []).filter(id => !viewedAssignments.includes(id)).length;
    const unseenResults = (notifications?.studentGradedSessionIds || []).filter(id => !viewedResults.includes(id)).length;
    const unseenTeacherPending = (notifications?.teacherPendingJoinRequestIds || []).filter(id => !viewedTeacherPending.includes(id)).length;
    const unseenTeacherUngraded = (notifications?.teacherUngradedSubmissionIds || []).filter(id => !viewedTeacherUngraded.includes(id)).length;

    const classroomPings = unseenAssignments + unseenResults + unseenTeacherPending + unseenTeacherUngraded;
    const communityPings = (notifications?.pendingFriendRequests || 0) + (notifications?.pendingChallenges || 0);
                           
    return (
        <Navbar bg="dark" variant="dark" expand="lg" className="shadow-sm mb-4 border-bottom border-secondary">
            <Container>
                <Navbar.Brand className="fw-bold" style={{ cursor: 'pointer', color: 'var(--primary-cyan)' }} onClick={() => navigate('/dashboard')}>
                    🚀 LanguageApp
                </Navbar.Brand>
                
                <Navbar.Toggle aria-controls="basic-navbar-nav" />
                <Navbar.Collapse id="basic-navbar-nav">
                    <Nav className="me-auto gap-2">
                        <Nav.Link onClick={() => navigate('/dashboard')} className="fw-bold">Dashboard</Nav.Link>

                        <Nav.Link onClick={() => navigate('/classrooms')} className="fw-bold position-relative me-3">
                            🏫 Osztálytermek
                            {classroomPings > 0 && (
                                <Badge bg="danger" pill className="position-absolute top-25 start-100 translate-middle shadow-sm" style={{ fontSize: '0.65rem' }}>
                                    {classroomPings}
                                </Badge>
                            )}
                        </Nav.Link>

                        <Nav.Link onClick={() => navigate('/friends')} className="fw-bold position-relative me-3">
                            🌐 Közösség
                            {communityPings > 0 && (
                                <Badge bg="danger" pill className="position-absolute top-25 start-100 translate-middle shadow-sm" style={{ fontSize: '0.65rem' }}>
                                    {communityPings}
                                </Badge>
                            )}
                        </Nav.Link>
                    </Nav>
                    {/* Csak az adminok látják ezt a gombot a lenyíló menüben */}
                        {(user.role === 'ADMIN' || user.role === 'ROLE_ADMIN') && (
                            <>
                                <NavDropdown.Item onClick={() => navigate('/admin')} className="text-danger fw-bold">🛡️ Admin Panel</NavDropdown.Item>
                                <NavDropdown.Divider />
                            </>
                        )}
                    
                    <Nav className="align-items-center">
                        <Badge bg="warning" text="dark" className="me-3 rounded-pill px-3 py-2 shadow-sm">
                            ⭐ {user.xp || 0} XP
                        </Badge>
                        
                        <NavDropdown title={<span className="text-light fw-bold">👤 {user.name}</span>} id="basic-nav-dropdown" align="end" menuVariant="dark">
                            <NavDropdown.Item onClick={() => navigate('/profile')} className="text-light">Profilom</NavDropdown.Item>
                            <NavDropdown.Divider />
                            <NavDropdown.Item onClick={handleLogout} className="text-danger fw-bold">Kijelentkezés</NavDropdown.Item>
                        </NavDropdown>
                    </Nav>
                </Navbar.Collapse>
            </Container>
        </Navbar>
    );
};

export default NavigationBar;