import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';

/** Remaining visible time, rather than wall-clock time. Focus is never taken or removed. */
export default function AchievementNotification({ awards, onClose, onPresented }) {
    const [hovered, setHovered] = useState(false);
    const [focused, setFocused] = useState(false);
    const [hidden, setHidden] = useState(document.hidden);
    const [visible, setVisible] = useState(() => typeof IntersectionObserver === 'undefined');
    const presented = useRef(false);
    const remaining = useRef(10000);
    const card = useRef(null);
    const closeRef = useRef(onClose);
    useEffect(() => { closeRef.current = onClose; }, [onClose]);
    useEffect(() => {
        if (typeof IntersectionObserver === 'undefined') return;
        const observer = new IntersectionObserver(entries => setVisible(entries.some(entry => entry.isIntersecting)));
        observer.observe(card.current);
        return () => observer.disconnect();
    }, []);
    useEffect(() => {
        if (!visible || hidden || presented.current) return;
        presented.current = true;
        onPresented?.();
    }, [visible, hidden, onPresented]);
    useEffect(() => {
        const changed = () => setHidden(document.hidden);
        document.addEventListener('visibilitychange', changed);
        return () => document.removeEventListener('visibilitychange', changed);
    }, []);
    useEffect(() => {
        if (hovered || focused || hidden || !visible) return;
        const started = performance.now();
        const timer = setTimeout(() => closeRef.current(), Math.max(0, remaining.current));
        return () => { clearTimeout(timer); remaining.current = Math.max(0, remaining.current - (performance.now() - started)); };
    }, [hovered, focused, hidden, visible]);
    const dismiss = () => {
        if (card.current?.contains(document.activeElement)) {
            document.getElementById('main-content')?.focus({ preventScroll: true });
        }
        onClose();
    };
    const summary = awards.length === 1 ? `Új kitüntetés: ${awards[0].name}` : `${awards.length} új kitüntetést szereztél!`;
    return <section ref={card} className="container my-3" aria-label="Új kitüntetések"
        onMouseEnter={() => setHovered(true)} onMouseLeave={() => setHovered(false)}
        onFocusCapture={() => setFocused(true)}
        onBlurCapture={event => { if (!event.currentTarget.contains(event.relatedTarget)) setFocused(false); }}>
        <div className="border border-info rounded-4 p-3 bg-body text-body shadow-sm">
            <p className="fw-bold mb-2"><span aria-hidden="true">🏆 </span>{summary}</p>
            <ul className="list-unstyled mb-2">{awards.map(award => <li key={award.userAchievementId}>
                <span aria-hidden="true">{award.iconUrl} </span>
                {awards.length > 1 && <strong>{award.name}: </strong>}{award.description}
            </li>)}</ul>
            <p className="small text-body-secondary">A kitüntetések bármikor elérhetők a profilodon.</p>
            <div className="d-flex flex-wrap gap-2">
                <Link className="btn btn-outline-info" to="/profile#achievements" onClick={onClose}>Megtekintés</Link>
                <button type="button" className="btn btn-outline-secondary" onClick={dismiss}>Értesítés bezárása</button>
            </div>
        </div>
    </section>;
}
