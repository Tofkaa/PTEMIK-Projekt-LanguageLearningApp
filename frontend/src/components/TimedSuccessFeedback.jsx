import { useEffect, useRef, useState } from 'react';
import { Alert } from 'react-bootstrap';

/** Count only visible reading time; never remove a focused message automatically. */
export default function TimedSuccessFeedback({ message, onDismiss, focusTarget }) {
    return <div role={message ? 'status' : undefined} aria-live="polite" aria-atomic="true">
        {message && <TimedSuccessCard message={message.text} key={message.id} onDismiss={onDismiss} focusTarget={focusTarget} />}
    </div>;
}

function TimedSuccessCard({ message, onDismiss, focusTarget }) {
    const [hovered, setHovered] = useState(false);
    const [focused, setFocused] = useState(false);
    const [hidden, setHidden] = useState(document.hidden);
    const [visible, setVisible] = useState(() => typeof IntersectionObserver === 'undefined');
    const remaining = useRef(10000);
    const alert = useRef(null);
    const dismiss = useRef(onDismiss);
    useEffect(() => { dismiss.current = onDismiss; }, [onDismiss]);
    useEffect(() => {
        if (typeof IntersectionObserver === 'undefined') return;
        const observer = new IntersectionObserver(entries => setVisible(entries.some(entry => entry.isIntersecting)));
        observer.observe(alert.current);
        return () => observer.disconnect();
    }, []);
    useEffect(() => {
        const changed = () => setHidden(document.hidden);
        document.addEventListener('visibilitychange', changed);
        return () => document.removeEventListener('visibilitychange', changed);
    }, []);
    useEffect(() => {
        if (hovered || focused || hidden || !visible) return;
        const started = performance.now();
        const timer = setTimeout(() => dismiss.current(), remaining.current);
        return () => {
            clearTimeout(timer);
            remaining.current = Math.max(0, remaining.current - (performance.now() - started));
        };
    }, [hovered, focused, hidden, visible]);
    const close = () => {
        if (alert.current?.contains(document.activeElement)) focusTarget?.current?.focus({ preventScroll: true });
        onDismiss();
    };
    return <Alert ref={alert} variant="success" role="presentation"
        className="mt-4 fw-bold text-start" dismissible onClose={close} closeLabel="Sikerüzenet bezárása"
        onMouseEnter={() => setHovered(true)} onMouseLeave={() => setHovered(false)}
        onFocusCapture={() => setFocused(true)}
        onBlurCapture={event => { if (!event.currentTarget.contains(event.relatedTarget)) setFocused(false); }}>
        {message}
    </Alert>;
}
