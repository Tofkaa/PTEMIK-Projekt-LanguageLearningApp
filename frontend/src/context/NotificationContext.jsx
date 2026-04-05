import React, { createContext, useState, useEffect, useContext, useCallback } from 'react';
import api from '../services/api';
import { useAuth } from './AuthContext';

const NotificationContext = createContext();

export const NotificationProvider = ({ children }) => {
    const { user } = useAuth();
    const [notifications, setNotifications] = useState({ total: 0, pendingFriends: 0, pendingChallenges: 0 });

    // 1. MANUÁLIS FRISSÍTÉS (Ezt ajánljuk ki a Context-en keresztül, pl. gombokhoz)
    const fetchSummary = useCallback(async () => {
        if (!user) return;
        try {
            // Itt is használjuk a params objektumot a ?_t helyett, hogy ne legyen hiba!
            const response = await api.get('/notifications/summary', {
                params: { _t: new Date().getTime() }
            });
            setNotifications({
                total: response.data.total,
                pendingFriends: response.data.pendingFriendRequests, // Itt a fordítás!
                pendingChallenges: response.data.pendingChallenges,
                totalFriends: response.data.totalAcceptedFriends,
                totalHistory: response.data.totalHistoryItems
            });
        } catch (error) {
            console.error("Értesítések lekérése sikertelen", error);
        }
    }, [user]);

    // 2. AUTOMATIKUS EFFECT (SSE és kezdeti lekérés)
   useEffect(() => {
        // Ha nincs user objektum, meg se próbáljunk semmit csinálni
        if (!user) return;

        let isMounted = true; 

        const loadInitialData = async () => {
        // Ha nincs érvényes token a memóriában, meg se próbáljuk, 
        // megvárjuk a következő kört (vagy a focus-t)
        const currentToken = localStorage.getItem('token') || sessionStorage.getItem('token');
        if (!currentToken || currentToken === 'null') return;

        try {
            const response = await api.get('/notifications/summary', {
                params: { _t: new Date().getTime() }
            });
            
            if (isMounted) {
                setNotifications({
                    total: response.data.total,
                    pendingFriends: response.data.pendingFriendRequests,
                    pendingChallenges: response.data.pendingChallenges,
                    totalFriends: response.data.totalAcceptedFriends,
                    totalHistory: response.data.totalHistoryItems
                });
            }
        } catch (error) {
            // Ha 403-at kapunk, ne szemeteljük tele a konzolt, 
            // mert az AuthContext silent refresh-e úgyis megoldja
            if (error.response?.status !== 403) {
                console.error("Értesítések lekérése sikertelen", error);
                }
            }
        };

        // 1. Azonnali lekérés
        loadInitialData();

        // 2. SSE Csatlakozás (VÉDELEMMEL a null token ellen!)
        const token = localStorage.getItem('token') || sessionStorage.getItem('token');
        
        let eventSource = null;
        
        // Csak akkor csatlakozunk, ha TÉNYLEG van tokenünk
        if (token && token !== 'null') {
            eventSource = new EventSource(`http://localhost:8080/api/notifications/stream?token=${token}`);

            eventSource.addEventListener("ping", () => {
                console.log("Ping érkezett! Adatok frissítése...");
                loadInitialData(); 
            });

            eventSource.onerror = () => {
                eventSource.close();
            };
        } else {
            console.log("SSE csatlakozás várakozásra áll (token frissítés vagy hiány).");
        }

        // 3. BIZTONSÁGI HÁLÓ (1 perces frissítés)
        const intervalId = setInterval(() => {
            loadInitialData();
        }, 60000); 

        // 4. ABLAK AKTIVÁLÁSA (Amikor visszalép a fülre)
        const handleFocus = () => {
            loadInitialData();
        };
        window.addEventListener('focus', handleFocus);

        // 5. TAKARÍTÁS
        return () => {
            isMounted = false;
            if (eventSource) eventSource.close(); // Csak akkor zárjuk, ha meg is nyitottuk
            clearInterval(intervalId); 
            window.removeEventListener('focus', handleFocus); 
            setNotifications({ total: 0, pendingFriends: 0, pendingChallenges: 0, totalFriends: 0, totalHistory: 0 });
        };
    }, [user]);

    // BIZTONSÁGI HÁLÓ (Derived State)
    const activeNotifications = user ? notifications : { total: 0, pendingFriends: 0, pendingChallenges: 0 };

    return (
        <NotificationContext.Provider value={{ notifications: activeNotifications, refreshNotifications: fetchSummary }}>
            {children}
        </NotificationContext.Provider>
    );
};

// eslint-disable-next-line react-refresh/only-export-components
export const useNotifications = () => useContext(NotificationContext);