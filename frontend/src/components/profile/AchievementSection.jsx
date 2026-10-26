import { useState, useEffect, useRef } from 'react';
import { Row, Col, Spinner } from 'react-bootstrap';
import api from '../../services/api'; 
import { formatToLocalDisplay } from '../../utils/dateUtils';

/**
 * AchievementsSection Component
 * * Fetches and displays the user's gamification trophies (both locked and unlocked).
 * Provides visual feedback (grayscale and locks) for achievements yet to be earned,
 * acting as a motivational driver for the user.
 */
const AchievementsSection = () => {
    // --- STATE MANAGEMENT ---
    const [achievements, setAchievements] = useState([]);
    const [isLoading, setIsLoading] = useState(true);
    const [error, setError] = useState(null);
    const section = useRef(null);
    const inFlight = useRef(new Set());
    const alive = useRef(false);
    useEffect(() => { alive.current = true; return () => { alive.current = false; }; }, []);

    // --- DATA FETCHING ---
    useEffect(() => {
        const fetchAchievements = async () => {
            try {
                // Fetch the comprehensive list of achievements with unlock status
                const response = await api.get('/achievements/me');
                setAchievements(response.data);
            } catch (err) {
                console.error("Error fetching achievements:", err);
                setError("Nem sikerült betölteni a kitüntetéseket.");
            } finally {
                setIsLoading(false);
            }
        };

        fetchAchievements();
    }, []);

    useEffect(() => {
        const root = section.current;
        if (!root) return;
        const visible = new Set();
        let observing = true;
        const acknowledge = async () => {
            if (document.hidden) return;
            const ids = [...visible].filter(id => !inFlight.current.has(id));
            if (!ids.length) return;
            ids.forEach(id => inFlight.current.add(id));
            try {
                await api.post('/achievements/acknowledge', { awardIds: ids, read: true });
                if (alive.current) setAchievements(items => items.map(a => ids.includes(a.userAchievementId) ? { ...a, unread: false } : a));
            } catch { /* Keep the new marker; a later visit can retry without interrupting the gallery. */ }
            finally { ids.forEach(id => inFlight.current.delete(id)); }
        };
        const cards = [...root.querySelectorAll('[data-unread-award]')];
        let observer;
        const checkVisible = () => {
            cards.forEach(card => {
                const box = card.getBoundingClientRect();
                if (box.bottom > 0 && box.top < window.innerHeight && box.right > 0 && box.left < window.innerWidth)
                    visible.add(card.dataset.unreadAward);
                else visible.delete(card.dataset.unreadAward);
            });
            acknowledge();
        };
        if (typeof IntersectionObserver !== 'undefined') {
            observer = new IntersectionObserver(entries => {
                if (!observing) return;
                entries.forEach(entry => {
                    if (!entry.target.dataset.unreadAward) return;
                    if (entry.isIntersecting) visible.add(entry.target.dataset.unreadAward);
                    else visible.delete(entry.target.dataset.unreadAward);
                });
                acknowledge();
            });
            cards.forEach(card => observer.observe(card));
        } else {
            checkVisible();
            window.addEventListener('scroll', checkVisible, true);
            window.addEventListener('resize', checkVisible);
        }
        const onVisibility = () => { if (!document.hidden) checkVisible(); };
        document.addEventListener('visibilitychange', onVisibility);
        return () => {
            observing = false;
            observer?.disconnect();
            document.removeEventListener('visibilitychange', onVisibility);
            window.removeEventListener('scroll', checkVisible, true);
            window.removeEventListener('resize', checkVisible);
        };
    }, [achievements]);

    // --- CONDITIONAL RENDERING ---
    if (isLoading) {
        return <div className="text-center p-4"><Spinner animation="border" variant="info" /></div>;
    }

    if (error) {
        return <div className="text-danger small">{error}</div>;
    }

    if (achievements.length === 0) {
        return <div className="text-body-secondary fst-italic">Még nincsenek elérhető kitüntetések a rendszerben.</div>;
    }

    // Sort achievements: Unlocked trophies appear first in the grid
    const sortedAchievements = [...achievements].sort((a, b) => 
        (a.isUnlocked === b.isUnlocked ? 0 : a.isUnlocked ? -1 : 1)
    );

    return (
        <section ref={section} aria-label="Kitüntetéseim">
        <Row className="g-3 mt-2">
            {sortedAchievements.map((ach) => (
                <Col xs={12} md={6} key={ach.achievementId}>
                    {/* Dynamic styling based on the 'isUnlocked' boolean */}
                    <div 
                        data-unread-award={ach.unread ? ach.userAchievementId : undefined}
                        className={`d-flex align-items-center p-3 rounded-4 border transition-all duration-300 h-100 ${
                            ach.isUnlocked 
                            ? 'border-info bg-dark shadow-sm' 
                            : 'border-secondary bg-black bg-opacity-25' 
                        }`}
                    >
                        {/* Trophy Icon (Emoji or Image) */}
                        <div 
                            aria-hidden="true"
                            className="d-flex justify-content-center align-items-center bg-secondary bg-opacity-25 rounded-circle me-3" 
                            style={{ 
                                width: '50px', 
                                height: '50px', 
                                fontSize: '1.8rem',
                                flexShrink: 0,
                                opacity: ach.isUnlocked ? 1 : 0.55,
                                filter: ach.isUnlocked ? 'none' : 'grayscale(100%)' 
                            }}
                        >
                            {ach.iconUrl}
                        </div>

                        {/* Trophy Details */}
                        <div>
                            <h3 className={`h6 fw-bold mb-1 ${ach.isUnlocked ? 'text-info' : 'text-light'}`}>
                                {ach.name} {ach.unread && <span className="badge text-bg-info ms-1">Új</span>} {!ach.isUnlocked && <><span aria-hidden="true" className="ms-1 fs-6">🔒</span><span className="visually-hidden"> – még nem szerezted meg</span></>}
                            </h3>
                            <p className="text-body-secondary mb-0" style={{ fontSize: '0.8rem' }}>
                                {ach.description}
                            </p>
                            
                            {/* Timestamp for earned achievements */}
                            {ach.isUnlocked && ach.achievedAt && (
                                <small className="text-success fw-bold mt-1 d-block" style={{ fontSize: '0.7rem' }}>
                                    Megszerezve: {formatToLocalDisplay(ach.achievedAt, false)}
                                </small>
                            )}
                        </div>
                    </div>
                </Col>
            ))}
        </Row>
        </section>
    );
};

export default AchievementsSection;
