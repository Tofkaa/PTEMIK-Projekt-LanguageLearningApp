import React, { createContext, useState, useEffect, useContext, useCallback } from 'react';
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

    const fetchVocabularyMap = useCallback(async snapshot => {
        try {
            const response = await vocabularyApi.getVocabularyMap();
            if (isCurrentAuth(snapshot)) setVocabularyMap(response.data);
        } catch (error) {
            if (isCurrentAuth(snapshot) && !axios.isCancel(error)) console.error('Hiba a szótérkép betöltésekor:', error);
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
        setUser(current => current && (!current.userId || !userData.userId || current.userId === userData.userId) ? userData : current);
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
    const completeOAuthLogin = useCallback(async token => {
        const epoch = beginAuthentication(true);
        storeAccessToken(token, epoch);
        const profile = await api.get('/users/me');
        requireCurrentAuth({ epoch });
        setUser(profile.data);
        await fetchVocabularyMap({ epoch });
        requireCurrentAuth({ epoch });
    }, [fetchVocabularyMap]);
    const logout = async (skipServerCall = false) => {
        endSession();
        const epoch = getAuthEpoch();
        if (skipServerCall) return;
        try {
            await queueAuthCookieOperation(epoch, () => api.post('/auth/logout', null, {
                _authEpoch: epoch, _allowStaleAuthOperation: true, timeout: 15000, skipGlobalError: true,
            }), true);
        } catch (error) {
            if (!axios.isCancel(error)) console.error('Error during server-side logout:', error);
        }
    };
    const changeLanguage = async courseCode => {
        const snapshot = authSnapshot();
        const response = await userApi.changeActiveCourse(courseCode);
        requireCurrentAuth(snapshot);
        setUser(response.data);
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
