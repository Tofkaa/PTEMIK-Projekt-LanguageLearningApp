import { Navbar, Container, Nav, Button, Badge } from 'react-bootstrap';
import { useAuth } from '../context/AuthContext.jsx';
import { useNavigate } from 'react-router-dom';

/**
 * NavigationBar Component
 * Renders the top navigation bar containing the app branding, 
 * user statistics (XP, Role), and the logout functionality.
 */
const NavigationBar = () => {
    const { user, logout } = useAuth();
    const navigate = useNavigate();

    /**
     * Handles the user logout process and redirects to the login screen.
     */
    const handleLogout = () => {
        logout();
        navigate('/login');
    };

    // If there is no authenticated user, do not render the navbar
    if (!user) return null;

    return (
        <Navbar bg="dark" variant="dark" expand="lg" className="shadow-sm mb-4">
            <Container>
                {/* Brand / Logo Area */}
                <Navbar.Brand className="fw-bold" style={{ cursor: 'pointer' }} onClick={() => navigate('/dashboard')}>
                    🚀 AdaptiveApp
                </Navbar.Brand>
                
                {/* Mobile Toggle Button */}
                <Navbar.Toggle aria-controls="basic-navbar-nav" />
                
                {/* Navigation Links and User Info */}
                <Navbar.Collapse id="basic-navbar-nav">
                    <Nav className="me-auto">
                        <Nav.Link onClick={() => navigate('/dashboard')}>Dashboard</Nav.Link>
                        {/* Future links (e.g., Leaderboard, Profile) can go here */}
                    </Nav>
                    
                    <Nav className="align-items-center">
                        {/* Display User Name and XP */}
                        <Navbar.Text className="me-3 text-light">
                            Hello, <span className="fw-bold text-white">{user.name}</span>! 
                            <Badge bg="warning" text="dark" className="ms-2">
                                {user.xp || 0} XP
                            </Badge>
                        </Navbar.Text>
                        
                        {/* Logout Button */}
                        <Button variant="outline-light" size="sm" onClick={handleLogout}>
                            Kijelentkezés
                        </Button>
                    </Nav>
                </Navbar.Collapse>
            </Container>
        </Navbar>
    );
};

export default NavigationBar;