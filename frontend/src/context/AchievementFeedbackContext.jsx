import { createContext, useCallback, useContext, useEffect, useRef, useState } from 'react';
import { useLocation } from 'react-router-dom';
import { useAuth } from './AuthContext';
import { useNotifications } from './NotificationContext';
import api from '../services/api';
import AchievementNotification from '../components/AchievementNotification';

const noop = () => {};
const Context = createContext({ presentInline: noop });

// The key discards queues, timers and stale request callbacks on account changes.
export function AchievementFeedbackProvider({ children }) {
    const { user } = useAuth();
    return <AccountFeedback key={user?.userId || 'guest'} userId={user?.userId}>{children}</AccountFeedback>;
}

function AccountFeedback({ children, userId }) {
    const location = useLocation();
    const { autoRefreshEnabled = true } = useNotifications() || {};
    const [queue, setQueue] = useState([]);
    const [modalOpen, setModalOpen] = useState(false);
    const [active, setActive] = useState([]);
    const seen = useRef(new Set());
    const alive = useRef(true);
    const version = useRef(0);
    // Also suppress during hydration and final review: no first-frame exam notification.
    const blocked = modalOpen || /^\/assignment\/session\//.test(location.pathname)
        || /^\/lesson\//.test(location.pathname)
        || /^\/vocabulary\/(practice|flashcards)/.test(location.pathname);

    useEffect(() => { alive.current = true; return () => { alive.current = false; }; }, []);

    useEffect(() => {
        const checkModal = () => setModalOpen(Boolean(document.querySelector('[role="dialog"][aria-modal="true"]')));
        const observer = new MutationObserver(checkModal);
        observer.observe(document.body, { subtree: true, childList: true, attributes: true, attributeFilter: ['role', 'aria-modal'] });
        checkModal();
        return () => observer.disconnect();
    }, []);

    const acknowledge = useCallback((awards, read = false) => {
        const awardIds = awards.map(a => a.userAchievementId).filter(Boolean);
        if (!userId || !awardIds.length) return;
        // Failure leaves server pending state intact for the next login/reconnect.
        api.post('/achievements/acknowledge', { awardIds, read }).catch(() => {});
    }, [userId]);

    const presentInline = useCallback(awards => {
        const ids = new Set(awards.map(a => a.userAchievementId));
        awards.forEach(a => seen.current.add(a.userAchievementId));
        setQueue(items => items.filter(a => !ids.has(a.userAchievementId)));
        setActive(items => items.filter(a => !ids.has(a.userAchievementId)));
        acknowledge(awards);
    }, [acknowledge]);

    useEffect(() => {
        if (!userId) return;
        let mounted = true;
        const load = async () => {
            const request = ++version.current;
            try {
                const { data } = await api.get('/achievements/pending');
                if (!mounted || !alive.current || request !== version.current) return;
                // Server is authoritative (also removes awards presented in another tab).
                setQueue(data.filter(a => !seen.current.has(a.userAchievementId)));
            } catch { /* Next refresh/reconnect retries; never interrupt learning. */ }
        };
        if (autoRefreshEnabled) load();
        window.addEventListener('achievement-refresh', load);
        return () => { mounted = false; window.removeEventListener('achievement-refresh', load); };
    }, [userId, autoRefreshEnabled]);

    useEffect(() => {
        if (blocked || active.length || !queue.length || document.hidden) return;
        const batch = queue.filter(a => !seen.current.has(a.userAchievementId));
        if (!batch.length) return;
        batch.forEach(a => seen.current.add(a.userAchievementId));
        setActive(batch);
        setQueue([]);
    }, [blocked, queue, active]);

    // A hidden tab should not consume the card before the user returns.
    useEffect(() => {
        const visible = () => { if (!document.hidden) setQueue(items => [...items]); };
        document.addEventListener('visibilitychange', visible);
        return () => document.removeEventListener('visibilitychange', visible);
    }, []);

    return <Context.Provider value={{ presentInline }}>
        <div className="visually-hidden" role="status" aria-live="polite" aria-atomic="true">
            {!blocked && active.length > 0 ? (active.length === 1 ? `Új kitüntetés: ${active[0].name}` : `${active.length} új kitüntetést szereztél!`) : ''}
        </div>
        {!blocked && active.length > 0 && <AchievementNotification key={active.map(a => a.userAchievementId).join(',')}
            awards={active} onPresented={() => acknowledge(active)} onClose={() => setActive([])} />}
        {children}
    </Context.Provider>;
}

// eslint-disable-next-line react-refresh/only-export-components
export const useAchievementFeedback = () => useContext(Context);
