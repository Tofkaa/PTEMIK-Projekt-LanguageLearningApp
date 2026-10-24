import { useId, useState } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { useNotifications } from '../context/NotificationContext';
import LearningFeedback from './LearningFeedback';

/** A second way to locate pages, with a consistent control for nonessential live updates. */
export default function AccessibilityFooter() {
    const { user } = useAuth();
    const { autoRefreshEnabled = true, setAutomaticRefresh, refreshNotifications } = useNotifications();
    const { pathname } = useLocation();
    const id = useId();
    const [busy, setBusy] = useState(false);
    const [feedback, setFeedback] = useState('');

    // Keep notification controls reachable during exercises without suggesting navigation away.
    const isExerciseStep = /^\/(lesson|assignment)\//.test(pathname);

    const links = user ? [
        ['/dashboard', 'Dashboard'], ['/classrooms', 'Osztálytermek'], ['/friends', 'Közösség'],
        ['/hub', 'Szótár HUB'], ['/profile', 'Profilom'], ['/settings', 'Beállítások'],
        ...(user.role === 'ADMIN' ? [['/admin', 'Admin Panel']] : []),
    ] : [['/login', 'Bejelentkezés'], ['/register', 'Regisztráció'], ['/forgot-password', 'Elfelejtett jelszó']];
    links.push(['/terms', 'Felhasználási feltételek'], ['/privacy', 'Adatkezelési tájékoztató']);

    const refresh = async () => {
        if (busy) return;
        setBusy(true);
        setFeedback('');
        try {
            const success = await refreshNotifications();
            setFeedback(success ? 'Értesítések frissítve.' : 'A frissítés nem sikerült. Próbáld újra később.');
        } finally { setBusy(false); }
    };

    return <footer className="container mt-4 py-4 border-top border-secondary">
        <details>
            <summary className="fw-bold app-accent-text p-2">{isExerciseStep ? 'Értesítési beállítások' : 'Oldaltérkép és értesítési beállítások'}</summary>
            {!isExerciseStep && <nav aria-label="Oldaltérkép" className="mt-3">
                <ul className="list-unstyled d-flex flex-wrap gap-3 mb-4">
                    {links.map(([path, label]) => <li key={path}><Link className="app-accent-link d-inline-block p-1" to={path}>{label}</Link></li>)}
                </ul>
            </nav>}
            {user && <section aria-labelledby={`${id}-title`}>
                <h2 id={`${id}-title`} className="h6">Értesítések frissítése</h2>
                <div className="form-check form-switch mb-2">
                    <input id={`${id}-automatic`} className="form-check-input" type="checkbox" role="switch"
                        checked={autoRefreshEnabled} aria-describedby={`${id}-help`}
                        onChange={event => setAutomaticRefresh(event.target.checked)} />
                    <label className="form-check-label" htmlFor={`${id}-automatic`}>Értesítésszámlálók automatikus frissítése</label>
                </div>
                <p id={`${id}-help`} className="small text-body-secondary">
                    Kikapcsolva a számlálók nem változnak háttérfrissítés miatt; bármikor frissítheted őket az alábbi gombbal.
                    A saját műveleteid eredménye továbbra is megjelenhet. Ez a beállítás a vizsgák időmérését nem érinti.
                </p>
                <button type="button" className="btn btn-sm btn-outline-info" aria-disabled={busy} onClick={refresh}>
                    Értesítések frissítése most
                </button>
                <LearningFeedback feedback={feedback ? { type: 'info', msg: feedback } : null} />
            </section>}
        </details>
    </footer>;
}
