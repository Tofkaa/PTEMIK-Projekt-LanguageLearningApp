import { act, fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import AchievementNotification from './AchievementNotification';

const awards = [{ userAchievementId: 'award-1', name: 'Első lecke', description: 'Sikeres tanulás', iconUrl: '🏆' }];
function setup() {
    const close = vi.fn();
    render(<MemoryRouter><main id="main-content" tabIndex={-1}><AchievementNotification awards={awards} onClose={close} /></main></MemoryRouter>);
    return close;
}
beforeEach(() => { vi.useFakeTimers(); Object.defineProperty(document, 'hidden', { configurable: true, value: false }); });
afterEach(() => { vi.useRealTimers(); vi.unstubAllGlobals(); });
describe('achievement card visible-time budget', () => {
    it('expires after ten seconds and never steals focus', () => {
        const close = setup();
        expect(screen.getByRole('button')).not.toHaveFocus();
        act(() => vi.advanceTimersByTime(9999)); expect(close).not.toHaveBeenCalled();
        act(() => vi.advanceTimersByTime(1)); expect(close).toHaveBeenCalledTimes(1);
    });
    it('pauses while hovered and resumes the remaining time', () => {
        const close = setup(); const card = screen.getByRole('region');
        act(() => vi.advanceTimersByTime(3000)); fireEvent.mouseEnter(card);
        act(() => vi.advanceTimersByTime(20000)); expect(close).not.toHaveBeenCalled();
        fireEvent.mouseLeave(card); act(() => vi.advanceTimersByTime(6999)); expect(close).not.toHaveBeenCalled();
        act(() => vi.advanceTimersByTime(1)); expect(close).toHaveBeenCalledOnce();
    });
    it('keeps focused controls even when focus moves inside the card', () => {
        const close = setup(); const link = screen.getByRole('link'); const button = screen.getByRole('button');
        fireEvent.focus(link); fireEvent.blur(link, { relatedTarget: button }); fireEvent.focus(button);
        act(() => vi.advanceTimersByTime(20000)); expect(close).not.toHaveBeenCalled();
        fireEvent.blur(button, { relatedTarget: document.body });
        act(() => vi.advanceTimersByTime(10000)); expect(close).toHaveBeenCalledOnce();
    });
    it('pauses while the document is hidden', () => {
        const close = setup(); act(() => vi.advanceTimersByTime(4000));
        Object.defineProperty(document, 'hidden', { configurable: true, value: true }); fireEvent(document, new Event('visibilitychange'));
        act(() => vi.advanceTimersByTime(60000)); expect(close).not.toHaveBeenCalled();
        Object.defineProperty(document, 'hidden', { configurable: true, value: false }); fireEvent(document, new Event('visibilitychange'));
        act(() => vi.advanceTimersByTime(6000)); expect(close).toHaveBeenCalledOnce();
    });
    it('provides an untimed profile destination and manual dismissal', () => {
        const close = setup(); expect(screen.getByRole('link', { name: 'Megtekintés' })).toHaveAttribute('href', '/profile#achievements');
        screen.getByRole('button', { name: 'Értesítés bezárása' }).focus();
        fireEvent.click(screen.getByRole('button', { name: 'Értesítés bezárása' })); expect(close).toHaveBeenCalledOnce();
        expect(screen.getByRole('main')).toHaveFocus();
    });
});

it('starts presentation and the timer only when the card enters the viewport', () => {
    let changed;
    vi.stubGlobal('IntersectionObserver', class {
        constructor(callback) { changed = callback; }
        observe() {} disconnect() {}
    });
    const close = vi.fn(), presented = vi.fn();
    render(<MemoryRouter><AchievementNotification awards={awards} onClose={close} onPresented={presented} /></MemoryRouter>);
    act(() => vi.advanceTimersByTime(60000)); expect(close).not.toHaveBeenCalled(); expect(presented).not.toHaveBeenCalled();
    act(() => changed([{ isIntersecting: true }])); expect(presented).toHaveBeenCalledOnce();
    act(() => vi.advanceTimersByTime(3000)); act(() => changed([{ isIntersecting: false }]));
    act(() => vi.advanceTimersByTime(60000)); expect(close).not.toHaveBeenCalled();
    act(() => changed([{ isIntersecting: true }])); expect(presented).toHaveBeenCalledOnce();
    act(() => vi.advanceTimersByTime(7000)); expect(close).toHaveBeenCalledOnce();
});
