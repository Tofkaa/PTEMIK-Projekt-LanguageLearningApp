import { safeError } from '../utils/safeError';
import { clearInvitation } from '../utils/invitationIntent';
import React, { createContext, useState, useEffect, useContext, useCallback, useRef } from 'react';
import axios from 'axios';
import api from '../services/api';
import { vocabularyApi } from '../services/vocabularyApi';
import { userApi } from '../services/userApi';
import { authSnapshot, beginAuthentication, endSession, getAccessToken, getAuthEpoch, isCurrentAuth,
    queueAuthCookieOperation, requireCurrentAuth, storeAccessToken, subscribeAuth } from '../services/authSession';

const AuthContext = createContext();
const COURSE_NOTICE = 'languageapp-course-change';
const revision = value => Number.isSafeInteger(value?.activeCourseRevision) && value.activeCourseRevision >= 0 ? value.activeCourseRevision : 0;
const courseKey = value => JSON.stringify([value?.userId, value?.activeCourseId, value?.activeCourseCode, revision(value)]);
const mergeProfile = (current, data) => {
    if (!current) return { ...data, activeCourseRevision: revision(data) };
    const next = { ...current, ...data };
    if (!Number.isSafeInteger(data.activeCourseRevision) || data.activeCourseRevision < revision(current)) {
        next.activeCourseId = current.activeCourseId;
        next.activeCourseCode = current.activeCourseCode;
        next.activeCourseRevision = revision(current);
    }
    return next;
};
const broadcastCourse = user => {
    try { localStorage.setItem(COURSE_NOTICE, JSON.stringify({ userId: user.userId, revision: revision(user), nonce: crypto.randomUUID() })); }
    catch { /* Focus and notification reconciliation still work without browser storage. */ }
};

export const AuthProvider = ({ children }) => {
    const [user, setUser] = useState(null);
    const userRef = useRef(null);
    const [vocabularyMap, setVocabularyMap] = useState({});
    const [loading, setLoading] = useState(true);
    const [startupError, setStartupError] = useState(false);
    const vocabularyRequest = useRef(0);
    const courseRequest = useRef(0);
    const courseQueue = useRef(Promise.resolve());
    const profileFlight = useRef(null);

    const publishProfile = useCallback((data, snapshot) => {
        if (!data || !isCurrentAuth(snapshot)) return null;
        const current = userRef.current;
        if (current?.userId && data.userId && current.userId !== data.userId) return null;
        const next = mergeProfile(current, data);
        if (courseKey(current) !== courseKey(next)) { ++vocabularyRequest.current; setVocabularyMap({}); }
        userRef.current = next;
        setUser(next);
        return next;
    }, []);
    const fetchVocabularyMap = useCallback(async (snapshot, selection = userRef.current) => {
        const request = ++vocabularyRequest.current;
        const scope = courseKey(selection);
        try {
            const response = await vocabularyApi.getVocabularyMap(selection?.activeCourseId);
            if (isCurrentAuth(snapshot) && request === vocabularyRequest.current && courseKey(userRef.current) === scope)
                setVocabularyMap(response.data);
        } catch (error) {
            if (isCurrentAuth(snapshot) && request === vocabularyRequest.current && !axios.isCancel(error))
                console.error('Hiba a szótérkép betöltésekor:', safeError(error));
        }
    }, []);
    const refreshProfile = useCallback(() => {
        const snapshot = authSnapshot();
        if (!snapshot.token || !userRef.current?.userId) return Promise.resolve(false);
        if (profileFlight.current?.epoch === snapshot.epoch) return profileFlight.current.promise;
        const promise = (async () => {
            try {
                const response = await api.get('/users/me', { skipGlobalError: true });
                if (!isCurrentAuth(snapshot)) return false;
                const next = publishProfile(response?.data, snapshot);
                if (!next) return false;
                await fetchVocabularyMap(snapshot, next);
                return true;
            } catch (error) {
                if (isCurrentAuth(snapshot) && [401, 403].includes(error.response?.status)) endSession(snapshot.epoch);
                return false;
            }
        })();
        profileFlight.current = { epoch: snapshot.epoch, promise };
        void promise.finally(() => { if (profileFlight.current?.promise === promise) profileFlight.current = null; });
        return promise;
    }, [publishProfile, fetchVocabularyMap]);
    const checkLoggedInUser = useCallback(async () => {
        const snapshot = authSnapshot();
        setStartupError(false);
        if (!snapshot.token) { setLoading(false); return; }
        setLoading(true);
        try {
            const response = await api.get('/users/me');
            requireCurrentAuth(snapshot);
            const next = publishProfile(response.data, snapshot);
            await fetchVocabularyMap(snapshot, next);
        } catch (error) {
            if (!isCurrentAuth(snapshot) || axios.isCancel(error)) return;
            if ([401, 403].includes(error.response?.status)) endSession(snapshot.epoch);
            else setStartupError(true);
        } finally { if (isCurrentAuth(snapshot)) setLoading(false); }
    }, [fetchVocabularyMap, publishProfile]);

    useEffect(() => {
        const unsubscribe = subscribeAuth(type => {
            ++vocabularyRequest.current; ++courseRequest.current;
            userRef.current = null; profileFlight.current = null; courseQueue.current = Promise.resolve();
            setUser(null); setVocabularyMap({}); setStartupError(false); setLoading(false);
            if (type === 'external' && getAccessToken()) void checkLoggedInUser();
        });
        if (window.location.pathname === '/oauth2/redirect') setLoading(false);
        else void checkLoggedInUser();
        return unsubscribe;
    }, [checkLoggedInUser]);
    useEffect(() => {
        const onStorage = event => {
            if (event.key !== COURSE_NOTICE || !userRef.current) return;
            try {
                const hint = JSON.parse(event.newValue);
                // Storage is only a wake-up hint; all actual selection data is re-read from the server.
                if (hint.userId === userRef.current.userId && Number.isSafeInteger(hint.revision) && hint.revision > revision(userRef.current))
                    void refreshProfile();
            } catch { /* Ignore malformed or unrelated hints. */ }
        };
        const onFocus = () => { if (userRef.current) void refreshProfile(); };
        const onVisibility = () => { if (document.visibilityState === 'visible') onFocus(); };
        window.addEventListener('storage', onStorage); window.addEventListener('focus', onFocus);
        document.addEventListener('visibilitychange', onVisibility);
        return () => { window.removeEventListener('storage', onStorage); window.removeEventListener('focus', onFocus); document.removeEventListener('visibilitychange', onVisibility); };
    }, [refreshProfile]);

    const updateProfile = async userData => {
        const snapshot = authSnapshot(); const next = publishProfile(userData, snapshot);
        if (next) await fetchVocabularyMap(snapshot, next);
    };
    const login = async (token, userData, rememberMe = false) => {
        const epoch = beginAuthentication(rememberMe); storeAccessToken(token, epoch);
        const next = publishProfile(userData, { epoch });
        await fetchVocabularyMap({ epoch }, next); requireCurrentAuth({ epoch });
    };
    const authenticate = async credentials => {
        const epoch = beginAuthentication(credentials.rememberMe);
        const response = await queueAuthCookieOperation(epoch, () => api.post('/auth/login', credentials, {
            _authEpoch: epoch, timeout: 15000, skipGlobalError: true,
        }));
        storeAccessToken(response.data.accessToken, epoch);
        const profile = await api.get('/users/me'); requireCurrentAuth({ epoch });
        const next = publishProfile(profile.data, { epoch });
        await fetchVocabularyMap({ epoch }, next); requireCurrentAuth({ epoch });
    };
    const completeOAuthLogin = useCallback(async code => {
        const epoch = beginAuthentication(true);
        const exchange = await queueAuthCookieOperation(epoch, () => api.post('/auth/oauth/exchange', { code }, {
            _authEpoch: epoch, timeout: 15000, skipGlobalError: true,
        }));
        storeAccessToken(exchange.data.accessToken, epoch);
        const profile = await api.get('/users/me'); requireCurrentAuth({ epoch });
        const next = publishProfile(profile.data, { epoch });
        await fetchVocabularyMap({ epoch }, next); requireCurrentAuth({ epoch });
    }, [fetchVocabularyMap, publishProfile]);
    const logout = async (skipServerCall = false) => {
        clearInvitation(); endSession(); const epoch = getAuthEpoch();
        if (skipServerCall) return;
        try {
            await queueAuthCookieOperation(epoch, () => api.post('/auth/logout', null, {
                _authEpoch: epoch, _allowStaleAuthOperation: true, timeout: 15000, skipGlobalError: true,
            }), true);
        } catch (error) { if (!axios.isCancel(error)) console.error('Error during server-side logout:', safeError(error)); }
    };
    const changeLanguage = async courseCode => {
        const snapshot = authSnapshot(); const request = ++courseRequest.current;
        ++vocabularyRequest.current; setVocabularyMap({});
        const pending = courseQueue.current.then(async () => {
            requireCurrentAuth(snapshot);
            try {
                const response = await userApi.changeActiveCourse(courseCode, revision(userRef.current), crypto.randomUUID());
                requireCurrentAuth(snapshot);
                const next = publishProfile(response.data, snapshot);
                if (next) broadcastCourse(next);
                return next;
            } catch (error) {
                if (isCurrentAuth(snapshot)) await refreshProfile();
                throw error;
            }
        });
        courseQueue.current = pending.catch(() => {});
        const next = await pending; requireCurrentAuth(snapshot);
        if (request !== courseRequest.current) return false;
        await fetchVocabularyMap(snapshot, next);
        return true;
    };
    const syncXp = useCallback((userId, xp) => {
        if (!Number.isFinite(xp) || xp < 0 || userRef.current?.userId !== userId) return;
        publishProfile({ userId, xp }, authSnapshot());
    }, [publishProfile]);
    const syncCourseSelection = useCallback((ownerId, data) => {
        if (userRef.current?.userId !== ownerId || !data?.activeCourseId || !Number.isSafeInteger(data.activeCourseRevision)
                || data.activeCourseRevision <= revision(userRef.current)) return false;
        const snapshot = authSnapshot();
        const next = publishProfile({ userId: ownerId, activeCourseId: data.activeCourseId,
            activeCourseCode: data.activeCourseCode, activeCourseRevision: data.activeCourseRevision }, snapshot);
        if (next) { broadcastCourse(next); void fetchVocabularyMap(snapshot, next); return true; }
        return false;
    }, [publishProfile, fetchVocabularyMap]);
    const updateCurrentUser = update => {
        const current = userRef.current;
        if (current) publishProfile(typeof update === 'function' ? update(current) : update, authSnapshot());
    };

    if (loading) return <div className="d-flex justify-content-center align-items-center vh-100">
        <div className="spinner-border text-primary" role="status"><span className="visually-hidden">Betöltés…</span></div>
    </div>;
    if (startupError) return <main className="container py-5">
        <h1 className="h3">A bejelentkezés ellenőrzése nem sikerült</h1>
        <p role="alert">Ellenőrizd a kapcsolatot, majd próbáld újra. A munkamenetedet megőriztük.</p>
        <button type="button" className="btn btn-primary me-2" onClick={checkLoggedInUser}>Újrapróbálás</button>
        <button type="button" className="btn btn-outline-secondary" onClick={() => logout()}>Kijelentkezés</button>
    </main>;

    return <AuthContext.Provider value={{ user, setUser: updateCurrentUser, login, authenticate, completeOAuthLogin,
        logout, vocabularyMap, changeLanguage, updateProfile, syncXp, syncCourseSelection }}>{children}</AuthContext.Provider>;
};

// eslint-disable-next-line react-refresh/only-export-components
export const useAuth = () => useContext(AuthContext);
