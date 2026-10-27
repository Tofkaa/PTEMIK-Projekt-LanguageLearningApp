import { useEffect, useRef, useState } from 'react';
import { Button, Container } from 'react-bootstrap';
import { useNavigate } from 'react-router-dom';
import api from '../services/api';
import LearningFeedback from '../components/LearningFeedback';
import { useAuth } from '../context/AuthContext';

export default function OAuthLinkConfirmation() {
    const { user } = useAuth();
    return <Confirmation key={user?.userId || user?.email || 'guest'} />;
}

function Confirmation() {
    const navigate = useNavigate();
    // Capture once before clearing the address bar; never persist this proof in browser storage.
    const [proof] = useState(() => new URLSearchParams(window.location.hash.slice(1)).get('proof'));
    const [preview, setPreview] = useState(null);
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState('');
    const heading = useRef(null);
    const mounted = useRef(false);
    useEffect(() => {
        mounted.current = true;
        return () => { mounted.current = false; };
    }, []);
    useEffect(() => {
        window.history.replaceState(window.history.state, '', window.location.pathname + window.location.search);
        heading.current?.focus();
        let active = true;
        if (!proof) return;
        api.post('/oauth-links/preview', { proof }, { skipGlobalError: true }).then(({ data }) => {
            if (active) setPreview(data);
        }).catch(failure => {
            if (active) setError(failure.response?.data?.message || 'Az összekapcsolás nem ellenőrizhető. Indíts új kérést a Beállításokban.');
        });
        return () => { active = false; };
    }, [proof]);
    const back = message => {
        if (mounted.current) navigate('/settings', { replace: true, state: { settingsTab: 'security', oauthLinkMessage: message } });
    };
    const confirm = async () => {
        setBusy(true); setError('');
        try {
            await api.post('/oauth-links/confirm', { proof, confirmed: true }, { skipGlobalError: true });
            back('A belépési módot sikeresen hozzákapcsoltuk a fiókodhoz.');
        } catch (failure) { setError(failure.response?.data?.message || 'Az összekapcsolás nem sikerült.'); }
        finally { setBusy(false); }
    };
    const cancel = async () => {
        setBusy(true); setError('');
        try { await api.post('/oauth-links/cancel', null, { skipGlobalError: true }); back('Az összekapcsolást megszakítottad.'); }
        catch { setError('A megszakítás nem sikerült. Próbáld újra.'); }
        finally { setBusy(false); }
    };
    return <Container className="py-5" style={{ maxWidth: '48rem' }}>
        <h1 ref={heading} tabIndex={-1}>Belépési mód összekapcsolása</h1>
        <LearningFeedback label="Fiókösszekapcsolás" error={error || (!proof ? 'A jóváhagyás hiányzik. Indíts új kérést a Beállításokban.' : '')} />
        {proof && !preview && !error && <p role="status">A jóváhagyás ellenőrzése…</p>}
        {preview && <>
            <p>LanguageApp-fiók: <strong>{preview.accountEmail}</strong></p>
            <p>Hozzákapcsolandó {preview.provider === 'google' ? 'Google' : 'Discord'}-fiók: <strong>{preview.providerEmail}</strong></p>
            <p>Csak akkor erősítsd meg, ha mindkét fiók a tiéd. Az e-mail-címed és az előrehaladásod megmarad.</p>
            <Button disabled={busy} onClick={confirm}>Összekapcsolás megerősítése</Button>{' '}
            <Button variant="outline-secondary" disabled={busy} onClick={cancel}>Mégse</Button>
        </>}
        {(!proof || error) && <Button variant="link" onClick={() => back('')}>Vissza a Beállításokhoz</Button>}
    </Container>;
}
