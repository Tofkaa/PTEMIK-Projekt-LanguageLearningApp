import { safeError } from '../utils/safeError';
import { Navbar, Container, Nav, Badge, NavDropdown } from 'react-bootstrap';
import { useAuth } from '../context/AuthContext.jsx';
import { Link, NavLink, useNavigate } from 'react-router-dom';
import { useNotifications } from '../context/NotificationContext.jsx';
import { useEffect, useState } from 'react'; 
import ThemeToggle from './ThemeToggle.jsx'; 
import { getUnseenIds, getScopedCounter, setScopedCounter } from '../utils/viewedStore.js';

const AVAILABLE_COURSES = {
    'en': { name: 'Angol', flag: '🇬🇧' },
    'es': { name: 'Spanyol', flag: '🇪🇸' },
    'de': { name: 'Német', flag: '🇩🇪' }
};


const NavigationBar = () => {
    const { user, logout , changeLanguage} = useAuth();
    const navigate = useNavigate();
    const { notifications } = useNotifications();
    
    const [, setLocalUpdate] = useState(0);

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
        logout();
        navigate('/login');
    };

    if (!user) return null;

    const unseenAssignments = getUnseenIds('viewedAssignments', user.userId, notifications?.studentActiveAssignmentIds).length;
    const unseenResults = getUnseenIds('viewedResults', user.userId, notifications?.studentGradedSessionIds).length;
    const unseenTeacherPending = getUnseenIds('viewedTeacherPending', user.userId, notifications?.teacherPendingJoinRequestIds).length;
    const unseenTeacherUngraded = getUnseenIds('viewedTeacherUngraded', user.userId, notifications?.teacherUngradedSubmissionIds).length;

    const classroomPings = unseenAssignments + unseenResults + unseenTeacherPending + unseenTeacherUngraded;
    const communityPings = (notifications?.pendingFriends || 0) + (notifications?.pendingChallenges || 0);

    const activeCourseCode = user.activeCourseCode || 'en';
    const activeCourseData = AVAILABLE_COURSES[activeCourseCode] || AVAILABLE_COURSES['en'];
    const rawDueVocabCount = notifications?.dueVocabularyCount || 0;
    const seenDueVocabCount = getScopedCounter('seenDueVocabCount', user.userId);
    if (rawDueVocabCount < seenDueVocabCount) {
        setScopedCounter('seenDueVocabCount', user.userId, rawDueVocabCount);
    }

    const dueVocabCount = rawDueVocabCount > seenDueVocabCount ? rawDueVocabCount : 0;
    const handleLanguageChange = async (courseCode) => {
        try {
            await changeLanguage(courseCode);
            
        } catch (error) {
            console.error("Sikertelen nyelvváltás", safeError(error));
        }
    };
                           
    return (
        <Navbar 
            aria-label="Fő navigáció"
            bg="dark" 
            variant="dark" 
            expand="lg" 
            className="shadow-sm mb-4 border-bottom border-secondary position-relative"
            style={{ zIndex: 1050 }}
        >
            <Container>
                {/* Brand Logo */}
                <Navbar.Brand as={Link} to="/dashboard" className="fw-bold d-flex align-items-center gap-2" style={{ color: 'var(--app-accent-text)' }}>
                    <img src="/languageapp.svg" width="32" height="32" alt="" /> LanguageApp
                </Navbar.Brand>
                
                <Navbar.Toggle aria-controls="basic-navbar-nav" label="Navigációs menü" />
                <Navbar.Collapse id="basic-navbar-nav">
                    
                    {/* Main Navigation Links  */}
                    <Nav className="me-auto align-items-lg-center gap-3 mt-3 mt-lg-0">
                        
                        <Nav.Link as={NavLink} to="/dashboard" className="fw-bold d-flex align-items-center gap-2">
                            <span aria-hidden="true">📊</span> Dashboard
                        </Nav.Link>

                       <Nav.Link as={NavLink} to="/classrooms" className="tour-navbar-classrooms fw-bold position-relative d-flex align-items-center gap-2">
                            <span aria-hidden="true">🏫</span> Osztálytermek
                            {classroomPings > 0 && (
                                <Badge bg="danger" pill className="nav-notification-badge shadow-sm" style={{ fontSize: '0.65rem' }}>
                                    {classroomPings}
                                </Badge>
                            )}
                        </Nav.Link>

                       <Nav.Link as={NavLink} to="/friends" className="tour-navbar-community fw-bold position-relative d-flex align-items-center gap-2">
                            <span aria-hidden="true">🌐</span> Közösség
                            {communityPings > 0 && (
                                <Badge bg="danger" pill className="nav-notification-badge shadow-sm" style={{ fontSize: '0.65rem' }}>
                                    {communityPings}
                                </Badge>
                            )}
                        </Nav.Link>

                       {/* Vocabulary Hub */}
                        <Nav.Link as={NavLink} to="/hub" className="tour-dictionary-hub fw-bold position-relative d-flex align-items-center gap-2">
                            <span aria-hidden="true">🎯</span> Szótár HUB
                            {dueVocabCount > 0 && (
                                <Badge bg="danger" pill className="nav-notification-badge shadow-sm" style={{ fontSize: '0.65rem' }}>
                                    {dueVocabCount}
                                </Badge>
                            )}
                        </Nav.Link>

                        {/* Admin Link */}
                        {(user.role === 'ADMIN' || user.role === 'ROLE_ADMIN') && (
                            <Nav.Link as={NavLink} to="/admin" className="text-danger fw-bold d-flex align-items-center gap-2 ms-lg-3 border-start border-secondary ps-lg-4">
                                <span aria-hidden="true">🛡️</span> Admin Panel
                            </Nav.Link>
                        )}

                    </Nav>
                    
                    {/* Right side section (Theme Toggle, XP and Profile) */}
                    <Nav className="align-items-lg-center gap-3 mt-3 mt-lg-0">
                        <ThemeToggle />
                        <Badge bg="warning" text="dark" className="rounded-pill px-3 py-2 shadow-sm fs-6 d-flex align-items-center gap-1">
                            <span aria-hidden="true">⭐</span> {user.xp || 0} XP
                        </Badge>

                         <NavDropdown 
                            className="tour-language-selector mx-2"
                            title={
                                <span className="fs-5 d-inline-flex align-items-center" style={{cursor: 'pointer'}}>
                                    <span aria-hidden="true">{activeCourseData.flag}</span>
                                    <span className="visually-hidden">Tanult nyelv: {activeCourseData.name}</span>
                                </span>
                            } 
                            id="language-nav-dropdown" 
                            align="end" 
                            menuVariant="dark"
                        >
                            <NavDropdown.Header className="text-light opacity-75">Nyelv kiválasztása</NavDropdown.Header>
                            {Object.entries(AVAILABLE_COURSES).map(([code, data]) => (
                                <NavDropdown.Item 
                                    as="button"
                                    key={code} 
                                    aria-pressed={activeCourseCode === code}
                                    onClick={() => handleLanguageChange(code)}
                                    
                                    className={`d-flex align-items-center gap-2 ${activeCourseCode === code ? 'bg-primary text-white fw-bold' : 'text-light'}`}
                                >
                                    <span aria-hidden="true">{data.flag}</span> {data.name}
                                    {activeCourseCode === code && <span aria-hidden="true" className="ms-auto small">✓</span>}
                                </NavDropdown.Item>
                            ))}
                        </NavDropdown>

                        <NavDropdown 
                            className="tour-profile-menu"
                            title={
                                <span className="text-light fw-bold d-inline-flex align-items-center gap-2">
                                    {user.profilePictureUrl ? (
                                        <img 
                                            src={user.profilePictureUrl} 
                                            alt=""
                                            className="rounded-circle object-fit-cover border border-secondary"
                                            style={{ width: '30px', height: '30px' }}
                                        />
                                    ) : (
                                        <span aria-hidden="true">👤</span>
                                    )}
                                    {user.name}
                                </span>
                            } 
                            id="basic-nav-dropdown" 
                            align="end" 
                            menuVariant="dark"
                        >
                            <NavDropdown.Item as={Link} to="/profile" className="text-light">Profilom</NavDropdown.Item>
                            <NavDropdown.Divider />
                            <NavDropdown.Item as="button" onClick={handleLogout} className="text-danger fw-bold">Kijelentkezés</NavDropdown.Item>
                        </NavDropdown>
                    </Nav>

                </Navbar.Collapse>
            </Container>
        </Navbar>
    );
};

export default NavigationBar;
