import React, { createContext, useState, useEffect, useContext } from 'react';
import api from '../services/api';

// 1. Létrehozzuk magát a Context-et (az "űrt", amiben az adatok lebegnek majd)
const AuthContext = createContext();

// 2. A Provider komponens, ami "körbeöleli" majd az egész alkalmazásunkat
export const AuthProvider = ({ children }) => {
    const [user, setUser] = useState(null);
    const [loading, setLoading] = useState(true);

    // Ez lefut minden alkalommal, amikor az alkalmazás betöltődik (F5 frissítés)
    useEffect(() => {
        const checkLoggedInUser = async () => {
            const token = localStorage.getItem('token');
            if (token) {
                try {
                    // Ha van token, lekérjük a legfrissebb profil adatokat a mi szuper biztonságos végpontunkról!
                    const response = await api.get('/users/me');
                    setUser(response.data);
                } catch (error) {
                    console.error("Hiba a profil lekérésekor (lejárt token?):", error);
                    localStorage.removeItem('token');
                    setUser(null);
                }
            }
            setLoading(false);
        };

        checkLoggedInUser();
    }, []);

    // A Bejelentkezés metódus (ezt fogjuk hívni a Login oldalon, miután visszajött a 200 OK a backendtől)
    const login = (token, userData) => {
        localStorage.setItem('token', token);
        setUser(userData);
    };

    // A Kijelentkezés metódus (ezt kötjük majd a Navbar "Kijelentkezés" gombjára)
    const logout = () => {
        localStorage.removeItem('token');
        setUser(null);
    };

    // Ha még töltünk (pl. várjuk a backend válaszát az F5 után), nem rendereljük ki az oldalt, csak egy töltőképernyőt
    if (loading) {
        return <div className="d-flex justify-content-center align-items-center vh-100">
            <div className="spinner-border text-primary" role="status">
                <span className="visually-hidden">Töltés...</span>
            </div>
        </div>;
    }

    // 3. A Provider "szétosztja" ezeket a metódusokat és adatokat a belső komponenseknek (children)
    return (
        <AuthContext.Provider value={{ user, login, logout }}>
            {children}
        </AuthContext.Provider>
    );
};

// 4. Egy Custom Hook, amivel bármelyik komponensből 1 sorral lekérhetjük a user adatokat!
export const useAuth = () => {
    return useContext(AuthContext);
};