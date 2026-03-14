import React, { createContext, useState, useEffect, useContext } from 'react';
import api from '../services/api';

/**
 * Global Authentication Context
 * Provides a centralized state for user data and authentication methods.
 */
const AuthContext = createContext();

/**
 * AuthProvider Component
 * Wraps the application to provide authentication state and functions to all child components.
 * * @param {Object} props - Component properties
 * @param {React.ReactNode} props.children - Child components to be wrapped by the provider
 */
export const AuthProvider = ({ children }) => {
    // Global state for the authenticated user object
    const [user, setUser] = useState(null);
    // Global loading state used during initial token verification
    const [loading, setLoading] = useState(true);

    /**
     * Runs once when the application mounts (e.g., on hard refresh).
     * Checks if a token exists in LocalStorage and attempts to fetch the latest user profile.
     */
    useEffect(() => {
        const checkLoggedInUser = async () => {
            const token = localStorage.getItem('token');
            
            if (token) {
                try {
                    // If token exists, fetch the latest user profile from the backend
                    const response = await api.get('/users/me');
                    setUser(response.data);
                } catch (error) {
                    console.error("Error fetching user profile (expired token?):", error);
                    // Clear invalid/expired token and reset user state
                    localStorage.removeItem('token');
                    setUser(null);
                }
            }
            
            // Verification complete, remove loading screen
            setLoading(false);
        };

        checkLoggedInUser();
    }, []);

    /**
     * Logs the user in by storing the token and updating the user state.
     * Called after a successful login API request.
     * * @param {string} token - The JWT access token
     * @param {Object} userData - The authenticated user's profile data
     */
    const login = (token, userData) => {
        localStorage.setItem('token', token);
        setUser(userData);
    };

    /**
     * Terminates the user session.
     * Calls the backend to invalidate the HttpOnly refresh token cookie,
     * then clears the local access token and user state.
     */
    const logout = async () => {
        try {
            // 1. Request the backend to invalidate the HttpOnly Refresh Token cookie
            await api.post('/auth/logout');
            console.log("Successful server-side logout.");
        } catch (error) {
            console.error("Error during server-side logout:", error);
        } finally {
            // 2. Regardless of server response, immediately discard the client-side Access Token
            localStorage.removeItem('token');
            
            // 3. Reset the React state, which triggers a redirect to the Login page via PrivateRoute
            setUser(null);
        }
    };

    // Display a loading spinner while verifying the token on initial mount
    if (loading) {
        return (
            <div className="d-flex justify-content-center align-items-center vh-100">
                <div className="spinner-border text-primary" role="status">
                    <span className="visually-hidden">Loading...</span>
                </div>
            </div>
        );
    }

    // Provide the authentication state and methods to the rest of the application
    return (
        <AuthContext.Provider value={{ user, login, logout }}>
            {children}
        </AuthContext.Provider>
    );
};

/**
 * Custom hook for easy consumption of the AuthContext.
 * * @returns {Object} The authentication context (user, login, logout)
 */
export const useAuth = () => {
    return useContext(AuthContext);
};