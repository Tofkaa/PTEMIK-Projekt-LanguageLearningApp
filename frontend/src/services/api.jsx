import axios from 'axios';

const api = axios.create({
    baseURL: 'http://localhost:8081/api',
    headers: {
        'Content-Type': 'application/json',
    },
});

api.interceptors.request.use(
    (config) => {
  
        const token = localStorage.getItem('token');
        
        if (token) {
            config.headers['Authorization'] = `Bearer ${token}`;
        }
        
        return config;
    },
    (error) => {
        return Promise.reject(error);
    }
);

api.interceptors.response.use(
    (response) => {
        return response;
    },
    (error) => {

        if (error.response && error.response.status === 401) {
            console.warn('Token expired or invalid. Logging out...');
   
            localStorage.removeItem('token');
            // Később ide jön majd egy átirányítás a /login oldalra!
            // window.location.href = '/login'; 
        }
        
        return Promise.reject(error);
    }
);

export default api;