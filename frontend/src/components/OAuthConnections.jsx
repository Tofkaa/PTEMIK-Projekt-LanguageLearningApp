import { useEffect, useRef, useState } from 'react';
import { Button, Form, Spinner } from 'react-bootstrap';
import api from '../services/api';
import LearningFeedback from './LearningFeedback';

const labels = { google: 'Google', discord: 'Discord' };
const openOAuthLink = url => window.location.assign(url);

export default function OAuthConnections({ initialError = '', success = '' }) {
    const [providers, setProviders] = useState(null);
    const [pending, setPending] = useState(null);
    const [code, setCode] = useState('');
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState(initialError);
    const codeRef = useRef(null);
    const active = useRef(false);
    const previousPending = useRef(null);
    const requestRef = useRef(null);
    useEffect(() => {
        active.current = true;
        api.get('/oauth-links', { skipGlobalError: true }).then(({ data }) => {
            if (active.current) setProviders(data);
        }).catch(() => { if (active.current) setError('A kapcsolt belépési módok betöltése nem sikerült. Töltsd újra az oldalt.'); });
        return () => { active.current = false; };
    }, []);
    useEffect(() => {
        if (pending) codeRef.current?.focus();
        else if (previousPending.current) requestRef.current?.focus();
        previousPending.current = pending;
    }, [pending]);
    const perform = async action => {
        setBusy(true); setError('');
        try { await action(); }
        catch (failure) { setError(failure.response?.data?.message || 'Az összekapcsolás nem sikerült. Próbáld újra.'); }
        finally { setBusy(false); }
    };
    const request = provider => perform(async () => {
        const { data } = await api.post('/oauth-links/request', { provider }, { skipGlobalError: true });
        if (active.current) { setPending(data); setCode(''); }
    });
    const verify = event => {
        event.preventDefault();
        return perform(async () => {
            const { data } = await api.post('/oauth-links/verify', { code }, { skipGlobalError: true });
            if (active.current) openOAuthLink(data.authorizationUrl);
        });
    };
    const cancel = () => perform(async () => {
        await api.post('/oauth-links/cancel', null, { skipGlobalError: true });
        if (active.current) { setPending(null); setCode(''); }
    });
    return <section className="mb-5 pb-4 border-bottom border-secondary" aria-labelledby="oauth-connections-title">
        <h2 id="oauth-connections-title" className="h5 text-info">Kapcsolt belépési módok</h2>
        <p>Google- vagy Discord-belépést adhatsz ehhez a fiókodhoz. A profilod és az előrehaladásod megmarad.</p>
        <LearningFeedback label="Fiókösszekapcsolás" error={error}
            feedback={success ? { type: 'success', msg: success } : null} />
        {!providers && !error && <p role="status">Belépési módok betöltése…</p>}
        <ul className="list-unstyled">
            {providers?.map(({ provider, linked }) => <li key={provider} className="d-flex flex-wrap align-items-center gap-3 mb-3">
                <span>{labels[provider]}: {linked ? 'hozzákapcsolva' : 'nincs hozzákapcsolva'}</span>
                {!linked && !pending && <Button ref={provider === providers.find(item => !item.linked)?.provider ? requestRef : undefined} variant="outline-info"
                    disabled={busy} onClick={() => request(provider)}>{labels[provider]} hozzákapcsolása</Button>}
            </li>)}
        </ul>
        {pending && <Form onSubmit={verify} style={{ maxWidth: '32rem' }}>
            <p id="oauth-link-code-help">Az összekapcsolási kódot ide küldtük: {pending.email}. A kód 15 percig érvényes, legfeljebb öt próbálkozással.
                A kód után válaszd ki a hozzákapcsolandó {labels[pending.provider]}-fiókot, majd külön erősítsd meg az összekapcsolást.</p>
            <Form.Group controlId="oauth-link-code" className="mb-3">
                <Form.Label>Összekapcsolási kód</Form.Label>
                <Form.Control ref={codeRef} value={code} onChange={event => setCode(event.target.value)}
                    inputMode="numeric" autoComplete="one-time-code" pattern="[0-9]{6}" maxLength={6} required
                    aria-describedby="oauth-link-code-help" />
            </Form.Group>
            <div className="d-flex flex-wrap gap-2">
                <Button type="submit" disabled={busy}>{busy && <Spinner size="sm" aria-hidden="true" />} Tovább a szolgáltatóhoz</Button>
                <Button variant="outline-secondary" disabled={busy} onClick={cancel}>Összekapcsolás megszakítása</Button>
            </div>
        </Form>}
    </section>;
}
