import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { clearInvitation, invitationPath, postLoginDestination, readInvitation, rememberInvitation, resolveInvitation } from './invitationIntent';
const intent = { kind: 'friend', token: 'a'.repeat(43) };
beforeEach(() => { sessionStorage.clear(); localStorage.clear(); }); afterEach(() => vi.restoreAllMocks());
it('keeps a validated per-tab intent through authentication and clears explicitly', () => {
    expect(rememberInvitation(intent)).toBe(true); expect(readInvitation()).toEqual(intent);
    expect(postLoginDestination()).toBe(`/invite/friend#token=${intent.token}`);
    clearInvitation(); expect(postLoginDestination()).toBe('/dashboard');
});
it('never accepts arbitrary redirect URLs or unrecognized kinds/tokens', () => {
    for (const value of [{ kind: '//evil.test', token: intent.token }, { kind: 'friend', token: 'https://evil.test/' }, { redirect: 'https://evil.test' }]) {
        expect(rememberInvitation(value)).toBe(false); expect(invitationPath(value)).toBeNull();
    }
    sessionStorage.setItem('pendingInvitation', JSON.stringify({ ...intent, redirect: '//evil.test' }));
    expect(postLoginDestination()).toBe(`/invite/friend#token=${intent.token}`);
});
it('prioritizes an explicit link and never falls back from a malformed explicit fragment', () => {
    rememberInvitation(intent);
    expect(resolveInvitation('friend', '#token=bad')).toBeNull();
    expect(resolveInvitation('friend', `#token=${intent.token}&token=${intent.token}`)).toBeNull();
    expect(resolveInvitation('friend', `#token=${intent.token}&redirect=//evil.test`)).toBeNull();
    expect(resolveInvitation('classroom', '')).toBeNull();
    const next = { kind: 'classroom', token: 'b'.repeat(43) };
    expect(resolveInvitation('classroom', `#token=${next.token}`)).toEqual(next);
});
it('handles malformed or unavailable storage without throwing', () => {
    sessionStorage.setItem('pendingInvitation', '{'); expect(readInvitation()).toBeNull();
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => { throw new Error('blocked'); });
    expect(rememberInvitation(intent)).toBe(false);
});

it('never resumes an invitation from discontinued cross-tab storage', () => {
    localStorage.setItem('registrationInvitation', JSON.stringify({ intent, email: 'new@example.test', createdAt: Date.now() }));
    expect(postLoginDestination()).toBe('/dashboard');
    clearInvitation();
    expect(localStorage.getItem('registrationInvitation')).toBeNull();
});
