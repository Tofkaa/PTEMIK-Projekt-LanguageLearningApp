const KEY = 'pendingInvitation';
const TOKEN = /^[A-Za-z0-9_-]{43}$/;
const valid = value => value && ['friend', 'classroom'].includes(value.kind)
    && typeof value.token === 'string' && TOKEN.test(value.token);

export function readInvitation() {
    try { const value = JSON.parse(sessionStorage.getItem(KEY)); return valid(value) ? { kind: value.kind, token: value.token } : null; }
    catch { return null; }
}
export function rememberInvitation(value) {
    if (!valid(value)) return false;
    try { sessionStorage.setItem(KEY, JSON.stringify({ kind: value.kind, token: value.token })); return true; }
    catch { return false; }
}
export function clearInvitation() {
    try { sessionStorage.removeItem(KEY); } catch { /* Storage may be disabled. */ }
    // Remove the discontinued cross-tab continuation left by earlier local builds.
    try { localStorage.removeItem('registrationInvitation'); } catch { /* Storage may be disabled. */ }
}
export function invitationPath(value) {
    return valid(value) ? `/invite/${value.kind}#token=${value.token}` : null;
}
export function postLoginDestination() { return invitationPath(readInvitation()) || '/dashboard'; }
export function resolveInvitation(kind, hash) {
    if (!['friend', 'classroom'].includes(kind)) return null;
    if (hash) {
        const params = new URLSearchParams(hash.replace(/^#/, ''));
        const value = { kind, token: params.get('token') };
        return params.getAll('token').length === 1 && [...params.keys()].every(key => key === 'token') && valid(value) ? value : null;
    }
    const pending = readInvitation();
    return pending?.kind === kind ? pending : null;
}
