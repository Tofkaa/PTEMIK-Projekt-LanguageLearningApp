import { safeError } from '../utils/safeError';
/**
 * @file NotificationContext.jsx
 * @description Provides global state management for application-wide notifications,
 * utilizing a hybrid approach of Server-Sent Events (SSE) and periodic polling.
 */

import React, { createContext, useState, useEffect, useLayoutEffect, useContext, useCallback, useRef } from 'react';
import api from '../services/api';
import { openNotificationStream } from '../services/notificationStream';
import { getAccessToken, getAuthEpoch } from '../services/authSession';
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
    const { user, syncXp, syncCourseSelection } = useAuth();
    const userId = user?.userId;

    const courseId = user?.activeCourseId;
    const authEpoch = getAuthEpoch();
    const scope = JSON.stringify([authEpoch, userId, courseId, user?.activeCourseRevision]);
    const currentScope = useRef(scope);
    useLayoutEffect(() => { currentScope.current = scope; }, [scope]);
    const [notificationState, setNotificationState] = useState({ scope: null, data: EMPTY_NOTIFICATIONS });
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
        if (!userId || currentScope.current !== scope || getAuthEpoch() !== authEpoch) return;
        const currentToken = getAccessToken();
        if (!currentToken || currentToken === 'null') return;

        const requestId = ++requestCounterRef.current;

        try {
            const response = await api.get('/notifications/summary', {
                params: { _t: Date.now(), courseId }
            });

            if (requestId !== requestCounterRef.current || currentScope.current !== scope || getAuthEpoch() !== authEpoch) return;

            const data = response.data;
            if (syncCourseSelection?.(userId, data)) return false;
            if (data.vocabularyCourseId && data.vocabularyCourseId !== courseId) return false;
            syncXp?.(userId, data.currentXp);
            pruneViewedStore(userId, data);
            setNotificationState({ scope, data: mapSummaryData(data) });
            window.dispatchEvent(new Event('achievement-refresh'));
            return true;
        } catch (error) {
            if (error.response?.status !== 403) {
                console.error("Értesítések lekérése sikertelen", safeError(error));
            }
            return false;
        }
    }, [userId, courseId, scope, authEpoch, syncXp, syncCourseSelection]);

    useEffect(() => {
        if (!userId || !autoRefreshEnabled) return;

        let isMounted = true;

        const loadInitialData = () => { if (isMounted) void fetchSummary(); };

        loadInitialData();

        const closeStream = openNotificationStream({ onConnected: loadInitialData, onPing: loadInitialData });

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
            closeStream();
            clearInterval(intervalId);
            window.removeEventListener('focus', handleFocus);
        };
    }, [userId, autoRefreshEnabled, invalidateRequests, fetchSummary]);

    const setAutomaticRefresh = enabled => {
        setAutoRefreshEnabled(enabled);
        try { localStorage.setItem('notificationAutoRefresh', enabled ? 'enabled' : 'paused'); }
        catch { /* The setting still applies to this page when browser storage is unavailable. */ }
    };
    const activeNotifications = user && notificationState.scope === scope ? notificationState.data : EMPTY_NOTIFICATIONS;

    return (
        <NotificationContext.Provider value={{ notifications: activeNotifications, refreshNotifications: fetchSummary,
            autoRefreshEnabled, setAutomaticRefresh }}>
            {children}
        </NotificationContext.Provider>
    );
};

// eslint-disable-next-line react-refresh/only-export-components
export const useNotifications = () => useContext(NotificationContext);
