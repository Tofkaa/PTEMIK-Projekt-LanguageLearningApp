import { useState, useEffect } from 'react';
import { Badge, Spinner } from 'react-bootstrap';
import api from '../../services/api';

const RecentResultsSection = () => {
    const [results, setResults] = useState([]);
    const [isLoading, setIsLoading] = useState(true);
    const [error, setError] = useState(null);

    useEffect(() => {
        const fetchRecentResults = async () => {
            try {
                const response = await api.get('/results/recent');
                setResults(response.data);
            } catch (err) {
                console.error("Hiba az eredmények lekérésekor:", err);
                setError("Nem sikerült betölteni a statisztikákat.");
            } finally {
                setIsLoading(false);
            }
        };

        fetchRecentResults();
    }, []);

    // Helper to get difficulty
    const getDifficultyBadge = (difficulty) => {
        switch (difficulty) {
            case 'HARD': return <Badge bg="danger" className="ms-2">HARD</Badge>;
            case 'MEDIUM': return <Badge bg="warning" text="dark" className="ms-2">MEDIUM</Badge>;
            case 'EASY': return <Badge bg="success" className="ms-2">EASY</Badge>;
            default: return <Badge bg="info" className="ms-2">{difficulty}</Badge>;
        }
    };

    if (isLoading) return <div className="text-center p-3"><Spinner animation="border" variant="info" size="sm" /></div>;
    if (error) return <div className="text-danger small p-3 text-center">{error}</div>;

    if (results.length === 0) {
        return (
            <div className="p-3 border border-secondary rounded-3 bg-black bg-opacity-25 text-center">
                <p className="text-light opacity-50 fst-italic mb-0">
                    Még nincsenek megjeleníthető eredményeid. Teljesíts egy leckét a Dashboardon!
                </p>
            </div>
        );
    }

    return (
        <div className="d-flex flex-column gap-3 mt-2">
            {results.map((result, idx) => (
                <div key={idx} className="d-flex justify-content-between align-items-center p-3 rounded-3 border border-secondary bg-black bg-opacity-25">
                    {/* Left side: Lesson name and date */}
                    <div>
                        <h6 className="fw-bold text-light mb-1">
                            {result.lessonTitle} 
                            {getDifficultyBadge(result.difficulty)}
                        </h6>
                        <small className="text-light opacity-50">
                            {new Date(result.submittedAt).toLocaleDateString('hu-HU', { 
                                year: 'numeric', month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' 
                            })}
                        </small>
                    </div>

                    {/* Right side: points */}
                    <div className="text-end">
                        <h5 className={`fw-bold mb-0 ${result.score >= 60 ? 'text-success' : 'text-danger'}`}>
                            {result.score}%
                        </h5>
                        <small className="text-light opacity-75" style={{ fontSize: '0.75rem' }}>
                            {result.correctAnswers} / {result.totalQuestions} helyes
                        </small>
                    </div>
                </div>
            ))}
        </div>
    );
};

export default RecentResultsSection;