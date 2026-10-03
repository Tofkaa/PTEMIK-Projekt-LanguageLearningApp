import React, { createContext, useState, useEffect, useContext } from 'react';

const ThemeContext = createContext();

export const ThemeProvider = ({ children }) => {
    // Alapértelmezettként sötét mód, vagy amit a localStorage-ben talál
    const [theme, setTheme] = useState(localStorage.getItem('app-theme') || 'dark');

    useEffect(() => {
        // Állapot mentése
        localStorage.setItem('app-theme', theme);
        
        // Bootstrap 5.3+ global téma attribútum beállítása a gyökér elemen
        document.documentElement.setAttribute('data-bs-theme', theme);
        
        // Body alap háttér beállítása
        if (theme === 'dark') {
            document.body.classList.add('bg-dark', 'text-light');
            document.body.classList.remove('bg-light', 'text-dark');
        } else {
            document.body.classList.add('bg-light', 'text-dark');
            document.body.classList.remove('bg-dark', 'text-light');
        }
    }, [theme]);

    const toggleTheme = () => {
        setTheme(prevTheme => prevTheme === 'dark' ? 'light' : 'dark');
    };

    return (
        <ThemeContext.Provider value={{ theme, toggleTheme }}>
            {children}
        </ThemeContext.Provider>
    );
};

// eslint-disable-next-line react-refresh/only-export-components
export const useTheme = () => useContext(ThemeContext);