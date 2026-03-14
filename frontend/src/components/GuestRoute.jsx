import { Navigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext.jsx';

/**
 * GuestRoute Component
 * Protects authentication routes (Login, Register) from already logged-in users.
 * If an authenticated user tries to access these pages, they are redirected to the Dashboard.
 *
 * @param {Object} props - Component properties
 * @param {React.ReactNode} props.children - The component to render if the user is a guest
 */
const GuestRoute = ({ children }) => {
    const { user } = useAuth();
    const token = localStorage.getItem('token');

    if (user || token) {
        return <Navigate to="/dashboard" replace />;
    }

    return children;
};

export default GuestRoute;