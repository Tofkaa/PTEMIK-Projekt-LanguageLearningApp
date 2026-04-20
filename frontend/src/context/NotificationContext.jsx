/**
 * @file NotificationContext.jsx
 * @description Provides global state management for application-wide notifications,
 * utilizing a hybrid approach of Server-Sent Events (SSE) and periodic polling.
 */

import React, { createContext, useState, useEffect, useContext, useCallback } from 'react';
import api from '../services/api';
import { useAuth } from './AuthContext';

const NotificationContext = createContext();

export const NotificationProvider = ({ children }) => {
    const { user } = useAuth();
    
    // Kezdeti állapot minden új és régi mezővel felkészítve
    const [notifications, setNotifications] = useState({ 
        total: 0, 
        pendingFriends: 0, 
        pendingChallenges: 0,
        totalFriends: 0,
        totalHistory: 0,
        teacherPendingJoinRequestIds: [],
        teacherUngradedSubmissionIds: [],
        studentActiveAssignmentIds: [],
        studentGradedSessionIds: [],
        lastPingTime: 0
    });

   /**
     * Manuális frissítést indító függvény
     */
    const fetchSummary = useCallback(async () => {
        if (!user) return;
        try {
            const response = await api.get('/notifications/summary', {
                params: { _t: new Date().getTime() }
            });
            const data = response.data;
            
        
            setNotifications({
                ...data, 
                // Visszafelé kompatibilitás a régi komponensek miatt:
                total: data.total || 0,
                pendingFriends: data.pendingFriendRequests || 0, 
                pendingChallenges: data.pendingChallenges || 0,
                totalFriends: data.totalAcceptedFriends || 0,
                totalHistory: data.totalHistoryItems || 0
            });
        } catch (error) {
            console.error("Értesítések lekérése sikertelen", error);
        }
    }, [user]);


   useEffect(() => {
        if (!user) return;

        let isMounted = true; 

        // SSE / Polling által használt betöltő
        const loadInitialData = async () => {
            const currentToken = localStorage.getItem('token') || sessionStorage.getItem('token');
            if (!currentToken || currentToken === 'null') return;

            try {
                const response = await api.get('/notifications/summary', {
                    params: { _t: new Date().getTime() }
                });
                
                if (isMounted) {
                    const data = response.data;
                    // Itt is alkalmazzuk a mindent áteresztő beállítást!
                    setNotifications({
                        ...data,
                        total: data.total || 0,
                        pendingFriends: data.pendingFriendRequests || 0,
                        pendingChallenges: data.pendingChallenges || 0,
                        totalFriends: data.totalAcceptedFriends || 0,
                        totalHistory: data.totalHistoryItems || 0
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
            // Takarításnál is nullázzuk az új mezőket
            setNotifications({ 
                total: 0, pendingFriends: 0, pendingChallenges: 0, totalFriends: 0, totalHistory: 0,
                teacherPendingJoinRequests: 0, teacherUngradedSubmissions: 0, studentActiveAssignmentIds: [], studentGradedSessionIds: [], lastPingTime: 0
            });
        };
    }, [user]);

    // Üres fallback state beállítása, ha kijelentkezik a user
    const activeNotifications = user ? notifications : { 
        total: 0, pendingFriends: 0, pendingChallenges: 0,
        teacherPendingJoinRequests: 0, teacherUngradedSubmissions: 0, studentActiveAssignmentIds: [], studentGradedSessionIds: [], lastPingTime: 0
    };

    return (
        <NotificationContext.Provider value={{ notifications: activeNotifications, refreshNotifications: fetchSummary }}>
            {children}
        </NotificationContext.Provider>
    );
};

// eslint-disable-next-line react-refresh/only-export-components
export const useNotifications = () => useContext(NotificationContext);