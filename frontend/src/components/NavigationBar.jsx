import { Navbar, Container, Nav, Badge, NavDropdown } from 'react-bootstrap';
import { useAuth } from '../context/AuthContext.jsx';
import { useNavigate } from 'react-router-dom';

/**
 * NavigationBar Component
 * Renders the top navigation bar containing the app branding, 
 * user statistics (XP), and a profile dropdown menu.
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
        <Navbar bg="dark" variant="dark" expand="lg" className="shadow-sm mb-4 border-bottom border-secondary">
            <Container>
                {/* Brand / Logo Area */}
                <Navbar.Brand 
                    className="fw-bold" 
                    style={{ cursor: 'pointer', color: 'var(--primary-cyan)' }} 
                    onClick={() => navigate('/dashboard')}
                >
                    🚀 AdaptiveApp
                </Navbar.Brand>
                
                {/* Mobile Toggle Button */}
                <Navbar.Toggle aria-controls="basic-navbar-nav" />
                
                {/* Navigation Links and User Info */}
                <Navbar.Collapse id="basic-navbar-nav">
                    <Nav className="me-auto">
                        <Nav.Link onClick={() => navigate('/dashboard')}>Dashboard</Nav.Link>
                    </Nav>
                    
                    <Nav className="align-items-center">
                        {/* Gamified XP Badge */}
                        <Badge bg="warning" text="dark" className="me-3 rounded-pill px-3 py-2 shadow-sm">
                            ⭐ {user.xp || 0} XP
                        </Badge>
                        
                        {/* Profile Dropdown Menu */}
                        <NavDropdown 
                            title={<span className="text-light fw-bold">👤 {user.name}</span>} 
                            id="basic-nav-dropdown" 
                            align="end" 
                            menuVariant="dark" /* Keeps the dropdown menu dark! */
                        >
                            <NavDropdown.Item onClick={() => navigate('/profile')} className="text-light">
                                Profilom (Hamarosan)
                            </NavDropdown.Item>
                            <NavDropdown.Divider />
                            <NavDropdown.Item onClick={handleLogout} className="text-danger fw-bold">
                                Kijelentkezés
                            </NavDropdown.Item>
                        </NavDropdown>
                    </Nav>
                </Navbar.Collapse>
            </Container>
        </Navbar>
    );
};

export default NavigationBar;