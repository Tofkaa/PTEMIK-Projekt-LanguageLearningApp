import { StrictMode } from 'react';
import { cleanup, render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import GuestRoute from '../GuestRoute';
import OAuth2RedirectHandler from '../OAuth2RedirectHandler';

const auth=vi.hoisted(()=>({ user:null,completeOAuthLogin:vi.fn() }));
vi.mock('../../context/AuthContext',()=>({useAuth:()=>auth}));
beforeEach(()=>{auth.user=null;auth.completeOAuthLogin.mockReset();auth.completeOAuthLogin.mockResolvedValue();localStorage.clear();});
afterEach(()=>{cleanup();localStorage.clear();window.history.replaceState({}, '', '/');});

it('permits login when a leftover token exists but no authenticated profile is available',()=>{
    localStorage.setItem('token','unverified-or-stale');
    render(<MemoryRouter><GuestRoute><h1>Belépés</h1></GuestRoute></MemoryRouter>);
    expect(screen.getByRole('heading',{name:'Belépés'})).toBeInTheDocument();
});
it('handles the existing OAuth callback once under StrictMode',async()=>{
    render(<StrictMode><MemoryRouter initialEntries={['/oauth2/redirect#code=new-code']}><Routes>
        <Route path="/oauth2/redirect" element={<OAuth2RedirectHandler/>}/>
        <Route path="/dashboard" element={<h1>Dashboard</h1>}/>
    </Routes></MemoryRouter></StrictMode>);
    expect(await screen.findByRole('heading',{name:'Dashboard'})).toBeInTheDocument();
    expect(auth.completeOAuthLogin).toHaveBeenCalledExactlyOnceWith('new-code');
});

it('rejects and scrubs a legacy JWT-query callback without consuming that JWT', async () => {
    window.history.replaceState({}, '', '/oauth2/redirect?token=legacy-secret');
    render(<MemoryRouter initialEntries={['/oauth2/redirect?token=legacy-secret']}><Routes>
        <Route path="/oauth2/redirect" element={<OAuth2RedirectHandler />} />
        <Route path="/login" element={<h1>Login</h1>} />
    </Routes></MemoryRouter>);
    expect(await screen.findByRole('heading', { name: 'Login' })).toBeInTheDocument();
    expect(auth.completeOAuthLogin).not.toHaveBeenCalled(); expect(window.location.search).toBe('');
    expect(localStorage.getItem('token')).toBeNull();
});
