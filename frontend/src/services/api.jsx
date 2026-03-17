import axios from 'axios';

const api = axios.create({
    baseURL: 'http://localhost:8081/api',
    withCredentials: true, // Szükséges a HttpOnly cookie-khoz
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

        // VÉDELEM: Ne csináljunk semmit, ha maga a login vagy a refresh végpont hibázik! (Ez okozta a kidobást)
        if (originalRequest.url.includes('/auth/login') || originalRequest.url.includes('/auth/refresh')) {
            return Promise.reject(error);
        }

        // Ha 401 vagy 403 a hiba, és még nem próbáltuk újra
        if (error.response && (error.response.status === 401 || error.response.status === 403) && !originalRequest._retry) {
            
            // Ha épp folyamatban van egy frissítés, akkor a többi kérést VÁRÓLISTÁRA tesszük
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
                console.log('Access token expired or unauthorized. Attempting silent refresh...');
                const refreshResponse = await api.post('/auth/refresh');
                const newToken = refreshResponse.data.accessToken;
                
                // Mentsük le az új tokent a megfelelő helyre
                setToken(newToken);
                
                // Frissítsük az aktuális elakadt kérés fejlécét
                originalRequest.headers['Authorization'] = `Bearer ${newToken}`;
                
                // Engedjük el a várólistán lévő többi kérést is az új tokennel!
                processQueue(null, newToken);
                
                // Indítsuk újra az eredeti kérést
                return api(originalRequest);

            } catch (refreshError) {
                console.warn('Refresh token expired or invalid. Forcing logout...');
                processQueue(refreshError, null);
                clearTokens();
                
                // Csak akkor dobjuk ki a usert, ha tényleg lejárt a refresh token is
                window.location.href = '/login'; 
                return Promise.reject(refreshError);
            } finally {
                // Engedjük fel a zárat
                isRefreshing = false;
            }
        }
        
        return Promise.reject(error);
    }
);

export default api;