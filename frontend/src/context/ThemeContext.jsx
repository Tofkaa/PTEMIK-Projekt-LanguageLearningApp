import { createContext, useState, useEffect, useContext, useSyncExternalStore } from 'react';

const ThemeContext = createContext();

const systemThemeQuery = '(prefers-color-scheme: dark)';
const preferences = ['system', 'light', 'dark'];

function readPreference() {
    try {
        const saved = localStorage.getItem('app-theme');
        return preferences.includes(saved) ? saved : 'system';
    } catch {
        return 'system';
    }
}

function subscribeToSystemTheme(onChange) {
    const media = window.matchMedia?.(systemThemeQuery);
    media?.addEventListener('change', onChange);
    return () => media?.removeEventListener('change', onChange);
}

function isSystemDark() {
    return window.matchMedia?.(systemThemeQuery).matches ?? false;
}

export const ThemeProvider = ({ children }) => {
    const [themePreference, setPreference] = useState(readPreference);
    const systemDark = useSyncExternalStore(subscribeToSystemTheme, isSystemDark, () => false);
    const theme = themePreference === 'system' ? (systemDark ? 'dark' : 'light') : themePreference;

    useEffect(() => {
        try {
            localStorage.setItem('app-theme', themePreference);
        } catch {
            // Theme switching still works when browser storage is unavailable.
        }
    }, [themePreference]);

    useEffect(() => {
        document.documentElement.setAttribute('data-bs-theme', theme);
        document.documentElement.style.colorScheme = theme;
        
        // Body alap háttér beállítása
        if (theme === 'dark') {
            document.body.classList.add('bg-dark', 'text-light');
            document.body.classList.remove('bg-light', 'text-dark');
        } else {
            document.body.classList.add('bg-light', 'text-dark');
            document.body.classList.remove('bg-dark', 'text-light');
        }
    }, [theme]);

    const setThemePreference = (preference) => {
        if (preferences.includes(preference)) setPreference(preference);
    };

    return (
        <ThemeContext.Provider value={{ theme, themePreference, setThemePreference }}>
            {children}
        </ThemeContext.Provider>
    );
};

// eslint-disable-next-line react-refresh/only-export-components
export const useTheme = () => useContext(ThemeContext);
