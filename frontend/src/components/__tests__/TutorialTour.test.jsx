import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import TutorialTour from '../TutorialTour';

vi.mock('../../context/AuthContext', () => ({ useAuth: () => ({ user: { userId: 'tour-test' } }) }));

beforeEach(() => {
    localStorage.setItem('hasSeenTutorial_tour-test', 'true');
    vi.stubGlobal('ResizeObserver', class { observe() {} unobserve() {} disconnect() {} });
    vi.stubGlobal('matchMedia', vi.fn(() => ({ matches: true, addEventListener() {}, removeEventListener() {} })));
    vi.spyOn(HTMLElement.prototype, 'getBoundingClientRect').mockReturnValue({ x: 0, y: 0, top: 0, left: 0, right: 800, bottom: 600, width: 800, height: 600 });
});
afterEach(() => { cleanup(); localStorage.clear(); vi.restoreAllMocks(); vi.unstubAllGlobals(); });

function openPage() {
    return render(<main id="main-content" tabIndex={-1}>
        <TutorialTour />
        <a className="tour-navbar-classrooms" href="#rooms">Osztálytermek megnyitása</a>
        <a className="tour-navbar-community" href="#friends">Közösség megnyitása</a>
        <a className="tour-dictionary-hub" href="#dictionary">Szótár megnyitása</a>
        <section className="tour-hero-widget">Ajánló</section>
        <section className="tour-learning-path">Leckék</section>
        <section className="tour-performance">Teljesítmény</section>
        <section className="tour-leaderboard">Ranglista</section>
        <button className="tour-profile-menu">Profil</button>
    </main>);
}

describe('installed Joyride integration', () => {
    it('keeps all nine steps available when navbar targets are hidden in a narrow viewport', async () => {
        vi.mocked(window.matchMedia).mockImplementation(query => ({
            matches: query !== '(min-width: 992px)', addEventListener() {}, removeEventListener() {},
        }));
        openPage();
        for (const selector of ['.tour-navbar-classrooms', '.tour-navbar-community', '.tour-dictionary-hub', '.tour-profile-menu']) {
            document.querySelector(selector).style.display = 'none';
        }
        const trigger = screen.getByRole('button', { name: 'Az oldal bemutatása' });
        trigger.focus(); fireEvent.click(trigger);
        for (const [index, name] of ['Üdvözlünk a LanguageApp-ban!', 'Osztálytermek', 'Közösség', 'Szótár HUB',
            'Intelligens ajánló', 'Tanulási Útvonal', 'Teljesítmény', 'Ranglista', 'Profil és Beállítások'].entries()) {
            const dialog = await screen.findByRole('dialog', { name });
            expect(within(dialog).getByText(`${index + 1} / 9`)).toBeInTheDocument();
            if ([1, 2, 3, 8].includes(index)) expect(dialog).toHaveTextContent('Navigációs menü');
            fireEvent.click(within(dialog).getByRole('button', { name: index === 8 ? 'Készen vagyok!' : 'Tovább' }));
        }
        await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
        await waitFor(() => expect(trigger).toHaveFocus());
    });
    it('opens a named Hungarian dialog, traps Tab and restores the trigger after Escape', async () => {
        openPage();
        const trigger = screen.getByRole('button', { name: 'Az oldal bemutatása' });
        trigger.focus();
        fireEvent.click(trigger);
        const dialog = await screen.findByRole('dialog', { name: 'Üdvözlünk a LanguageApp-ban!' });
        expect(dialog).toHaveAttribute('aria-modal', 'true');
        expect(dialog).toHaveAccessibleDescription(/legfontosabb funkciókat/);
        const next = within(dialog).getByRole('button', { name: 'Tovább' });
        const skip = within(dialog).getByRole('button', { name: 'Kihagyás' });
        const title = within(dialog).getByRole('heading', { name: 'Üdvözlünk a LanguageApp-ban!' });
        await waitFor(() => expect(title).toHaveFocus());
        fireEvent.keyDown(title, { key: 'Tab' });
        expect(skip).toHaveFocus();
        fireEvent.keyDown(skip, { key: 'Tab', shiftKey: true });
        expect(next).toHaveFocus();
        trigger.focus();
        expect(title).toHaveFocus();
        fireEvent.keyDown(next, { key: 'Escape' });
        await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
        await waitFor(() => expect(trigger).toHaveFocus());
    });

    it('advances to the next real target and can be skipped and restarted', async () => {
        openPage();
        const trigger = screen.getByRole('button', { name: 'Az oldal bemutatása' });
        trigger.focus();
        fireEvent.click(trigger);
        const welcome = await screen.findByRole('dialog', { name: 'Üdvözlünk a LanguageApp-ban!' });
        fireEvent.click(within(welcome).getByRole('button', { name: 'Tovább' }));
        const classroom = await screen.findByRole('dialog', { name: 'Osztálytermek' });
        expect(within(classroom).getByRole('heading', { name: 'Osztálytermek' })).toHaveFocus();
        expect(within(classroom).getByRole('button', { name: 'Vissza' })).toBeInTheDocument();
        fireEvent.click(within(classroom).getByRole('button', { name: 'Kihagyás' }));
        await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
        await waitFor(() => expect(trigger).toHaveFocus());
        expect(localStorage.getItem('hasSeenTutorial_tour-test')).toBe('true');
        fireEvent.click(trigger);
        expect(await screen.findByRole('dialog', { name: 'Üdvözlünk a LanguageApp-ban!' })).toBeInTheDocument();
    });

    it('completes every step and returns focus to the original trigger', async () => {
        openPage();
        const trigger = screen.getByRole('button', { name: 'Az oldal bemutatása' });
        trigger.focus(); fireEvent.click(trigger);
        for (const name of ['Üdvözlünk a LanguageApp-ban!', 'Osztálytermek', 'Közösség', 'Szótár HUB',
            'Intelligens ajánló', 'Tanulási Útvonal', 'Teljesítmény', 'Ranglista']) {
            const dialog = await screen.findByRole('dialog', { name });
            fireEvent.click(within(dialog).getByRole('button', { name: 'Tovább' }));
        }
        const final = await screen.findByRole('dialog', { name: 'Profil és Beállítások' });
        fireEvent.click(within(final).getByRole('button', { name: 'Készen vagyok!' }));
        await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
        await waitFor(() => expect(trigger).toHaveFocus());
    });
});
