import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import VocabularyHub from './VocabularyHub';
import api from '../services/api';
vi.mock('../services/api', () => ({ default: { get: vi.fn() } }));
vi.mock('../context/AuthContext', () => ({ useAuth: () => ({ user: { userId: 'student', activeCourseCode: 'en' } }) }));
vi.mock('../context/NotificationContext', () => ({ useNotifications: () => ({ notifications: {} }) }));
const words = [
    { word: 'late', translation: 'késői', nextPracticeAt: '2026-10-26T11:00:00+02:00', srsLevel: 6, mastered: true },
    { word: 'early', translation: 'korai', nextPracticeAt: '2026-10-26T08:30:00Z', srsLevel: 1, mastered: false },
    { word: 'same-time', translation: 'azonos', nextPracticeAt: '2026-10-26T08:30:00Z', srsLevel: 2, mastered: false },
];
beforeEach(() => { vi.clearAllMocks(); api.get.mockResolvedValue({ data: structuredClone(words) }); });
afterEach(cleanup);
const order = () => screen.getAllByRole('rowheader').map(cell => cell.textContent);
async function setup() {
    render(<MemoryRouter><VocabularyHub /></MemoryRouter>);
    return screen.findByRole('combobox', { name: 'Következő ismétlés sorrendje' });
}
it('orders actual instants ascending and descending, preserving ties and selector focus', async () => {
    const select = await setup();
    expect(order()).toEqual(['early', 'same-time', 'late']);
    expect(screen.getByRole('columnheader', { name: 'Következő Ismétlés' })).toHaveAttribute('aria-sort', 'ascending');
    select.focus(); fireEvent.change(select, { target: { value: 'DESC' } });
    expect(order()).toEqual(['late', 'early', 'same-time']); expect(select).toHaveFocus();
    expect(screen.getByRole('columnheader', { name: 'Következő Ismétlés' })).toHaveAttribute('aria-sort', 'descending');
    expect(api.get).toHaveBeenCalledTimes(1);
});
it('combines ordering with the existing text and learning-state filters', async () => {
    const select = await setup(); fireEvent.change(select, { target: { value: 'DESC' } });
    fireEvent.change(screen.getByRole('combobox', { name: 'Tanulási állapot' }), { target: { value: 'LEARNING' } });
    expect(order()).toEqual(['early', 'same-time']);
    fireEvent.change(screen.getByRole('textbox', { name: 'Keresés szó vagy jelentés alapján' }), { target: { value: 'korai' } });
    expect(order()).toEqual(['early']);
    fireEvent.change(screen.getByRole('textbox', { name: 'Keresés szó vagy jelentés alapján' }), { target: { value: 'nincs' } });
    expect(screen.getByText('Nem található szó a megadott szűrési feltételekkel.')).toBeInTheDocument();
});
it('keeps immediately due fallback rows first ascending and last descending', async () => {
    api.get.mockResolvedValue({ data: [...words, { word: 'now', translation: 'most', srsLevel: 0, nextPracticeAt: null }] });
    const select = await setup(); expect(order()[0]).toBe('now');
    fireEvent.change(select, { target: { value: 'DESC' } }); expect(order().at(-1)).toBe('now');
});
