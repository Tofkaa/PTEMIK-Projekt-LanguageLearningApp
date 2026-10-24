import { afterAll, afterEach, beforeAll, describe, expect, it, vi } from 'vitest';
import { act, cleanup, render, screen, waitFor } from '@testing-library/react';
import { readFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import AchievementsSection from '../profile/AchievementSection';
import RecentResultsSection from '../profile/RecentResultsSection';
import api from '../../services/api';
import NavigationBar from '../NavigationBar';
import GlobalErrorToast from '../GlobalErrorToast';
import { MemoryRouter } from 'react-router-dom';

vi.mock('../../services/api', () => ({ default: { get: vi.fn() } }));
vi.mock('../../context/AuthContext', () => ({ useAuth: () => ({ user: { userId: 'badge-test', name: 'Teszt', activeCourseCode: 'en', role: 'STUDENT' } }) }));
vi.mock('../../context/NotificationContext', () => ({ useNotifications: () => ({ notifications: { dueVocabularyCount: 20, pendingFriends: 12 } }) }));
vi.mock('../ThemeToggle', () => ({ default: () => null }));

const stylesheet = document.createElement('style');
beforeAll(() => {
    // Vitest stubs CSS imports by default, including ?raw in this configuration.
    // Read the shipped styles so a missing stylesheet cannot silently pass.
    const require = createRequire(import.meta.url);
    const bootstrapCss = readFileSync(require.resolve('bootstrap/dist/css/bootstrap.min.css'), 'utf8');
    const themeCss = readFileSync(require.resolve('../../index.css'), 'utf8');
    const appCss = readFileSync(require.resolve('../../custom.css'), 'utf8');
    stylesheet.textContent = `${bootstrapCss}\n${themeCss}\n${appCss}`;
    document.head.append(stylesheet);
});
afterEach(() => { cleanup(); vi.clearAllMocks(); localStorage.clear(); });
afterAll(() => { stylesheet.remove(); document.documentElement.removeAttribute('data-bs-theme'); });

// jsdom does not resolve CSS variables. Resolve them from its stylesheet cascade;
// these checks cover flat colours/alpha/opacity, not layout, shadows or browser rendering.
function variable(element, name) {
    for (let node = element; node; node = node.parentElement) {
        const value = getComputedStyle(node).getPropertyValue(name).trim();
        if (value) return value;
    }
    return '';
}
function resolve(element, value) {
    return value.replace(/var\((--[\w-]+)(?:,\s*([^()]+))?\)/g,
        (_, name, fallback) => resolve(element, variable(element, name) || fallback || ''));
}
function rgba(value) {
    if (value === 'transparent' || !value) return [0, 0, 0, 0];
    if (/^#[0-9a-f]{3}$/i.test(value)) value = `#${[...value.slice(1)].map(c => c + c).join('')}`;
    if (/^#[0-9a-f]{6}$/i.test(value)) return [...value.slice(1).match(/../g).map(c => parseInt(c, 16)), 1];
    const match = value.match(/^rgba?\(([^)]+)\)$/);
    if (!match) throw new Error(`Unsupported colour in contrast fixture: ${value}`);
    const channels = match[1].split(/[,\s/]+/).filter(Boolean).map(Number);
    return [...channels.slice(0, 3), channels[3] ?? 1];
}
function blend(front, back) {
    return [...front.slice(0, 3).map((c, i) => c * front[3] + back[i] * (1 - front[3])), 1];
}
function background(element) {
    if (!element) return [255, 255, 255, 1];
    return blend(rgba(resolve(element, getComputedStyle(element).backgroundColor)), background(element.parentElement));
}
function foreground(element) {
    const value = resolve(element, getComputedStyle(element).color);
    return value && value !== 'inherit' ? rgba(value) : foreground(element.parentElement);
}
function luminance(rgb) {
    const linear = rgb.slice(0, 3).map(c => c / 255).map(c => c <= 0.04045 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4);
    return linear[0] * 0.2126 + linear[1] * 0.7152 + linear[2] * 0.0722;
}
function ratio(a, b) {
    const values = [luminance(a), luminance(b)].sort((x, y) => y - x);
    return (values[0] + 0.05) / (values[1] + 0.05);
}
function textContrast(element) {
    let back = background(element);
    let front = blend(foreground(element), back);
    for (let node = element; node; node = node.parentElement) {
        const opacity = Number.parseFloat(getComputedStyle(node).opacity || '1');
        if (opacity < 1) {
            const outside = background(node.parentElement);
            front = blend([...front.slice(0, 3), opacity], outside);
            back = blend([...back.slice(0, 3), opacity], outside);
        }
    }
    return ratio(front, back);
}

describe.each(['light', 'dark'])('%s theme colour regressions', theme => {
    function surface(children) {
        document.documentElement.setAttribute('data-bs-theme', theme);
        return render(<div className="bg-dark p-4">{children}</div>);
    }

    it('keeps text, filled badges, buttons and selected menu items above 4.5:1', () => {
        surface(<>
            {['primary', 'info', 'warning', 'success', 'danger', 'secondary', 'muted'].map(colour => <span key={colour} data-testid={`text-${colour}`} className={`text-${colour}`}>Szöveg</span>)}
            {['primary', 'secondary', 'info', 'warning', 'success', 'danger'].map(colour => <span key={colour} data-testid={`badge-${colour}`} className={`badge bg-${colour}`}>Állapot</span>)}
            {['primary', 'secondary', 'info', 'warning', 'success', 'danger', 'outline-info', 'outline-danger', 'outline-secondary'].map(colour => <button key={colour} data-testid={`button-${colour}`} className={`btn btn-${colour}`}>Művelet</button>)}
            <div className="dropdown-menu-dark"><button data-testid="selected-course" className="dropdown-item bg-primary text-white">Kiválasztott nyelv</button></div>
        </>);
        for (const element of document.querySelectorAll('[data-testid]')) {
            expect(textContrast(element), `${theme}: ${element.dataset.testid}`).toBeGreaterThanOrEqual(4.5);
        }
    });

    it('keeps primary hover and active palettes readable without falling back to Bootstrap blue', () => {
        surface(<button className="btn btn-primary">Mentés</button>);
        const button = screen.getByRole('button', { name: 'Mentés' });
        for (const state of ['hover', 'active']) {
            const front = rgba(resolve(button, variable(button, `--bs-btn-${state}-color`)));
            const back = rgba(resolve(button, variable(button, `--bs-btn-${state}-bg`)));
            const border = rgba(resolve(button, variable(button, `--bs-btn-${state}-border-color`)));
            expect(front[3]).toBe(1);
            expect(back[3]).toBe(1);
            expect(border[3]).toBe(1);
            expect(ratio(front, back), `${theme}: ${state} text`).toBeGreaterThanOrEqual(4.5);
            expect(ratio(border, background(button.parentElement)), `${theme}: ${state} boundary`).toBeGreaterThanOrEqual(3);
        }
    });

    it('keeps control text readable and its border palette above 3:1', () => {
        surface(<>
            <input data-testid="field" className="form-control border-secondary bg-secondary bg-opacity-25" defaultValue="Érték" />
            <select data-testid="select" className="form-select border-0 bg-secondary text-light"><option>Kiválasztott érték</option></select>
        </>);
        for (const element of document.querySelectorAll('[data-testid]')) {
            expect(textContrast(element), `${theme}: ${element.dataset.testid}`).toBeGreaterThanOrEqual(4.5);
            // jsdom's shorthand/important border cascade is incomplete. Check the
            // actual stylesheet token here; visible border application needs a browser.
            const border = rgba(resolve(element, variable(element, '--app-control-border')));
            const outside = background(element.parentElement);
            expect(border[3], `${theme}: control border token must exist`).toBe(1);
            expect(ratio(blend(border, outside), outside), `${theme}: control border palette`).toBeGreaterThanOrEqual(3);
        }
    });

    it('keeps the description of an unearned achievement readable', async () => {
        api.get.mockResolvedValue({ data: [{ achievementId: 'locked', name: 'Kitartó tanuló', description: 'Tanulj öt egymást követő napon.', isUnlocked: false, iconUrl: '🏆' }] });
        surface(<AchievementsSection />);
        const description = await screen.findByText('Tanulj öt egymást követő napon.');
        expect(textContrast(description)).toBeGreaterThanOrEqual(4.5);
        expect(screen.getByText('– még nem szerezted meg')).toBeInTheDocument();
    });

    it('keeps two-digit navbar counters unbroken and reserves their width in the flex layout', () => {
        surface(<MemoryRouter><NavigationBar /></MemoryRouter>);
        for (const count of ['20', '12']) {
            const badge = screen.getByText(count);
            const style = getComputedStyle(badge);
            expect(style.whiteSpace).toBe('nowrap');
            expect(style.overflowWrap).toBe('normal');
            expect(style.flexShrink).toBe('0');
            expect(style.maxWidth).toBe('none');
            expect(badge).not.toHaveClass('position-absolute');
        }
    });

    it('keeps the current-player accent readable on the highlighted leaderboard row', () => {
        surface(<table className="table table-dark"><tbody><tr className="table-active">
            <th scope="row"><span className="text-info">Saját név</span></th>
        </tr></tbody></table>);
        const accent = foreground(screen.getByText('Saját név'));
        // The real browser paints this Bootstrap active-row surface using an inset shadow.
        const activeSurface = rgba(theme === 'light' ? '#e2e3e5' : '#373b3e');
        expect(ratio(accent, activeSurface)).toBeGreaterThanOrEqual(4.5);
    });

    it('keeps friend codes and level labels intact rather than wrapping each character', () => {
        surface(<><span className="badge community-code">ABC-12</span>
            <span className="badge leaderboard-level">11. szint</span></>);
        for (const element of document.querySelectorAll('.community-code, .leaderboard-level')) {
            expect(getComputedStyle(element).whiteSpace).toBe('nowrap');
            expect(getComputedStyle(element).overflowWrap).toBe('normal');
            expect(getComputedStyle(element).flexShrink).toBe('0');
        }
    });

    it('keeps inactive classroom tabs readable on the page background', () => {
        surface(<nav className="custom-dark-tabs" style={{ backgroundColor: theme === 'light' ? '#f8f9fa' : '#212529' }}>
            <button className="nav-link">Statisztika és haladás</button>
        </nav>);
        expect(textContrast(screen.getByRole('button', { name: 'Statisztika és haladás' }))).toBeGreaterThanOrEqual(4.5);
    });

    it('keeps the heatmap scores readable on success and danger surfaces', () => {
        surface(<table className="table table-dark classroom-heatmap"><tbody><tr>
            <td className="bg-success text-light"><div>90%</div></td>
            <td className="bg-danger text-light"><div>20%</div></td>
        </tr></tbody></table>);
        for (const score of ['90%', '20%']) expect(textContrast(screen.getByText(score))).toBeGreaterThanOrEqual(4.5);
    });

    it('keeps persistent errors in document flow instead of covering subsequent controls', async () => {
        surface(<><GlobalErrorToast /><button>Folytatás</button></>);
        const continueButton = screen.getByRole('button', { name: 'Folytatás' });
        continueButton.focus();
        act(() => window.dispatchEvent(new CustomEvent('api-error', { detail: 'Nem sikerült betölteni az adatokat.' })));
        const alert = await screen.findByRole('alert');
        await waitFor(() => expect(alert).toHaveClass('show'));
        expect(continueButton).toHaveFocus();
        expect(getComputedStyle(alert.parentElement).position).toBe('static');
        expect(getComputedStyle(alert).maxWidth).toBe('40rem');
        const body = alert.querySelector('.toast-body');
        // Bootstrap's entering toast is transparent; measure after that transition finishes.
        await waitFor(() => expect(textContrast(body)).toBeGreaterThanOrEqual(4.5));
    });

    it('keeps recent-result dates readable on their actual translucent card surface', async () => {
        api.get.mockResolvedValue({ data: [{ lessonTitle: 'Lecke', score: 80, difficulty: 'EASY', submittedAt: '2026-10-18T12:00:00Z', correctAnswers: 4, totalQuestions: 5 }] });
        surface(<RecentResultsSection />);
        await screen.findByText('Lecke');
        const date = document.querySelector('.text-body-secondary');
        expect(textContrast(date)).toBeGreaterThanOrEqual(4.5);
    });
});
