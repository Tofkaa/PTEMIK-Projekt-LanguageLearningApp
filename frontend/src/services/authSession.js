import { CanceledError } from 'axios';

let epoch = 0;
let remember = false;
let cookieQueue = Promise.resolve();
const listeners = new Set();
const validToken = value => typeof value === 'string' && value.trim() !== '' && value !== 'null' && value !== 'undefined';

export const getAccessToken = () => {
    const temporary = sessionStorage.getItem('token');
    const persistent = localStorage.getItem('token');
    return validToken(temporary) ? temporary : validToken(persistent) ? persistent : null;
};
export const getAuthEpoch = () => epoch;
export const authSnapshot = () => ({ epoch, token: getAccessToken() });
export const isCurrentAuth = snapshot => snapshot.epoch === epoch;
export const staleAuthError = () => new CanceledError('A munkamenet időközben megváltozott.');
export const requireCurrentAuth = snapshot => { if (!isCurrentAuth(snapshot)) throw staleAuthError(); };
export const subscribeAuth = listener => { listeners.add(listener); return () => listeners.delete(listener); };
const notify = type => listeners.forEach(listener => listener(type));
const removeTokens = () => { localStorage.removeItem('token'); sessionStorage.removeItem('token'); };

export const beginAuthentication = (rememberMe = false) => {
    epoch += 1;
    remember = rememberMe;
    removeTokens();
    notify('changing');
    return epoch;
};
export const endSession = (expectedEpoch = epoch) => {
    if (expectedEpoch !== epoch) return false;
    epoch += 1;
    removeTokens();
    notify('ended');
    return true;
};
export const storeAccessToken = (token, expectedEpoch) => {
    requireCurrentAuth({ epoch: expectedEpoch });
    if (!validToken(token)) throw new Error('Érvénytelen access token érkezett.');
    if (getAccessToken()) remember = validToken(sessionStorage.getItem('token')) ? false : true;
    const target = remember ? localStorage : sessionStorage;
    (remember ? sessionStorage : localStorage).removeItem('token');
    target.setItem('token', token);
};

/** Client-side correlation only; actual JWT authenticity is always verified by the backend. */
export const tokenSessionId = token => {
    try {
        const payload = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
        const sid = JSON.parse(atob(payload)).sid;
        return typeof sid === 'string' ? sid : null;
    } catch { return null; }
};

/** Cookie-changing auth requests run in user-intent order, including an in-flight older login. */
export const queueAuthCookieOperation = (expectedEpoch, operation, allowStaleStart = false) => {
    const result = cookieQueue.then(() => {
        if (!allowStaleStart) requireCurrentAuth({ epoch: expectedEpoch });
        return operation();
    });
    cookieQueue = result.catch(() => {});
    return result;
};

window.addEventListener?.('storage', event => {
    if (event.key !== 'token' && event.key !== null) return;
    if (event.storageArea && event.storageArea !== localStorage) return;
    if (validToken(sessionStorage.getItem('token'))) return;
    const oldSid = tokenSessionId(event.oldValue || '');
    const newSid = tokenSessionId(event.newValue || '');
    if (event.oldValue === event.newValue && event.key !== null) return;
    if (oldSid && oldSid === newSid) return;
    epoch += 1;
    remember = true;
    notify('external');
});
