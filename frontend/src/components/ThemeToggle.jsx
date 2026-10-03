import React from 'react';
import { Button } from 'react-bootstrap';
import { useTheme } from '../context/ThemeContext';

const ThemeToggle = () => {
    const { theme, toggleTheme } = useTheme();

    return (
        <Button 
            variant={theme === 'dark' ? 'outline-light' : 'outline-dark'}
            className="rounded-circle p-2 d-flex align-items-center justify-content-center transition-all hover-scale"
            style={{ width: '42px', height: '42px', borderWidth: '2px' }}
            onClick={toggleTheme}
            title={theme === 'dark' ? 'Váltás Világos Módra' : 'Váltás Sötét Módra'}
        >
            <span style={{ fontSize: '1.2rem', lineHeight: 1 }}>
                {theme === 'dark' ? '☀️' : '🌙'}
            </span>
        </Button>
    );
};

export default ThemeToggle;