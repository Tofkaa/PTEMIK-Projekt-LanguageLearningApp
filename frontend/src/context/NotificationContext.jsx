/**
 * @file NotificationContext.jsx
 * @description Provides global state management for application-wide notifications,
 * utilizing a hybrid approach of Server-Sent Events (SSE) and periodic polling.
 */

import React, { createContext, useState, useEffect, useContext, useCallback, useRef } from 'react';
import api from '../services/api';
import { useAuth } from './AuthContext';
import { pruneViewedStore } from '../utils/viewedStore';

const NotificationContext = createContext();

const EMPTY_NOTIFICATIONS = {
    total: 0,
    pendingFriends: 0,
    pendingFriendRequests: 0,
    pendingChallenges: 0,
    totalFriends: 0,
    totalAcceptedFriends: 0,
    totalHistory: 0,
    totalHistoryItems: 0,
    teacherPendingJoinRequestIds: [],
    teacherUngradedSubmissionIds: [],
    studentActiveAssignmentIds: [],
    studentGradedSessionIds: [],
    lastPingTime: 0,
    dueVocabularyCount: 0
};


const mapSummaryData = (data) => ({
    ...EMPTY_NOTIFICATIONS,
    ...data,
    total: data.total || 0,
    pendingFriends: data.pendingFriendRequests || 0,
    pendingFriendRequests: data.pendingFriendRequests || 0,
    pendingChallenges: data.pendingChallenges || 0,
    totalFriends: data.totalAcceptedFriends || 0,
    totalAcceptedFriends: data.totalAcceptedFriends || 0,
    totalHistory: data.totalHistoryItems || 0,
    totalHistoryItems: data.totalHistoryItems || 0,
    teacherPendingJoinRequestIds: data.teacherPendingJoinRequestIds || [],
    teacherUngradedSubmissionIds: data.teacherUngradedSubmissionIds || [],
    studentActiveAssignmentIds: data.studentActiveAssignmentIds || [],
    studentGradedSessionIds: data.studentGradedSessionIds || [],
    dueVocabularyCount: data.dueVocabularyCount || 0
});

export const NotificationProvider = ({ children }) => {
    const { user } = useAuth();
    const userId = user?.userId;

    const [notifications, setNotifications] = useState(EMPTY_NOTIFICATIONS);
    const requestCounterRef = useRef(0);

    /**
     * Manuális és automatikus frissítést indító függvény
     */
    const fetchSummary = useCallback(async () => {
        if (!userId) return;
        const currentToken = localStorage.getItem('token') || sessionStorage.getItem('token');
        if (!currentToken || currentToken === 'null') return;

        const requestId = ++requestCounterRef.current;

        try {
            const response = await api.get('/notifications/summary', {
                params: { _t: Date.now() }
            });

            if (requestId !== requestCounterRef.current) return;

            const data = response.data;
            pruneViewedStore(userId, data);
            setNotifications(mapSummaryData(data));
        } catch (error) {
            if (error.response?.status !== 403) {
                console.error("Értesítések lekérése sikertelen", error);
            }
        }
    }, [userId]);

    useEffect(() => {
        if (!userId) return;

        let isMounted = true;

        const loadInitialData = async () => {
            const currentToken = localStorage.getItem('token') || sessionStorage.getItem('token');
            if (!currentToken || currentToken === 'null') return;

            const requestId = ++requestCounterRef.current;

            try {
                const response = await api.get('/notifications/summary', {
                    params: { _t: Date.now() }
                });

                if (isMounted && requestId === requestCounterRef.current) {
                    const data = response.data;
                    pruneViewedStore(userId, data);
                    setNotifications(mapSummaryData(data));
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
                loadInitialData();
            });

            eventSource.onerror = () => {
                console.warn("SSE kapcsolat megszakadt, újracsatlakozás várható...");
            };
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
            requestCounterRef.current++;
            if (eventSource) eventSource.close();
            clearInterval(intervalId);
            window.removeEventListener('focus', handleFocus);
            setNotifications(EMPTY_NOTIFICATIONS);
        };
    }, [userId]);

    const activeNotifications = user ? notifications : EMPTY_NOTIFICATIONS;

    return (
        <NotificationContext.Provider value={{ notifications: activeNotifications, refreshNotifications: fetchSummary }}>
            {children}
        </NotificationContext.Provider>
    );
};

// eslint-disable-next-line react-refresh/only-export-components
export const useNotifications = () => useContext(NotificationContext);