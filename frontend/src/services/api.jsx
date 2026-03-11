import axios from 'axios';

const api = axios.create({
    baseURL: 'http://localhost:8081/api',
    withCredentials: true, // Crucial for sending HttpOnly cookies (like the refresh token)
    headers: {
        'Content-Type': 'application/json',
    },
});

/**
 * Request Interceptor
 * Attaches the JWT Access Token from LocalStorage to the Authorization header of every request.
 */
api.interceptors.request.use(
    (config) => {
        const token = localStorage.getItem('token');
        if (token) {
            config.headers['Authorization'] = `Bearer ${token}`;
        }
        return config;
    },
    (error) => Promise.reject(error)
);

/**
 * Response Interceptor
 * Handles global API errors. Specifically intercepts 401 Unauthorized responses
 * to automatically attempt a silent token refresh using the HttpOnly cookie.
 */
api.interceptors.response.use(
    (response) => response,
    async (error) => {
        // Capture the original request configuration
        const originalRequest = error.config;

        // If the error is 401 (Unauthorized) AND we haven't retried this request yet
        if (error.response && error.response.status === 401 && !originalRequest._retry) {
            originalRequest._retry = true; // Mark as retried to prevent infinite loops

            try {
                console.log('Access token expired. Attempting silent refresh...');
                
                // Call the backend refresh endpoint. 
                // Note: The HttpOnly refreshToken cookie is automatically sent because of withCredentials: true
                const refreshResponse = await api.post('/auth/refresh');
                
                // Extract the new access token
                const newToken = refreshResponse.data.accessToken;
                
                // Save the new token to LocalStorage
                localStorage.setItem('token', newToken);
                
                // Update the Authorization header of the original failed request
                originalRequest.headers['Authorization'] = `Bearer ${newToken}`;
                
                // Resend the original request with the new token!
                return api(originalRequest);

            } catch (refreshError) {
                // If the refresh request ALSO fails (e.g., the refresh token cookie expired or is invalid)
                console.warn('Refresh token expired or invalid. Forcing logout...');
                
                localStorage.removeItem('token');
                // Redirect the user to the login page
                window.location.href = '/login'; 
                
                return Promise.reject(refreshError);
            }
        }
        
        // For all other errors, just reject the promise as usual
        return Promise.reject(error);
    }
);

export default api;