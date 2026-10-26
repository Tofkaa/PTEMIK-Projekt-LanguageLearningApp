import { useEffect, useMemo, useRef, useState } from 'react';
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom';
import { Button, Card, Container } from 'react-bootstrap';
import { useAuth } from '../context/AuthContext';
import { useNotifications } from '../context/NotificationContext';
import api from '../services/api';
import LearningFeedback from '../components/LearningFeedback';
import useStageFocus from '../hooks/useStageFocus';
import { clearInvitation, rememberInvitation, resolveInvitation } from '../utils/invitationIntent';

const messages = {
    PENDING: 'A kérelmed jóváhagyásra vár.',
    INCOMING: 'A meghívó tulajdonosa már küldött neked barátkérelmet. A közösségi oldalon elfogadhatod.',
    CONNECTED: 'Ez a kapcsolat már létrejött.',
    SELF: 'Ez a saját meghívólinked. Másokkal megosztva használhatod.',
    FORBIDDEN: 'Osztályteremhez csak diák csatlakozhat. Jelenleg más szerepkörű fiókkal vagy belépve.',
    REJECTED: 'Ezt a csatlakozási kérelmet elutasították. Egyeztess az osztályterem tanárával.',
};
export default function Invitation() {
    const { kind } = useParams();
    const { hash } = useLocation();
    const { user } = useAuth();
    const intent = useMemo(() => resolveInvitation(kind, hash), [kind, hash]);
    return <InvitationLanding key={`${user?.userId || 'guest'}:${Boolean(user?.verified || user?.isVerified)}:${kind}:${intent?.token || 'invalid'}`} intent={intent} />;
}
function InvitationLanding({ intent }) {
    const { user, logout, updateProfile } = useAuth();
    const { refreshNotifications, notifications, autoRefreshEnabled } = useNotifications() || {};
    const navigate = useNavigate();
    const verified = Boolean(user?.verified || user?.isVerified);
    const userId = user?.userId;
    const [preview, setPreview] = useState(null);
    const [loading, setLoading] = useState(Boolean(intent && user && verified));
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState('');
    const [feedback, setFeedback] = useState(null);
    const [storageUnavailable, setStorageUnavailable] = useState(false);
    const alive = useRef(false);
    const heading = useStageFocus(!intent ? 'invalid' : !user ? 'guest' : !verified ? 'verify' : loading ? 'loading' : error ? 'error' : preview?.state || 'ready');
    useEffect(() => { alive.current = true; return () => { alive.current = false; }; }, []);
    useEffect(() => {
        if (intent) setStorageUnavailable(!rememberInvitation(intent));
        else clearInvitation();
    }, [intent]);
    useEffect(() => {
        if (!intent || !userId || !verified) return;
        let current = true;
        api.post('/invitations/preview', intent).then(({ data }) => {
            if (current) {
                setPreview(data);
                if (['PENDING', 'INCOMING', 'CONNECTED', 'SELF', 'REJECTED'].includes(data.state)) clearInvitation();
            }
        }).catch(failure => {
            if (current) {
                setError(failure.response?.data?.message || 'Nem sikerült megnyitni a meghívót. Próbáld újra később!');
                if ([400, 404].includes(failure.response?.status)) clearInvitation();
            }
        }).finally(() => { if (current) setLoading(false); });
        return () => { current = false; };
    }, [intent, userId, verified]);
    // Re-read pending requests when the existing notification feed refreshes.
    // This follows the user's automatic-refresh preference and never submits a request.
    useEffect(() => {
        if (preview?.state !== 'PENDING' || !notifications || autoRefreshEnabled === false) return;
        let current = true;
        api.post('/invitations/preview', intent).then(({ data }) => {
            if (current && data.state !== 'PENDING') { setPreview(data); setFeedback(null); }
        }).catch(() => { /* Keep the last known state; explicit refresh reports errors. */ });
        return () => { current = false; };
    }, [notifications, autoRefreshEnabled, preview?.state, intent]);
    const refreshPreview = async () => {
        if (busy) return;
        setBusy(true); setError('');
        try {
            const { data } = await api.post('/invitations/preview', intent);
            if (alive.current) { setPreview(data); setFeedback(null); }
        } catch (failure) {
            if (alive.current) setError(failure.response?.data?.message || 'Nem sikerült frissíteni a kérelem állapotát. Próbáld újra!');
        } finally { if (alive.current) setBusy(false); }
    };
    const leave = () => { clearInvitation(); navigate(user ? '/dashboard' : '/login'); };
    const confirm = async () => {
        if (busy || preview?.state !== 'READY') return;
        setBusy(true); setError('');
        try {
            const { data } = await api.post('/invitations/confirm', intent);
            if (!alive.current) return;
            setPreview(data); clearInvitation();
            setFeedback({ type: 'success', msg: messages[data.state] || 'A művelet befejeződött.' });
            refreshNotifications?.();
        } catch (failure) {
            if (alive.current) setError(failure.response?.data?.message || 'Nem sikerült elküldeni a kérelmet. Próbáld újra!');
        } finally { if (alive.current) setBusy(false); }
    };
    const checkVerification = async () => {
        if (busy) return;
        setBusy(true); setError('');
        try {
            const { data } = await api.get('/users/me');
            if (!alive.current || data.userId !== user.userId) return;
            if (!(data.verified || data.isVerified)) setError('Az e-mail-megerősítés még nem történt meg. Nyisd meg a levélben kapott aktiváló linket!');
            else await updateProfile(data);
        } catch { if (alive.current) setError('Nem sikerült ellenőrizni a megerősítést. Próbáld újra!'); }
        finally { if (alive.current) setBusy(false); }
    };
    const switchAccount = async () => {
        if (busy) return;
        setBusy(true);
        await logout();
        rememberInvitation(intent);
        navigate('/login');
    };
    const classroom = intent?.kind === 'classroom';
    return <Container className="py-5" style={{ maxWidth: '720px' }}>
        <Card className="p-4 bg-body text-body border-secondary">
            <h1 ref={heading} tabIndex={-1} className="h3">{classroom ? 'Osztálytermi meghívó' : 'Barátmeghívó'}</h1>
            {!intent ? <LearningFeedback label="Meghívó" error="Hiányzó vagy hibás meghívólink. Kérj új linket a tulajdonosától!" /> : <>
                {storageUnavailable && <p>Belépés után nyisd meg újra ezt a meghívólinket a folytatáshoz.</p>}
                {!user ? <>
                    <p>A meghívó megtekintéséhez jelentkezz be, vagy hozz létre fiókot. A megnyitás önmagában nem küld kérelmet.</p>
                    <div className="d-flex flex-wrap gap-2">
                        <Link className="btn btn-info" to="/login">Bejelentkezés</Link>
                        <Link className="btn btn-outline-info" to="/register">Regisztráció</Link>
                    </div>
                </> : <>
                    <p>Bejelentkezve: <strong>{user.name}</strong></p>
                    {!verified ? <>
                        <p>Előbb erősítsd meg az e-mail címedet a kapott levélben! Utána itt folytathatod.</p>
                        <Button variant="info" disabled={busy} onClick={checkVerification}>Megerősítés ellenőrzése</Button>
                    </> : loading ? <p role="status">Meghívó betöltése…</p> : preview && <>
                        <h2 className="h4">{preview.title}</h2>
                        <p>{preview.description}</p>
                        {preview.state === 'READY' ? <>
                            <p>{classroom ? 'A csatlakozási kérelmet a tanárnak külön jóvá kell hagynia.' : 'A barátkérelmet a másik felhasználónak külön el kell fogadnia.'}</p>
                            <Button variant="info" disabled={busy} onClick={confirm} aria-busy={busy}>
                                {busy ? 'Kérelem küldése…' : classroom ? 'Csatlakozási kérelem küldése' : 'Barátkérelem küldése'}
                            </Button>
                        </> : !feedback && <p>{messages[preview.state] || 'Ez a meghívó jelenleg nem használható.'}</p>}
                        {preview.state === 'PENDING' && <Button variant="outline-info" disabled={busy} onClick={refreshPreview}>Állapot frissítése</Button>}
                        {preview.state === 'CONNECTED' && classroom && /^[a-f0-9-]{36}$/i.test(preview.classroomId || '')
                            && <Link className="btn btn-outline-info" to={`/classrooms/${preview.classroomId}`} onClick={clearInvitation}>Osztályterem megnyitása</Link>}
                        {['INCOMING', 'CONNECTED'].includes(preview.state) && !classroom && <Link className="btn btn-outline-info" to="/friends" onClick={clearInvitation}>Közösség megnyitása</Link>}
                    </>}
                    <div className="mt-3"><Button variant="outline-secondary" disabled={busy} onClick={switchAccount}>Másik fiókkal folytatom</Button></div>
                </>}
                <LearningFeedback label="Meghívó" error={error} feedback={feedback} />
            </>}
            <div className="mt-4"><Button variant="outline-secondary" onClick={leave} disabled={busy}>Mégse</Button></div>
        </Card>
    </Container>;
}
