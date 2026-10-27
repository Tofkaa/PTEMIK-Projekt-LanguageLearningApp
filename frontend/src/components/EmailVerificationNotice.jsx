import { useRef, useEffect, useState } from 'react';
import { Alert, Button } from 'react-bootstrap';
import { useAuth } from '../context/AuthContext';
import api from '../services/api';
import LearningFeedback from './LearningFeedback';

export default function EmailVerificationNotice() {
    const { user, setUser } = useAuth();
    const [busy, setBusy] = useState(false);
    const [feedback, setFeedback] = useState(null);
    const [error, setError] = useState('');
    const active = useRef(false);
    useEffect(() => { active.current = true; return () => { active.current = false; }; }, []);
    const run = async action => {
        setBusy(true); setError(''); setFeedback(null);
        try { await action(); }
        catch (failure) { if (active.current) setError(failure.response?.data?.message || 'A kérés nem sikerült. Próbáld újra.'); }
        finally { if (active.current) setBusy(false); }
    };
    const resend = () => run(async () => {
        await api.post('/users/me/verification-email', null, { skipGlobalError: true });
        if (active.current) setFeedback({ type: 'success', msg: 'A megerősítő levél küldését elindítottuk. Nyisd meg a legújabb levélben lévő linket; a korábbi linkek már nem érvényesek. A levél megérkezése eltarthat egy kicsit.' });
    });
    const check = () => run(async () => {
        const { data } = await api.get('/users/me', { skipGlobalError: true });
        if (!active.current) return;
        if (data.verified) setUser(current => current?.userId === user.userId ? { ...current, ...data } : current);
        else setFeedback({ type: 'info', msg: 'Az e-mail-cím még nincs megerősítve. Kattints a levélben található linkre, majd ellenőrizd újra.' });
    });
    return <div>
        <Alert variant="warning" className="py-2 px-3 border-warning rounded-3">
            <strong className="d-block">Kérjük, erősítsd meg az e-mail címedet!</strong>
            <p className="small mb-2">A megerősítő levelet ide küldjük: {user.email}. A Google-/Discord-belépés hozzákapcsolásához is szükséges a megerősítés.</p>
            <div className="d-flex flex-wrap gap-2">
                <Button variant="warning" disabled={busy} onClick={resend}>Megerősítő e-mail újraküldése</Button>
                <Button variant="warning" disabled={busy} onClick={check}>Már megerősítettem – állapot ellenőrzése</Button>
            </div>
        </Alert>
        <LearningFeedback label="E-mail-megerősítés" error={error} feedback={feedback} />
    </div>;
}
