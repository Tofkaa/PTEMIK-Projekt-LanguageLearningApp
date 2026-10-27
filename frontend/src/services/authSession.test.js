import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { authSnapshot, beginAuthentication, endSession, getAccessToken, isCurrentAuth,
    queueAuthCookieOperation, storeAccessToken, subscribeAuth } from './authSession';

const token = sid => `test.${btoa(JSON.stringify({ sid }))}.signature`;
beforeEach(() => { endSession(); localStorage.clear(); sessionStorage.clear(); });
afterEach(() => { endSession(); localStorage.clear(); sessionStorage.clear(); });

it('uses session storage consistently when legacy stores both contain a token', () => {
    localStorage.setItem('token','old-remembered');sessionStorage.setItem('token','current-tab');
    expect(getAccessToken()).toBe('current-tab');
    storeAccessToken('refreshed',authSnapshot().epoch);
    expect(sessionStorage.getItem('token')).toBe('refreshed');expect(localStorage.getItem('token')).toBeNull();
});
it.each(['null','undefined',''])('ignores invalid legacy token %s instead of masking a valid remembered session', value => {
    sessionStorage.setItem('token',value);localStorage.setItem('token','valid');expect(getAccessToken()).toBe('valid');
});
it('does not invalidate in-flight work when another tab refreshes the same remembered session', () => {
    const epoch=beginAuthentication(true);const old=token('same');storeAccessToken(old,epoch);
    const snapshot=authSnapshot();
    const renewed=old.replace('signature','renewed-signature');localStorage.setItem('token',renewed);
    window.dispatchEvent(new StorageEvent('storage',{key:'token',oldValue:old,newValue:renewed,storageArea:localStorage}));
    expect(isCurrentAuth(snapshot)).toBe(true);
});
it('invalidates previous work when another tab replaces a remembered identity', () => {
    const epoch=beginAuthentication(true);const old=token('a');storeAccessToken(old,epoch);const snapshot=authSnapshot();
    localStorage.setItem('token',token('b'));
    const changed=vi.fn();const unsubscribe=subscribeAuth(changed);
    window.dispatchEvent(new StorageEvent('storage',{key:'token',oldValue:old,newValue:token('b'),storageArea:localStorage}));
    unsubscribe();expect(isCurrentAuth(snapshot)).toBe(false);expect(changed).toHaveBeenCalledExactlyOnceWith('external');
});
it('does not overwrite an independent session-storage tab on another tabs storage event', () => {
    const epoch=beginAuthentication(false);storeAccessToken(token('tab'),epoch);const snapshot=authSnapshot();
    window.dispatchEvent(new StorageEvent('storage',{key:'token',oldValue:token('a'),newValue:token('b'),storageArea:localStorage}));
    expect(isCurrentAuth(snapshot)).toBe(true);expect(getAccessToken()).toBe(token('tab'));
});
it('skips an obsolete queued login without sending its cookie-changing request', async () => {
    const epoch=beginAuthentication();const obsolete=vi.fn();
    const result=queueAuthCookieOperation(epoch,obsolete).catch(error=>error.code);
    beginAuthentication();expect(await result).toBe('ERR_CANCELED');expect(obsolete).not.toHaveBeenCalled();
});
