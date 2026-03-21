import { useState, useEffect } from 'react';
import { Row, Col, Spinner } from 'react-bootstrap';
import api from '../../services/api'; 

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

    // --- CONDITIONAL RENDERING ---
    if (isLoading) {
        return <div className="text-center p-4"><Spinner animation="border" variant="info" /></div>;
    }

    if (error) {
        return <div className="text-danger small">{error}</div>;
    }

    if (achievements.length === 0) {
        return <div className="text-light opacity-50 fst-italic">Még nincsenek elérhető kitüntetések a rendszerben.</div>;
    }

    // Sort achievements: Unlocked trophies appear first in the grid
    const sortedAchievements = [...achievements].sort((a, b) => 
        (a.isUnlocked === b.isUnlocked ? 0 : a.isUnlocked ? -1 : 1)
    );

    return (
        <Row className="g-3 mt-2">
            {sortedAchievements.map((ach) => (
                <Col xs={12} md={6} key={ach.achievementId}>
                    {/* Dynamic styling based on the 'isUnlocked' boolean */}
                    <div 
                        className={`d-flex align-items-center p-3 rounded-4 border transition-all duration-300 h-100 ${
                            ach.isUnlocked 
                            ? 'border-info bg-dark shadow-sm' 
                            : 'border-secondary bg-black bg-opacity-25' 
                        }`}
                        style={{ 
                            opacity: ach.isUnlocked ? 1 : 0.5,
                            transform: ach.isUnlocked ? 'scale(1)' : 'scale(0.98)'
                        }}
                    >
                        {/* Trophy Icon (Emoji or Image) */}
                        <div 
                            className="d-flex justify-content-center align-items-center bg-secondary bg-opacity-25 rounded-circle me-3" 
                            style={{ 
                                width: '50px', 
                                height: '50px', 
                                fontSize: '1.8rem',
                                filter: ach.isUnlocked ? 'none' : 'grayscale(100%)' 
                            }}
                        >
                            {ach.iconUrl}
                        </div>

                        {/* Trophy Details */}
                        <div>
                            <h6 className={`fw-bold mb-1 ${ach.isUnlocked ? 'text-info' : 'text-light'}`}>
                                {ach.name} {!ach.isUnlocked && <span className="ms-1 fs-6">🔒</span>}
                            </h6>
                            <p className="text-light opacity-75 mb-0" style={{ fontSize: '0.8rem', lineHeight: '1.2' }}>
                                {ach.description}
                            </p>
                            
                            {/* Timestamp for earned achievements */}
                            {ach.isUnlocked && ach.achievedAt && (
                                <small className="text-success fw-bold mt-1 d-block" style={{ fontSize: '0.7rem' }}>
                                    Megszerezve: {new Date(ach.achievedAt).toLocaleDateString('hu-HU')}
                                </small>
                            )}
                        </div>
                    </div>
                </Col>
            ))}
        </Row>
    );
};

export default AchievementsSection;