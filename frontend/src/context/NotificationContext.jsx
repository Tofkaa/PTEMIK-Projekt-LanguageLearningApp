/**
 * @file NotificationContext.jsx
 * @description Provides global state management for application-wide notifications,
 * utilizing a hybrid approach of Server-Sent Events (SSE) and periodic polling.
 */

import React, { createContext, useState, useEffect, useContext, useCallback } from 'react';
import api from '../services/api';
import { useAuth } from './AuthContext';

/**
 * @typedef {Object} NotificationState
 * @property {number} total - Sum of all actionable pending notifications.
 * @property {number} pendingFriends - Number of inbound friend requests.
 * @property {number} pendingChallenges - Number of active challenges awaiting the user's turn.
 * @property {number} totalFriends - Total count of accepted friends (used for client-side diffing).
 * @property {number} totalHistory - Total count of closed challenges (used for client-side diffing).
 */
const NotificationContext = createContext();

/**
 * NotificationProvider Component
 * Wraps the application to provide real-time notification data to all nested components.
 * * @param {Object} props - React component props.
 * @param {React.ReactNode} props.children - Child elements.
 */
export const NotificationProvider = ({ children }) => {
    const { user } = useAuth();
    const [notifications, setNotifications] = useState({ total: 0, pendingFriends: 0, pendingChallenges: 0 });

   /**
     * Manually triggers a synchronization with the backend.
     * Utilizes cache-busting via a timestamp parameter.
     * * @async
     * @function
     */
    const fetchSummary = useCallback(async () => {
        if (!user) return;
        try {
            
            const response = await api.get('/notifications/summary', {
                params: { _t: new Date().getTime() }
            });
            setNotifications({
                total: response.data.total,
                pendingFriends: response.data.pendingFriendRequests, 
                pendingChallenges: response.data.pendingChallenges,
                totalFriends: response.data.totalAcceptedFriends,
                totalHistory: response.data.totalHistoryItems
            });
        } catch (error) {
            console.error("Értesítések lekérése sikertelen", error);
        }
    }, [user]);


   useEffect(() => {
        if (!user) return;

        let isMounted = true; 

        const loadInitialData = async () => {
        const currentToken = localStorage.getItem('token') || sessionStorage.getItem('token');
        if (!currentToken || currentToken === 'null') return;

        try {
            const response = await api.get('/notifications/summary', {
                params: { _t: new Date().getTime() }
            });
            
            if (isMounted) {
                setNotifications({
                    total: response.data.total,
                    pendingFriends: response.data.pendingFriendRequests,
                    pendingChallenges: response.data.pendingChallenges,
                    totalFriends: response.data.totalAcceptedFriends,
                    totalHistory: response.data.totalHistoryItems
                });
            }
        } catch (error) {
             if (error.response?.status !== 403) {
                console.error("Értesítések lekérése sikertelen", error);
                }
            }
        };


        loadInitialData();

        const token = localStorage.getItem('token') || sessionStorage.getItem('token');
        
        let eventSource = null;

        if (token && token !== 'null') {
            eventSource = new EventSource(`http://localhost:8080/api/notifications/stream?token=${token}`);

            eventSource.addEventListener("ping", () => {
                console.log("Ping érkezett! Adatok frissítése...");
                loadInitialData(); 
            });

            eventSource.onerror = () => {
                eventSource.close();
            };
        } else {
            console.log("SSE csatlakozás várakozásra áll (token frissítés vagy hiány).");
        }

        const intervalId = setInterval(() => {
            loadInitialData();
        }, 60000); 

        const handleFocus = () => {
            loadInitialData();
        };
        window.addEventListener('focus', handleFocus);

        return () => {
            isMounted = false;
            if (eventSource) eventSource.close(); 
            clearInterval(intervalId); 
            window.removeEventListener('focus', handleFocus); 
            setNotifications({ total: 0, pendingFriends: 0, pendingChallenges: 0, totalFriends: 0, totalHistory: 0 });
        };
    }, [user]);

    const activeNotifications = user ? notifications : { total: 0, pendingFriends: 0, pendingChallenges: 0 };

    return (
        <NotificationContext.Provider value={{ notifications: activeNotifications, refreshNotifications: fetchSummary }}>
            {children}
        </NotificationContext.Provider>
    );
};

// eslint-disable-next-line react-refresh/only-export-components
export const useNotifications = () => useContext(NotificationContext);