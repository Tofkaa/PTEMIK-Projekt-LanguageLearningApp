import { useEffect, useState } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { Button, Card, Container, Form } from 'react-bootstrap';
import { useAuth } from '../context/AuthContext';
import api from '../services/api';
import LearningFeedback from '../components/LearningFeedback';
import useStageFocus from '../hooks/useStageFocus';

export default function AccountDeletion() {
    const { user } = useAuth();
    const { hash } = useLocation();
    const token = new URLSearchParams(hash.slice(1)).get('token');
    const [completed, setCompleted] = useState(false);
    const headingRef = useStageFocus(completed);
    if (completed) return <Container className="py-5" style={{ maxWidth: '720px' }}><Card><Card.Body>
        <h1 ref={headingRef} tabIndex={-1} className="h2">A fiókod megszűnt</h1>
        <p>Profiladataid anonimizálódtak, a munkameneteid érvényüket vesztették. Korábbi tanulási eredményeid törölt felhasználóhoz kapcsolva megmaradhatnak.</p>
        <Link className="app-accent-link" to="/login">Vissza a belépéshez</Link>
    </Card.Body></Card></Container>;
    // A different account or link must start with a fresh, unchecked review.
    return <DeletionReview key={`${user?.userId || user?.email || 'guest'}:${token}`} token={token} onDeleted={() => setCompleted(true)} />;
}

function DeletionReview({ token, onDeleted }) {
    const { user, logout } = useAuth();
    const [status, setStatus] = useState('loading');
    const [preview, setPreview] = useState(null);
    const [confirmed, setConfirmed] = useState(false);
    const [error, setError] = useState('');
    const headingRef = useStageFocus(status);

    useEffect(() => {
        if (!user || !token) return;
        let active = true;
        api.post('/users/me/deletion/preview', { token }).then(({ data }) => {
            if (active) { setPreview(data); setStatus('ready'); }
        }).catch(err => {
            if (active) {
                setError(err.response?.data?.message || 'A jóváhagyó link ellenőrzése nem sikerült. Próbáld újra a levél megnyitását, vagy kérj új linket.');
                setStatus('error');
            }
        });
        return () => { active = false; };
    }, [user, token]);

    const finish = async event => {
        event.preventDefault();
        if (!confirmed || status !== 'ready') return;
        setStatus('submitting'); setError('');
        try {
            await api.post('/users/me/deletion/confirm', { token, confirmed: true });
            onDeleted();
            await logout(true);
        } catch (err) {
            setError(err.response?.data?.message || 'A törlés nem sikerült. A kérés nem ismétlődik automatikusan; ellenőrizd a kapcsolatot, majd próbáld újra.');
            setStatus('ready');
        }
    };

    return <Container className="py-5" style={{ maxWidth: '720px' }}>
        <Card><Card.Body>
            <h1 ref={headingRef} tabIndex={-1} className="h2">{status === 'submitting' ? 'Fióktörlés folyamatban…' : 'Fióktörlés áttekintése'}</h1>
            {!token ? <>
                <LearningFeedback error="Hiányzó jóváhagyó link. A Beállításokban kérhetsz új linket." />
                <Link className="app-accent-link" to="/settings">Új jóváhagyó link kérése a Beállításokban</Link>
            </> : !user ? <>
                <p>A linket a kérést indító bejelentkezésből, ugyanabban a böngészőprofilban nyisd meg. Új belépés után új linket kell kérned.</p>
                <Link className="app-accent-link" to="/login">Bejelentkezés</Link>
            </> : <>
                <LearningFeedback error={error} />
                {status === 'loading' && <p role="status">Jóváhagyó link ellenőrzése…</p>}
                {preview && <>
                    <div id="deletion-review-summary">
                        <p>Érintett fiók: <strong>{preview.email}</strong></p>
                        <p>A fiók megszüntetése végleges, nem vonható vissza. Profiladataid anonimizálódnak; a tanulási eredmények törölt felhasználóhoz kapcsolva megmaradhatnak.</p>
                    </div>
                    <p id="deletion-review-deadline">Jóváhagyás lejárata: <time dateTime={preview.expiresAt}>{new Date(preview.expiresAt).toLocaleString('hu-HU')}</time>. Lejárat után új linket kérhetsz a Beállításokban.</p>
                    <Form onSubmit={finish}>
                        <Form.Check id="oauth-deletion-confirmation" type="checkbox" className="my-4"
                            label="Megértettem a fióktörlés következményeit, és kérem a fiókom törlését."
                            aria-describedby="deletion-review-summary deletion-review-deadline"
                            checked={confirmed} disabled={status === 'submitting'} onChange={event => setConfirmed(event.target.checked)} />
                        <div className="d-flex flex-wrap gap-3 align-items-center">
                            <Button variant="danger" type="submit" disabled={!confirmed || status !== 'ready'}>
                                {status === 'submitting' ? 'Törlés folyamatban…' : 'Fiókom törlése'}
                            </Button>
                            {status !== 'submitting' && <Link className="app-accent-link" to="/settings">Mégse, vissza a Beállításokhoz</Link>}
                        </div>
                    </Form>
                </>}
                {!preview && <Link className="app-accent-link" to="/settings">Új jóváhagyó link kérése a Beállításokban</Link>}
            </>}
        </Card.Body></Card>
    </Container>;
}
