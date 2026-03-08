import { Navigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext.jsx';

const PrivateRoute = ({ children }) => {
    // Get user from global state
    const { user } = useAuth();

    // If not logged in return to login page
    if (!user) {
        return <Navigate to="/login" replace />;
    }

    // Otherwise allow to load the desired component
    return children;
};

export default PrivateRoute;