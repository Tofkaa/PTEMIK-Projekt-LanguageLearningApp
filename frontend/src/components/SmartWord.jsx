import React, { useState } from 'react';
import { OverlayTrigger, Popover, Spinner, Badge } from 'react-bootstrap';
import { vocabularyApi } from '../services/vocabularyApi'; // Ellenőrizd az import útvonalat!

/**
 * Interaktív szó-komponens. Kattintásra lekéri és egy Bootstrap Popoverben 
 * megjeleníti a fordítást, és a háttérben elmenti az SRS adatbázisba.
 * 
 * @param {string} word - Az angol szó (központozás automatikusan eltávolítva)
 * @param {boolean} disabled - Vizsga módban (isTest=true) letiltja a szótárat
 * @param {string} source - Az extra kontextus a backend felé
 */
const SmartWord = ({ word, disabled = false, source = 'LESSON' }) => {
    const [translationData, setTranslationData] = useState(null);
    const [isLoading, setIsLoading] = useState(false);
    const [error, setError] = useState(null);

    const handleToggle = async (show) => {
        // Csak akkor hívjuk az API-t, ha megnyílik a popover és még nincs adat
        if (show && !translationData && !isLoading) {
            setIsLoading(true);
            setError(null);
            
            try {
                // Eltávolítjuk a központozást (pl. "apple," -> "apple")
                const cleanWord = word.replace(/[.,!?]/g, '').trim();
                
                // Mivel az api.jsx kezeli a tokent és a hibákat, itt csak hívunk
                const response = await vocabularyApi.lookupWord(cleanWord, source);
                setTranslationData(response.data);
            } catch (err) {
                // A globális hiba toast úgyis megjelenik (api.jsx miatt), 
                // ide csak egy apró lokalizált visszajelzés kell
                setError("Fordítás nem elérhető.", err);
            } finally {
                setIsLoading(false);
            }
        }
    };

    // Ha vizsga módban vagyunk, egy teljesen sima <span>-t adunk vissza, nulla interakcióval
    if (disabled) {
        return <span>{word}</span>;
    }

    const popover = (
        <Popover id={`popover-${word.replace(/\s+/g, '-')}`} className="shadow-lg">
            <Popover.Header className="d-flex justify-content-between align-items-center bg-primary text-white">
                <strong className="text-capitalize">{word.replace(/[.,!?]/g, '').trim()}</strong>
                {translationData?.newAddition && (
                    <Badge bg="success" className="ms-2">Új</Badge>
                )}
            </Popover.Header>
            <Popover.Body>
                {isLoading ? (
                    <div className="d-flex align-items-center py-2">
                        <Spinner animation="border" size="sm" variant="primary" className="me-2" />
                        <span className="small text-muted">Keresés...</span>
                    </div>
                ) : error ? (
                    <span className="text-danger small fw-bold">{error}</span>
                ) : translationData ? (
                    <div>
                        <div className="fw-bold fs-6 mb-2">{translationData.translation}</div>
                        
                        <div className="d-flex justify-content-between align-items-center pt-2 mt-2 border-top small text-muted">
                            <span title="Spaced Repetition Szint">
                                🧠 Szint: {translationData.srsLevel}/6
                            </span>
                            <span title="Következő ismétlés">
                                📅 {new Date(translationData.nextPracticeAt).toLocaleDateString('hu-HU')}
                            </span>
                        </div>
                    </div>
                ) : null}
            </Popover.Body>
        </Popover>
    );

    return (
        <OverlayTrigger 
            trigger="click" 
            placement="top" 
            overlay={popover} 
            onToggle={handleToggle}
            rootClose // Bárhova máshova kattintva bezáródik
        >
            <span 
                className="text-primary fw-medium" 
                style={{ 
                    cursor: 'pointer', 
                    borderBottom: '1px dashed currentColor',
                    paddingBottom: '1px',
                    transition: 'opacity 0.2s'
                }}
                onMouseOver={(e) => e.target.style.opacity = '0.7'}
                onMouseOut={(e) => e.target.style.opacity = '1'}
                title="Kattints a fordításért"
            >
                {word}
            </span>
        </OverlayTrigger>
    );
};

export default SmartWord;