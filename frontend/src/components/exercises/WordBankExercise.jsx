import { useState, useEffect } from 'react';
import { Button } from 'react-bootstrap';

/**
 * WordBankExercise Component
 * Renders a word bank interactive exercise.
 * Users click available words to form a sentence, which updates the parent's answer state.
 * * @param {Object} props - Component props
 * @param {Object} props.data - The content object from the backend containing the question and wordBank array
 * @param {Function} props.onAnswer - Callback function to update the parent component's current answer
 */
const WordBankExercise = ({ data, onAnswer }) => {
    const [availableWords, setAvailableWords] = useState([]);
    const [selectedWords, setSelectedWords] = useState([]);

    // Initialize and shuffle words whenever the data (new question) changes
    useEffect(() => {
        if (data && data.wordBank) {
            // Simple array shuffle so the correct answer isn't in obvious order
            const shuffled = [...data.wordBank].sort(() => Math.random() - 0.5);
            setAvailableWords(shuffled);
            setSelectedWords([]); // Reset selected words for the new question
            onAnswer(''); // Clear the parent's current answer state
        }
    }, [data, onAnswer]);

    /**
     * Moves a word from the available pool to the constructed sentence.
     */
    const handleSelectWord = (word, index) => {
        const newSelected = [...selectedWords, word];
        setSelectedWords(newSelected);
        
        const newAvailable = [...availableWords];
        newAvailable.splice(index, 1);
        setAvailableWords(newAvailable);
        
        // Update the parent component with the current sentence string
        onAnswer(newSelected.join(' '));
    };

    /**
     * Removes a word from the constructed sentence and puts it back in the available pool.
     */
    const handleDeselectWord = (word, index) => {
        const newAvailable = [...availableWords, word];
        setAvailableWords(newAvailable);
        
        const newSelected = [...selectedWords];
        newSelected.splice(index, 1);
        setSelectedWords(newSelected);
        
        // Update the parent component
        onAnswer(newSelected.join(' '));
    };

    return (
        <div className="word-bank-container mt-4">
            {/* 1. Constructed Sentence Area (Top) */}
            <div 
                className="selected-area border-bottom border-secondary mb-5 pb-3 d-flex flex-wrap gap-2 justify-content-center align-items-center" 
                style={{ minHeight: '60px' }}
            >
                {selectedWords.length === 0 && (
                    <span className="text-muted fst-italic">Kattints a szavakra a mondat építéséhez...</span>
                )}
                {selectedWords.map((word, idx) => (
                    <Button 
                        key={`sel-${idx}`} 
                        variant="info" 
                        className="fw-bold text-dark rounded-pill px-4 shadow-sm" 
                        onClick={() => handleDeselectWord(word, idx)}
                    >
                        {word}
                    </Button>
                ))}
            </div>

            {/* 2. Available Words Area (Bottom) */}
            <div className="available-area d-flex flex-wrap gap-3 justify-content-center">
                {availableWords.map((word, idx) => (
                    <Button 
                        key={`avail-${idx}`} 
                        variant="outline-light" 
                        className="rounded-pill px-4 fw-bold shadow-sm" 
                        onClick={() => handleSelectWord(word, idx)}
                    >
                        {word}
                    </Button>
                ))}
            </div>
        </div>
    );
};

export default WordBankExercise;