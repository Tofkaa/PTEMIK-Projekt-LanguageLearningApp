import { BrowserRouter as Router, Routes, Route, Navigate } from 'react-router-dom';
import Login from './pages/Login.jsx';
import Register from './pages/Register.jsx';
import Dashboard from './pages/Dashboard.jsx';
import Lesson from './pages/LessonPlayer.jsx';
import PrivateRoute from './components/PrivateRoute.jsx';
import GuestRoute from './components/GuestRoute.jsx'; 
import NotFound from './pages/NotFound.jsx'; 
import Profile from './pages/Profile.jsx'
import Friends from './pages/Friends.jsx';
import NavigationBar from './components/NavigationBar.jsx';
import { AuthProvider } from './context/AuthContext.jsx';
import { NotificationProvider } from './context/NotificationContext.jsx';

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

                {/* Guest Routes: Protected from already authenticated users */}
                <Route 
                    path="/login" 
                    element={
                        <GuestRoute>
                            <Login />
                        </GuestRoute>
                    } 
                />
                <Route 
                    path="/register" 
                    element={
                        <GuestRoute>
                            <Register />
                        </GuestRoute>
                    } 
                />

                {/* Private Routes: Protected from unauthenticated guests */}
                <Route 
                    path="/dashboard" 
                    element={
                        <PrivateRoute>
                            <Dashboard />
                        </PrivateRoute>
                    } 
                />
                <Route
                    path="/lesson/:id" 
                    element={
                        <PrivateRoute>
                            <Lesson />
                        </PrivateRoute>
                    } 
                />
                <Route 
                    path="/profile" 
                    element={
                        <PrivateRoute>
                                <Profile />
                        </PrivateRoute>
                    } 
                
                />
                <Route 
                    path="/friends" 
                    element={
                        <Friends />
                    } 
                />

                {/* 404 Route: Catch-all for undefined URLs */}
                <Route path="*" element={<NotFound />} />
            </Routes>
            </Router>
        </NotificationProvider>
    </AuthProvider>
  );
}

export default App;