import { useEffect, useId, useRef, useState } from 'react';
import { Button, Form } from 'react-bootstrap';
import { useAuth } from '../context/AuthContext';
import api from '../services/api';
import LearningFeedback from './LearningFeedback';
import TimedSuccessFeedback from './TimedSuccessFeedback';
import { invitationPath } from '../utils/invitationIntent';

export default function InvitationShare(props) {
    const { user } = useAuth();
    return <ShareControls key={`${user?.userId || 'guest'}:${props.kind || 'friend'}:${props.classroomId || ''}`} {...props} />;
}
function ShareControls({ kind = 'friend', classroomId, targetName = 'saját profil' }) {
    const { user } = useAuth();
    const [busy, setBusy] = useState(false);
    const [feedback, setFeedback] = useState(null);
    const [error, setError] = useState('');
    const [copyValue, setCopyValue] = useState('');
    const input = useRef(null);
    const copyButton = useRef(null);
    const feedbackSequence = useRef(0);
    const showSuccess = text => setFeedback({ id: ++feedbackSequence.current, text });
    const alive = useRef(false);
    const id = useId();
    useEffect(() => { alive.current = true; return () => { alive.current = false; }; }, []);
    useEffect(() => { if (copyValue) { input.current?.focus(); input.current?.select(); } }, [copyValue]);
    if (!user || !(user.verified || user.isVerified)) return null;
    if (kind === 'classroom' && (!classroomId || user.role !== 'TEACHER')) return null;
    const endpoint = kind === 'classroom' ? `/invitations/classrooms/${classroomId}` : '/invitations/friend';
    const perform = async operation => {
        if (busy) return;
        setBusy(true); setError(''); setFeedback(null); setCopyValue('');
        try {
            if (operation === 'revoke') {
                await api.delete(endpoint);
                if (alive.current) showSuccess('A meghívólinket visszavontad. A kézi kód, a meglévő kapcsolatok és a korábbi kérelmek megmaradnak.');
                return;
            }
            const { data } = await (operation === 'rotate' ? api.put(endpoint) : api.post(endpoint));
            if (!alive.current) return;
            const path = invitationPath(data);
            if (!path) throw new Error('invalid response');
            const url = new URL(path, window.location.origin).href;
            try {
                if (!navigator.clipboard?.writeText) throw new Error('clipboard unavailable');
                await navigator.clipboard.writeText(url);
                if (alive.current) showSuccess(operation === 'rotate'
                    ? 'Az új linket a vágólapra másoltuk. A régi link már nem használható.' : 'A meghívólinket a vágólapra másoltuk.');
            } catch {
                if (alive.current) {
                    setCopyValue(url);
                    showSuccess(operation === 'rotate'
                        ? 'Az új link elkészült, a régi már nem használható. Másold ki az alábbi mezőből.' : 'A link elkészült. Másold ki az alábbi mezőből.');
                }
            }
        } catch (failure) {
            if (alive.current) setError(failure.response?.data?.message || 'Nem sikerült kezelni a meghívólinket. Próbáld újra!');
        } finally { if (alive.current) setBusy(false); }
    };
    return <div className="my-3 text-start" aria-label={`Meghívólink: ${targetName}`}>
        <Button ref={copyButton} variant="outline-info" disabled={busy} onClick={() => perform('copy')} aria-label={`Meghívólink másolása: ${targetName}`}>
            {busy ? 'Feldolgozás…' : 'Meghívólink másolása'}
        </Button>
        <details className="mt-2">
            <summary>Meghívólink kezelése</summary>
            <p className="small text-body-secondary mt-2">Az újragenerálás vagy visszavonás csak a korábbi linket érvényteleníti. A kézi kód és a meglévő kapcsolatok megmaradnak.</p>
            <div className="d-flex flex-wrap gap-2">
                <Button variant="outline-secondary" disabled={busy} onClick={() => perform('rotate')}>Új link készítése és másolása</Button>
                <Button variant="outline-danger" disabled={busy} onClick={() => perform('revoke')}>Link visszavonása</Button>
            </div>
        </details>
        <LearningFeedback label="Meghívólink" error={error} />
        <TimedSuccessFeedback message={feedback} focusTarget={copyButton}
            onDismiss={() => setFeedback(current => current?.id === feedback?.id ? null : current)} />
        {copyValue && <Form.Group className="mt-2" controlId={id}>
            <Form.Label>Meghívólink kézi másolása</Form.Label>
            <Form.Control ref={input} value={copyValue} readOnly type="text" />
        </Form.Group>}
    </div>;
}
