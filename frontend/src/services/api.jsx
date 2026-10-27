import axios from 'axios';
import { authSnapshot, endSession, getAccessToken, getAuthEpoch, isCurrentAuth, queueAuthCookieOperation,
    requireCurrentAuth, staleAuthError, storeAccessToken, tokenSessionId } from './authSession';

const api = axios.create({
    baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
    withCredentials: true,
    headers: { 'Content-Type': 'application/json' },
});
const isAuthEndpoint = config => /\/auth\//.test(config?.url || '');
let refreshFlight = null;

api.interceptors.request.use(config => {
    config._authEpoch ??= getAuthEpoch();
    if (!config._allowStaleAuthOperation) requireCurrentAuth({ epoch: config._authEpoch });
    const token = getAccessToken();
    config._authToken = token;
    if (!isAuthEndpoint(config) && token) config.headers.set('Authorization', `Bearer ${token}`);
    else config.headers.delete('Authorization');
    return config;
});

const report = message => window.dispatchEvent(new CustomEvent('api-error', { detail: message }));
const refreshAccessToken = (snapshot, skipGlobalError) => {
    if (refreshFlight?.epoch === snapshot.epoch) {
        if (!skipGlobalError) refreshFlight.notifyErrors = true;
        return refreshFlight.promise;
    }
    const flight = { epoch: snapshot.epoch, notifyErrors: !skipGlobalError };
    flight.promise = queueAuthCookieOperation(snapshot.epoch, () => api.post('/auth/refresh', null, {
        _authEpoch: snapshot.epoch, skipGlobalError: true, timeout: 15000,
    })).then(response => {
        requireCurrentAuth(snapshot);
        const token = response.data?.accessToken;
        const sid = tokenSessionId(snapshot.token || '');
        if (sid && sid !== tokenSessionId(token || '')) {
            endSession(snapshot.epoch);
            throw staleAuthError();
        }
        storeAccessToken(token, snapshot.epoch);
        return token;
    }).catch(error => {
        if (!isCurrentAuth(snapshot) || axios.isCancel(error)) throw staleAuthError();
        if ([400, 401, 403].includes(error.response?.status)) endSession(snapshot.epoch);
        else if (flight.notifyErrors) report('A munkamenet frissítése most nem sikerült. Ellenőrizd a kapcsolatot, majd próbáld újra.');
        throw error;
    }).finally(() => { if (refreshFlight === flight) refreshFlight = null; });
    refreshFlight = flight;
    return flight.promise;
};

api.interceptors.response.use(response => {
    if (response.config?._authEpoch !== undefined) requireCurrentAuth({ epoch: response.config._authEpoch });
    return response;
}, async error => {
    const request = error.config;
    if (axios.isCancel(error)) return Promise.reject(error);
    if (request?._authEpoch !== undefined && request._authEpoch !== getAuthEpoch()) return Promise.reject(staleAuthError());
    if (!request || isAuthEndpoint(request)) return Promise.reject(error);

    if (error.response?.status === 401 && getAccessToken()) {
        if (request._retry) {
            endSession(request._authEpoch);
            return Promise.reject(error);
        }
        request._retry = true;
        const snapshot = authSnapshot();
        if (request._authToken === snapshot.token) await refreshAccessToken(snapshot, request.skipGlobalError);
        requireCurrentAuth(snapshot);
        return api(request);
    }

    if (!request.skipGlobalError) {
        let message = 'Ismeretlen hiba történt.';
        if (!error.response) message = 'Hálózati hiba! Kérlek ellenőrizd az internetkapcsolatot.';
        else if (error.response.status >= 500) message = 'Váratlan szerverhiba (500). Kérlek próbáld újra később!';
        else if (error.response.status === 404) message = 'A keresett adat vagy végpont nem található (404).';
        else if (error.response.status === 403) message = 'Nincs megfelelő jogosultságod ehhez a művelethez! (403)';
        else if (error.response.status === 401) message = 'A művelethez be kell jelentkezned.';
        else if (error.response.status === 413) message = 'A beküldött válaszok összmérete túl nagy. Rövidítsd a választ, majd próbáld újra.';
        else if ([400, 409].includes(error.response.status)) {
            const backend = error.response.data?.message || error.response.data;
            message = typeof backend === 'string' ? backend : error.response.status === 409
                ? 'Az adatok időközben megváltoztak (409).' : 'Hibás vagy érvénytelen kérés (400).';
        }
        report(message);
    }
    return Promise.reject(error);
});

export default api;
