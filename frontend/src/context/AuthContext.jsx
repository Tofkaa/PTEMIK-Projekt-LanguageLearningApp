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

export const AuthProvider = ({ children }) => {
    const [user, setUser] = useState(null);
    const [vocabularyMap, setVocabularyMap] = useState({});
    const [loading, setLoading] = useState(true);
    const [startupError, setStartupError] = useState(false);
    const vocabularyRequest = useRef(0);
    const courseRequest = useRef(0);
    const courseQueue = useRef(Promise.resolve());

    const fetchVocabularyMap = useCallback(async snapshot => {
        const request = ++vocabularyRequest.current;
        try {
            const response = await vocabularyApi.getVocabularyMap();
            if (isCurrentAuth(snapshot) && request === vocabularyRequest.current) setVocabularyMap(response.data);
        } catch (error) {
            if (isCurrentAuth(snapshot) && request === vocabularyRequest.current && !axios.isCancel(error)) console.error('Hiba a szótérkép betöltésekor:', safeError(error));
        }
    }, []);

    const checkLoggedInUser = useCallback(async () => {
        const snapshot = authSnapshot();
        setStartupError(false);
        if (!snapshot.token) { setLoading(false); return; }
        setLoading(true);
        try {
            const response = await api.get('/users/me');
            requireCurrentAuth(snapshot);
            setUser(response.data);
            await fetchVocabularyMap(snapshot);
        } catch (error) {
            if (!isCurrentAuth(snapshot) || axios.isCancel(error)) return;
            if ([401, 403].includes(error.response?.status)) endSession(snapshot.epoch);
            else setStartupError(true);
        } finally {
            if (isCurrentAuth(snapshot)) setLoading(false);
        }
    }, [fetchVocabularyMap]);

    useEffect(() => {
        const unsubscribe = subscribeAuth(type => {
            ++vocabularyRequest.current;
            ++courseRequest.current;
            setUser(null);
            setVocabularyMap({});
            setStartupError(false);
            setLoading(false);
            if (type === 'external' && getAccessToken()) void checkLoggedInUser();
        });
        if (window.location.pathname === '/oauth2/redirect') setLoading(false);
        else void checkLoggedInUser();
        return unsubscribe;
    }, [checkLoggedInUser]);

    const updateProfile = async userData => {
        const snapshot = authSnapshot();
        // A lesson/profile response may predate a course change. Only changeLanguage changes this field.
        setUser(current => current && (!current.userId || !userData.userId || current.userId === userData.userId)
            ? { ...userData, activeCourseCode: current.activeCourseCode ?? userData.activeCourseCode } : current);
        await fetchVocabularyMap(snapshot);
    };
    const login = async (token, userData, rememberMe = false) => {
        const epoch = beginAuthentication(rememberMe);
        storeAccessToken(token, epoch);
        setUser(userData);
        await fetchVocabularyMap({ epoch });
        requireCurrentAuth({ epoch });
    };
    const authenticate = async credentials => {
        const epoch = beginAuthentication(credentials.rememberMe);
        const response = await queueAuthCookieOperation(epoch, () => api.post('/auth/login', credentials, {
            _authEpoch: epoch, timeout: 15000, skipGlobalError: true,
        }));
        storeAccessToken(response.data.accessToken, epoch);
        const profile = await api.get('/users/me');
        requireCurrentAuth({ epoch });
        setUser(profile.data);
        await fetchVocabularyMap({ epoch });
        requireCurrentAuth({ epoch });
    };
    const completeOAuthLogin = useCallback(async code => {
        const epoch = beginAuthentication(true);
        const exchange = await queueAuthCookieOperation(epoch, () => api.post('/auth/oauth/exchange', { code }, {
            _authEpoch: epoch, timeout: 15000, skipGlobalError: true,
        }));
        storeAccessToken(exchange.data.accessToken, epoch);
        const profile = await api.get('/users/me');
        requireCurrentAuth({ epoch });
        setUser(profile.data);
        await fetchVocabularyMap({ epoch });
        requireCurrentAuth({ epoch });
    }, [fetchVocabularyMap]);
    const logout = async (skipServerCall = false) => {
        clearInvitation();
        endSession();
        const epoch = getAuthEpoch();
        if (skipServerCall) return;
        try {
            await queueAuthCookieOperation(epoch, () => api.post('/auth/logout', null, {
                _authEpoch: epoch, _allowStaleAuthOperation: true, timeout: 15000, skipGlobalError: true,
            }), true);
        } catch (error) {
            if (!axios.isCancel(error)) console.error('Error during server-side logout:', safeError(error));
        }
    };
    const changeLanguage = async courseCode => {
        const snapshot = authSnapshot();
        const request = ++courseRequest.current;
        ++vocabularyRequest.current;
        setVocabularyMap({});
        // Aborting a PATCH does not undo its server-side write. Serialize mutations instead.
        const pending = courseQueue.current.then(() => {
            requireCurrentAuth(snapshot);
            return userApi.changeActiveCourse(courseCode);
        });
        courseQueue.current = pending.catch(() => {});
        let response;
        try {
            response = await pending;
        } catch (error) {
            if (isCurrentAuth(snapshot) && request === courseRequest.current) await fetchVocabularyMap(snapshot);
            throw error;
        }
        requireCurrentAuth(snapshot);
        // Also retain an intermediate successful switch if the following switch fails.
        setUser(response.data);
        if (request !== courseRequest.current) return false;
        await fetchVocabularyMap(snapshot);
        return true;
    };
    const syncXp = useCallback((userId, xp) => {
        if (!Number.isFinite(xp) || xp < 0) return;
        setUser(current => current?.userId === userId && current.xp !== xp ? { ...current, xp } : current);
    }, []);
    const updateCurrentUser = update => setUser(current => current ? (typeof update === 'function' ? update(current) : update) : null);

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
        logout, vocabularyMap, changeLanguage, updateProfile, syncXp }}>{children}</AuthContext.Provider>;
};

// eslint-disable-next-line react-refresh/only-export-components
export const useAuth = () => useContext(AuthContext);
