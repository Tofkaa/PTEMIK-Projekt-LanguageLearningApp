import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';
import AdminRoute from '../AdminRoute';
import Settings from '../../pages/Settings';

const state = vi.hoisted(() => ({ user: { userId: 'admin', name: 'Admin', email: 'admin@example.test', role: 'ADMIN', verified: true, authProvider: 'LOCAL' } }));
vi.mock('../../context/AuthContext', () => ({ useAuth: () => ({ user: state.user, setUser: vi.fn(), logout: vi.fn() }) }));
vi.mock('../../services/api', () => ({ default: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() } }));
vi.mock('../OAuthConnections', () => ({ default: () => null }));
afterEach(() => { cleanup(); state.user = { userId: 'admin', name: 'Admin', email: 'admin@example.test', role: 'ADMIN', verified: true, authProvider: 'LOCAL' }; });

describe('account and administrator policy', () => {
    it('requires a confirmed admin before showing administration', () => {
        state.user.verified = false;
        render(<MemoryRouter><AdminRoute><h1>Admin content</h1></AdminRoute></MemoryRouter>);
        expect(screen.queryByRole('heading', { name: 'Admin content' })).not.toBeInTheDocument();
        expect(screen.getByText(/előbb meg kell erősítened az e-mail címedet/)).toBeInTheDocument();
    });
    it('retains the existing role restriction for a confirmed student', () => {
        state.user.role = 'STUDENT';
        render(<MemoryRouter><AdminRoute><h1>Admin content</h1></AdminRoute></MemoryRouter>);
        expect(screen.queryByRole('heading', { name: 'Admin content' })).not.toBeInTheDocument();
    });
    it('lets a confirmed administrator enter', () => {
        render(<MemoryRouter><AdminRoute><h1>Admin content</h1></AdminRoute></MemoryRouter>);
        expect(screen.getByRole('heading', { name: 'Admin content' })).toBeInTheDocument();
    });
    it('explains and disables administrator self deletion in privacy settings', () => {
        render(<MemoryRouter><Settings /></MemoryRouter>);
        fireEvent.click(screen.getByRole('tab', { name: 'Adatvédelem' }));
        const remove = screen.getByRole('button', { name: /Fiók végleges törlése/ });
        expect(remove).toBeDisabled();
        expect(remove).toHaveAccessibleDescription(/másik admin/);
    });
});
