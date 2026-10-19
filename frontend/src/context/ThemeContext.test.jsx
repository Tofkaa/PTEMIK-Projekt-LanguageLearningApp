import { act, cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ThemeProvider, useTheme } from './ThemeContext';
import ThemeToggle from '../components/ThemeToggle';

let media;
let listeners;

function Preview() {
    const { theme } = useTheme();
    return <><ThemeToggle /><output aria-label="Aktuális téma">{theme}</output>
        <input aria-label="Válasz" defaultValue="alma" /></>;
}

function openPreview() {
    return render(<ThemeProvider><Preview /></ThemeProvider>);
}

function setSystemDark(matches) {
    act(() => {
        media.matches = matches;
        listeners.forEach(listener => listener());
    });
}

describe('theme preferences', () => {
    beforeEach(() => {
        localStorage.clear();
        listeners = new Set();
        media = {
            matches: false,
            addEventListener: vi.fn((type, listener) => listeners.add(listener)),
            removeEventListener: vi.fn((type, listener) => listeners.delete(listener)),
        };
        vi.stubGlobal('matchMedia', vi.fn(() => media));
    });

    afterEach(() => {
        cleanup();
        vi.restoreAllMocks();
        vi.unstubAllGlobals();
        localStorage.clear();
        document.documentElement.removeAttribute('data-bs-theme');
        document.documentElement.style.colorScheme = '';
        document.body.className = '';
    });

    it('follows system changes by default without losing the current answer or focus', () => {
        openPreview();
        expect(screen.getByRole('combobox', { name: 'Megjelenési téma' })).toHaveValue('system');
        expect(document.documentElement).toHaveAttribute('data-bs-theme', 'light');
        const answer = screen.getByRole('textbox', { name: 'Válasz' });
        fireEvent.change(answer, { target: { value: 'körte' } });
        answer.focus();
        setSystemDark(true);
        expect(document.documentElement).toHaveAttribute('data-bs-theme', 'dark');
        expect(document.documentElement.style.colorScheme).toBe('dark');
        expect(answer).toHaveValue('körte');
        expect(answer).toHaveFocus();
        expect(localStorage.getItem('app-theme')).toBe('system');
    });

    it.each(['light', 'dark'])('preserves an existing %s choice across system changes and remounts', saved => {
        localStorage.setItem('app-theme', saved);
        const view = openPreview();
        setSystemDark(true);
        setSystemDark(false);
        expect(document.documentElement).toHaveAttribute('data-bs-theme', saved);
        view.unmount();
        openPreview();
        expect(screen.getByRole('combobox')).toHaveValue(saved);
    });

    it('persists manual selection and allows returning to the current system preference', () => {
        openPreview();
        const picker = screen.getByRole('combobox');
        fireEvent.change(picker, { target: { value: 'dark' } });
        expect(localStorage.getItem('app-theme')).toBe('dark');
        expect(document.body).toHaveClass('bg-dark', 'text-light');
        fireEvent.change(picker, { target: { value: 'system' } });
        expect(document.documentElement).toHaveAttribute('data-bs-theme', 'light');
        expect(document.body).not.toHaveClass('bg-dark', 'text-light');
        setSystemDark(true);
        expect(document.documentElement).toHaveAttribute('data-bs-theme', 'dark');
    });

    it('ignores an invalid saved preference', () => {
        localStorage.setItem('app-theme', 'invalid');
        media.matches = true;
        openPreview();
        expect(screen.getByRole('combobox')).toHaveValue('system');
        expect(document.documentElement).toHaveAttribute('data-bs-theme', 'dark');
    });

    it('still allows switching when browser storage is blocked', () => {
        vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => { throw new Error('blocked'); });
        vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => { throw new Error('blocked'); });
        openPreview();
        fireEvent.change(screen.getByRole('combobox'), { target: { value: 'dark' } });
        expect(document.documentElement).toHaveAttribute('data-bs-theme', 'dark');
    });

    it('cleans up the system preference listener', () => {
        const view = openPreview();
        expect(listeners.size).toBe(1);
        view.unmount();
        expect(listeners.size).toBe(0);
    });
});
