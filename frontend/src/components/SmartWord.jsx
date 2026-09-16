import React, { useState } from 'react';
import { OverlayTrigger, Popover, Spinner, Badge } from 'react-bootstrap';
import { vocabularyApi } from '../services/vocabularyApi';

/**
* Interactive word component. Retrieves on click and displays the translation in a Bootstrap Popover 
* and saves it to the SRS database in the background.
* 
* @param {string} word - The original word (as it appears in the sentence, even in uppercase)
* @param {string} queryWord - The lowercase, clean word for the API call
* @param {boolean} disabled - Disables the dictionary in test mode (isTest=true)
* @param {string} source - The extra context to the backend
* @param {string} preloadedTranslation - Optional, preloaded translation (JSON hint)
*/
const SmartWord = ({ word, queryWord, disabled = false, source = 'LESSON', preloadedTranslation = null, onWordClick }) => {
    
    // If we received a burned-in hint, we load it immediately
    const [translationData, setTranslationData] = useState(
        preloadedTranslation 
            ? { 
                translation: preloadedTranslation, 
                srsLevel: 'Beépített tipp', 
                newAddition: false,
                isHint: true
              } 
            : null
    );

    const [isLoading, setIsLoading] = useState(false);
    const [error, setError] = useState(null);

    const handleToggle = async (show) => {
        
        if (show && onWordClick) {
           onWordClick();
       }
      
        // If there is already a translation (e.g. because of the hint) or it is loading, we do not call the backend
       if (show && !translationData && !isLoading) {
            setIsLoading(true);
            
            try {
               // We use the queryWord towards the backend, which is already lowercase and clear
                const response = await vocabularyApi.lookupWord(queryWord || word.toLowerCase(), source);
                setTranslationData(response.data);
            } catch (err) {
                setError("Fordítás nem elérhető.", err);
            } finally {
                setIsLoading(false);
            }
        }
    };

    if (disabled) {
        return <span>{word}</span>;
    }

    const popover = (
        <Popover id={`popover-${word.replace(/\s+/g, '-')}`} className="shadow-lg">
            <Popover.Header className="d-flex justify-content-between align-items-center bg-primary text-white">
                <strong className="text-capitalize">{word}</strong>
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
                            <span title="Tudás szint">
                                🧠 Szint: {translationData.srsLevel} {translationData.isHint ? '' : '/6'}
                            </span>
                            
                            {!translationData.isHint && translationData.nextPracticeAt && (
                                <span title="Következő ismétlés">
                                    📅 {new Date(translationData.nextPracticeAt).toLocaleDateString('hu-HU')}
                                </span>
                            )}
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
            rootClose 
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