import { Badge } from 'react-bootstrap';

/**
 * WordBankExercise Component
 * * Interactive sentence-building exercise.
 * ARCHITECTURE NOTE: Uses "Derived State" instead of local `useState`/`useEffect`.
 * The selected and available words are calculated directly from the `currentAnswer` string 
 * provided by the parent. This guarantees a Single Source of Truth, prevents cascading renders, 
 * and cleanly handles duplicate words in the word bank.
 */
const WordBankExercise = ({ data, currentAnswer, onAnswer, disabled }) => {
    
    // 1. Derive selected words array from the parent's string state
    const selectedWords = currentAnswer ? currentAnswer.trim().split(/\s+/) : [];

    // 2. Derive available words by subtracting selected words from the initial options
    const availableWords = [...(data?.options || [])];
    selectedWords.forEach(word => {
        const index = availableWords.indexOf(word);
        if (index > -1) {
            availableWords.splice(index, 1);
        }
    });

    /**
     * Appends a word from the bank to the user's current sentence.
     */
    const handleSelect = (word) => {
        if (disabled) return;
        const newSelected = [...selectedWords, word];
        onAnswer(newSelected.join(' ')); // Emit new state to parent
    };

    /**
     * Removes a specific word from the user's current sentence, returning it to the bank.
     */
    const handleDeselect = (index) => {
        if (disabled) return;
        const newSelected = [...selectedWords];
        newSelected.splice(index, 1);
        onAnswer(newSelected.join(' ')); // Emit new state to parent
    };

    return (
        <div className="text-center">
            {/* --- Answer Construction Area --- */}
            <div 
                className="mb-4 p-3 border-bottom border-info border-opacity-50 d-flex flex-wrap gap-2 justify-content-center align-items-center rounded-top" 
                style={{ minHeight: '80px', backgroundColor: 'rgba(13, 202, 240, 0.05)' }}
            >
                {selectedWords.length === 0 && (
                    <span className="text-secondary fst-italic">Kattints a lenti szavakra a mondatépítéshez...</span>
                )}
                {selectedWords.map((word, idx) => (
                    <Badge 
                        key={`sel-${idx}`} 
                        bg="info" 
                        text="dark" 
                        className="fs-5 p-2 shadow-sm"
                        onClick={() => handleDeselect(idx)} 
                        style={{ cursor: disabled ? 'default' : 'pointer' }}
                    >
                        {word}
                    </Badge>
                ))}
            </div>

            {/* --- Available Words Bank --- */}
            <div className="d-flex flex-wrap gap-2 justify-content-center">
                {availableWords.map((word, idx) => (
                    <Badge 
                        key={`avail-${idx}`} 
                        bg="dark" 
                        border="secondary"
                        className="fs-5 p-3 border border-info border-opacity-25 text-light shadow-sm transition-all hover-border-info"
                        onClick={() => handleSelect(word)}
                        style={{ cursor: disabled ? 'default' : 'pointer' }}
                    >
                        {word}
                    </Badge>
                ))}
            </div>
        </div>
    );
};

export default WordBankExercise;