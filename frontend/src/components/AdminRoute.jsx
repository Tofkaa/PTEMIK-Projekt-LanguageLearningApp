import React from 'react';
import { Navigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

const AdminRoute = ({ children }) => {
    const { user } = useAuth();

    // Ha nincs bejelentkezve, vagy a role nem ADMIN, kidobjuk a dashboardra
    if (!user || (user.role !== 'ADMIN' && user.role !== 'ROLE_ADMIN')) {
        return <Navigate to="/dashboard" replace />;
    }

    return children;
};

export default AdminRoute;