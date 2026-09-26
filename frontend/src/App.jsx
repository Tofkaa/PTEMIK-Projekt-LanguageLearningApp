import { BrowserRouter as Router, Routes, Route, Navigate, useLocation } from 'react-router-dom';
import { Alert, Container, Button } from 'react-bootstrap';
import Login from './pages/Login.jsx';
import Register from './pages/Register.jsx';
import Dashboard from './pages/Dashboard.jsx';
import Lesson from './pages/LessonPlayer.jsx';
import PrivateRoute from './components/PrivateRoute.jsx';
import GuestRoute from './components/GuestRoute.jsx'; 
import NotFound from './pages/NotFound.jsx'; 
import Profile from './pages/Profile.jsx';
import Friends from './pages/Friends.jsx';
import ClassroomsPage from './pages/ClassroomsPage.jsx';
import NavigationBar from './components/NavigationBar.jsx';
import { AuthProvider, useAuth } from './context/AuthContext.jsx';
import { NotificationProvider } from './context/NotificationContext.jsx';
import ClassroomDetail from './pages/ClassroomDetail.jsx';
import AssignmentStart from './pages/AssignmentStart.jsx';
import AssignmentPlayer from './pages/AssignmentPlayer.jsx';
import AssignmentSubmissions from './pages/AssignmentSubmissions.jsx';
import AdminRoute from './components/AdminRoute.jsx';
import AdminDashboard from './pages/AdminDashboard.jsx';
import GlobalErrorToast from './components/GlobalErrorToast.jsx';
import VocabularyHub from './pages/VocabularyHub.jsx';
import VocabularyPractice from './pages/VocabularyPractice.jsx';
import FlashcardPractice from './pages/FlashcardPractice.jsx';
import VerifyEmail from './components/VerifyEmail.jsx';
import VerifiedRoute from './components/VerifiedRoute.jsx';
import ForgotPassword from './pages/ForgotPassword.jsx';
import ResetPassword from './pages/ResetPassword.jsx';
import Settings from './pages/Settings.jsx';

/**
 * VerificationBanner Component
 * Shows a warning if the authenticated user hasn't verified their email.
 */
const VerificationBanner = () => {
    const { user } = useAuth();
    const location = useLocation();

    if (!user) return null;
    
    if(user.verified === true) return null;

    const hiddenPaths = ['/assignment/session/', '/lesson/'];
    if (hiddenPaths.some(path => location.pathname.includes(path))) return null;

    return (
        <Container className="mb-3">
            <Alert variant="warning" className="d-flex justify-content-between align-items-center shadow-sm py-2 px-3 border-warning rounded-3">
                <div className="d-flex align-items-center gap-2">
                    <span className="fs-5">⚠️</span>
                    <div>
                        <strong className="d-block text-dark">Kérjük, erősítsd meg az e-mail címedet!</strong>
                        <span className="small text-dark opacity-75">Egyes funkciók (pl. feladatok beküldése) korlátozva lehetnek, amíg nem kattintasz az e-mailben kapott linkre.</span>
                    </div>
                </div>
            </Alert>
        </Container>
    );
};

/**
 * Main Application Component
 * Defines the routing logic and access control for the entire React application.
 */
function App() {
  return (
    <AuthProvider>
        <NotificationProvider>
            <Router>
                <NavigationBar />
                <VerificationBanner />
                <GlobalErrorToast />
                <Routes>
                    {/* Default route redirects to login */}
                    <Route path="/" element={<Navigate to="/login" replace />} />

                    {/* Guest Routes */}
                    <Route path="/login" element={<GuestRoute><Login /></GuestRoute>} />
                    <Route path="/register" element={<GuestRoute><Register /></GuestRoute>} />
                    <Route path="/forgot-password" element={<GuestRoute><ForgotPassword /></GuestRoute>} />
                    <Route path="/reset-password" element={<GuestRoute><ResetPassword /></GuestRoute>} />

                    {/* Private Routes */}
                    <Route path="/dashboard" element={<PrivateRoute><Dashboard /></PrivateRoute>} />
                    
                
                    <Route path="/settings" element={<PrivateRoute><Settings/></PrivateRoute>} />
                    
                    {/* Classrooms pages  */}
                    <Route path="/classrooms" element={<VerifiedRoute><ClassroomsPage /></VerifiedRoute>} />
                    <Route path="/classrooms/:id" element={<VerifiedRoute><ClassroomDetail /></VerifiedRoute>} />
                    
                    {/* Assignment Engine  */}
                    <Route path="/assignment/:id/start" element={<VerifiedRoute><AssignmentStart /></VerifiedRoute>} />
                    <Route path="/assignment/session/:sessionId/play" element={<VerifiedRoute><AssignmentPlayer /></VerifiedRoute>} />
                    <Route path="/assignment/:id/submissions" element={<VerifiedRoute><AssignmentSubmissions /></VerifiedRoute>} />

                    {/* Vocabulary pages */}
                    <Route path="/hub" element={<PrivateRoute><VocabularyHub /></PrivateRoute>} />
                    <Route path="/vocabulary/practice" element={<PrivateRoute><VocabularyPractice /></PrivateRoute>} />
                    <Route path="/vocabulary/flashcards" element={<PrivateRoute><FlashcardPractice /></PrivateRoute>} />
                    
                    {/* Other pages */}
                    <Route path="/lesson/:id" element={<PrivateRoute><Lesson /></PrivateRoute>} />
                    <Route path="/profile" element={<PrivateRoute><Profile /></PrivateRoute>} />
                    
                    {/* Commuunity */}
                    <Route path="/friends" element={<VerifiedRoute><Friends /></VerifiedRoute>} />
                    
                    <Route path="/verify" element={<VerifyEmail />} />
                    
                    {/* Admin pages */}
                    <Route path="/admin" element={<AdminRoute><AdminDashboard /></AdminRoute>} />

                    {/* 404 Route: Catch-all for undefined URLs */}
                    <Route path="*" element={<NotFound />} />
                </Routes>
            </Router>
        </NotificationProvider>
    </AuthProvider>
  );
}

export default App;