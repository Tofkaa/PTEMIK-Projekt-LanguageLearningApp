import axios from 'axios';

const api = axios.create({
    baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
    withCredentials: true,
    headers: {
        'Content-Type': 'application/json',
    },
});

// --- SEGÉDFÜGGVÉNYEK A TOKEN KEZELÉSHEZ ---
const getToken = () => sessionStorage.getItem('token') || localStorage.getItem('token');

const setToken = (token) => {
    if (sessionStorage.getItem('token')) {
        sessionStorage.setItem('token', token);
    } else {
        localStorage.setItem('token', token);
    }
};

const clearTokens = () => {
    localStorage.removeItem('token');
    sessionStorage.removeItem('token');
};

// --- REQUEST INTERCEPTOR ---
api.interceptors.request.use(
    (config) => {
        const token = getToken();
        if (token) {
            config.headers['Authorization'] = `Bearer ${token}`;
        }
        return config;
    },
    (error) => Promise.reject(error)
);

// --- FRISSÍTÉSI VÁRÓLISTA (REFRESH LOCK) ---
let isRefreshing = false;
let failedQueue = [];

const processQueue = (error, token = null) => {
    failedQueue.forEach(prom => {
        if (error) {
            prom.reject(error);
        } else {
            prom.resolve(token);
        }
    });
    failedQueue = [];
};

// --- RESPONSE INTERCEPTOR ---
api.interceptors.response.use(
    (response) => response,
    async (error) => {
        const originalRequest = error.config;

        if (originalRequest.url.includes('/auth/login') || originalRequest.url.includes('/auth/refresh')) {
            return Promise.reject(error);
        }

        if (error.response && (error.response.status === 401 || error.response.status === 403) && !originalRequest._retry) {
            if (isRefreshing) {
                return new Promise(function(resolve, reject) {
                    failedQueue.push({ resolve, reject });
                }).then(token => {
                    originalRequest.headers['Authorization'] = 'Bearer ' + token;
                    return api(originalRequest);
                }).catch(err => {
                    return Promise.reject(err);
                });
            }

            originalRequest._retry = true;
            isRefreshing = true;

            try {
                const refreshResponse = await api.post('/auth/refresh');
                const newToken = refreshResponse.data.accessToken;
                setToken(newToken);
                originalRequest.headers['Authorization'] = `Bearer ${newToken}`;
                processQueue(null, newToken);
                return api(originalRequest);

            } catch (refreshError) {
                processQueue(refreshError, null);
                clearTokens();
                window.location.href = '/login'; 
                return Promise.reject(refreshError);
            } finally {
                isRefreshing = false;
            }
        }
        
        // --- GLOBÁLIS HIBAKEZELÉS ---
        if (!originalRequest.url.includes('/auth/login')) {
            let errorMsg = 'Ismeretlen hiba történt.';
            
            if (!error.response) {
                errorMsg = 'Hálózati hiba! Kérlek ellenőrizd az internetkapcsolatot.';
            } else if (error.response.status >= 500) {
                errorMsg = 'Váratlan szerverhiba (500). Kérlek próbáld újra később!';
            } else if (error.response.status === 404) {
                errorMsg = 'A keresett adat vagy végpont nem található (404).';
            } else if (error.response.status === 403) {
                errorMsg = 'Nincs megfelelő jogosultságod ehhez a művelethez! (403)';
            } else if (error.response.status === 400) {
                const backendMsg = error.response.data?.message || error.response.data;
                errorMsg = typeof backendMsg === 'string' ? backendMsg : 'Hibás vagy érvénytelen kérés (400).';
            }

            // Kilőjük az egyedi eseményt a React felé
            window.dispatchEvent(new CustomEvent('api-error', { detail: errorMsg }));
        }

        return Promise.reject(error);
    }
);

export default api;