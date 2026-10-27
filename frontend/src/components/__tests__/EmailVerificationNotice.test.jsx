import { act, cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import Settings from '../../pages/Settings';
import EmailVerificationNotice from '../EmailVerificationNotice';
import api from '../../services/api';
const auth=vi.hoisted(() => ({ user:{ userId:'self',email:'own@example.test',verified:false },setUser:vi.fn() }));
vi.mock('../../context/AuthContext',()=>({ useAuth:()=>auth }));
vi.mock('../../services/api',()=>({ default:{ post:vi.fn(),get:vi.fn() } }));
beforeEach(()=>{vi.resetAllMocks();auth.user={ userId:'self',email:'own@example.test',verified:false };});
afterEach(cleanup);
it('offers self-service resend and announces delivery without changing verification',async()=>{
    api.post.mockResolvedValue({});render(<EmailVerificationNotice />);
    fireEvent.click(screen.getByRole('button',{name:'Megerősítő e-mail újraküldése'}));
    expect(await screen.findByRole('status')).toHaveTextContent('küldését elindítottuk');
    expect(api.post).toHaveBeenCalledExactlyOnceWith('/users/me/verification-email',null,{skipGlobalError:true});expect(auth.setUser).not.toHaveBeenCalled();
});
it('announces throttling without losing the resend control',async()=>{
    api.post.mockRejectedValue({response:{data:{message:'Egy perc elteltével kérheted újra.'}}});render(<EmailVerificationNotice />);
    fireEvent.click(screen.getByRole('button',{name:'Megerősítő e-mail újraküldése'}));
    expect(await screen.findByRole('alert',{name:'E-mail-megerősítés: hiba'})).toHaveTextContent('Egy perc');
    expect(screen.getByRole('button',{name:'Megerősítő e-mail újraküldése'})).toBeEnabled();
});
it('refreshes verification from the server when email was confirmed in another tab',async()=>{
    api.get.mockResolvedValue({data:{...auth.user,verified:true}});render(<EmailVerificationNotice />);
    fireEvent.click(screen.getByRole('button',{name:'Már megerősítettem – állapot ellenőrzése'}));
    await screen.findByRole('button',{name:'Megerősítő e-mail újraküldése'});
    await act(async()=>{});expect(auth.setUser).toHaveBeenCalledTimes(1);
    const update=auth.setUser.mock.calls[0][0];expect(update(auth.user).verified).toBe(true);
    expect(update({userId:'other',verified:false})).toEqual({userId:'other',verified:false});
});
it('explains when the link has not yet been used',async()=>{
    api.get.mockResolvedValue({data:auth.user});render(<EmailVerificationNotice />);
    fireEvent.click(screen.getByRole('button',{name:'Már megerősítettem – állapot ellenőrzése'}));
    expect(await screen.findByRole('status')).toHaveTextContent('még nincs megerősítve');expect(auth.setUser).not.toHaveBeenCalled();
});

it('explains the verification prerequisite in settings without a failing provider-list request',()=>{
    render(<MemoryRouter initialEntries={[{pathname:'/settings',state:{settingsTab:'security'}}]}><Settings /></MemoryRouter>);
    expect(screen.getByText(/Belépési mód hozzáadásához előbb erősítsd meg/)).toBeInTheDocument();
    expect(api.get).not.toHaveBeenCalled();
    expect(screen.queryByText(/A kapcsolt belépési módok betöltése nem sikerült/)).not.toBeInTheDocument();
});
