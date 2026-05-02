import { useAuth } from '../context/AuthContext';
import Unauthorized from '../pages/Unauthorized';

const AdminRoute = ({ children }) => {
    const { user } = useAuth();

    // Ha nincs bejelentkezve, vagy a role nem ADMIN, megmutatjuk a 403-as oldalt
    if (!user || (user.role !== 'ADMIN' && user.role !== 'ROLE_ADMIN')) {
        return <Unauthorized />;
    }

    return children;
};

export default AdminRoute;