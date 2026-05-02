import { BrowserRouter as Router, Routes, Route, Navigate } from 'react-router-dom';
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
import { AuthProvider } from './context/AuthContext.jsx';
import { NotificationProvider } from './context/NotificationContext.jsx';
import ClassroomDetail from './pages/ClassroomDetail.jsx';
import AssignmentStart from './pages/AssignmentStart.jsx';
import AssignmentPlayer from './pages/AssignmentPlayer.jsx';
import AssignmentSubmissions from './pages/AssignmentSubmissions.jsx';
import AdminRoute from './components/AdminRoute.jsx';
import AdminDashboard from './pages/AdminDashboard.jsx';

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
                <Routes>
                    {/* Default route redirects to login */}
                    <Route path="/" element={<Navigate to="/login" replace />} />

                    {/* Guest Routes */}
                    <Route path="/login" element={<GuestRoute><Login /></GuestRoute>} />
                    <Route path="/register" element={<GuestRoute><Register /></GuestRoute>} />

                    {/* Private Routes */}
                    <Route path="/dashboard" element={<PrivateRoute><Dashboard /></PrivateRoute>} />
                    
                    {/* Classrooms pages */}
                    <Route path="/classrooms" element={<PrivateRoute><ClassroomsPage /></PrivateRoute>} />
                    <Route path="/classrooms/:id" element={<PrivateRoute><ClassroomDetail /></PrivateRoute>} />
                    
                    {/* Assignment Engine */}
                    <Route path="/assignment/:id/start" element={<PrivateRoute><AssignmentStart /></PrivateRoute>} />
                    <Route path="/assignment/session/:sessionId/play" element={<PrivateRoute><AssignmentPlayer /></PrivateRoute>} />
                    <Route path="/assignment/:id/submissions" element={<AssignmentSubmissions />} />

                    {/* Other pages */}
                    <Route path="/lesson/:id" element={<PrivateRoute><Lesson /></PrivateRoute>} />
                    <Route path="/profile" element={<PrivateRoute><Profile /></PrivateRoute>} />
                    <Route path="/friends" element={<PrivateRoute><Friends /></PrivateRoute>} />

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