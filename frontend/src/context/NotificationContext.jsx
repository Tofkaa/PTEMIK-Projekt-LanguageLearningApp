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
    const { user, syncXp } = useAuth();
    const userId = user?.userId;

    const [notificationState, setNotificationState] = useState({ userId: null, data: EMPTY_NOTIFICATIONS });
    const [autoRefreshEnabled, setAutoRefreshEnabled] = useState(() => {
        try { return localStorage.getItem('notificationAutoRefresh') !== 'paused'; }
        catch { return true; }
    });
    const requestCounterRef = useRef(0);
    const invalidateRequests = useCallback(() => { requestCounterRef.current += 1; }, []);

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
            syncXp?.(userId, data.currentXp);
            pruneViewedStore(userId, data);
            setNotificationState({ userId, data: mapSummaryData(data) });
            window.dispatchEvent(new Event('achievement-refresh'));
            return true;
        } catch (error) {
            if (error.response?.status !== 403) {
                console.error("Értesítések lekérése sikertelen", error);
            }
            return false;
        }
    }, [userId, syncXp]);

    useEffect(() => {
        if (!userId || !autoRefreshEnabled) return;

        let isMounted = true;

        const loadInitialData = async () => {
            if (!isMounted) return;
            const currentToken = localStorage.getItem('token') || sessionStorage.getItem('token');
            if (!currentToken || currentToken === 'null') return;

            const requestId = ++requestCounterRef.current;

            try {
                const response = await api.get('/notifications/summary', {
                    params: { _t: Date.now() }
                });

                if (isMounted && requestId === requestCounterRef.current) {
                    const data = response.data;
                    syncXp?.(userId, data.currentXp);
                    pruneViewedStore(userId, data);
                    setNotificationState({ userId, data: mapSummaryData(data) });
                    window.dispatchEvent(new Event('achievement-refresh'));
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
            eventSource = new EventSource(`${(api.defaults?.baseURL || '/api').replace(/\/$/, '')}/notifications/stream?token=${encodeURIComponent(token)}`);

            eventSource.addEventListener("open", loadInitialData);
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
            invalidateRequests();
            if (eventSource) eventSource.close();
            clearInterval(intervalId);
            window.removeEventListener('focus', handleFocus);
        };
    }, [userId, autoRefreshEnabled, invalidateRequests, syncXp]);

    const setAutomaticRefresh = enabled => {
        setAutoRefreshEnabled(enabled);
        try { localStorage.setItem('notificationAutoRefresh', enabled ? 'enabled' : 'paused'); }
        catch { /* The setting still applies to this page when browser storage is unavailable. */ }
    };
    const activeNotifications = user && notificationState.userId === userId ? notificationState.data : EMPTY_NOTIFICATIONS;

    return (
        <NotificationContext.Provider value={{ notifications: activeNotifications, refreshNotifications: fetchSummary,
            autoRefreshEnabled, setAutomaticRefresh }}>
            {children}
        </NotificationContext.Provider>
    );
};

// eslint-disable-next-line react-refresh/only-export-components
export const useNotifications = () => useContext(NotificationContext);
