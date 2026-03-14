import { Button, Form } from 'react-bootstrap';

/**
 * MultipleChoiceExercise Component
 * Renders a list of options for the user to select from.
 * * @param {Object} props - Component props
 * @param {Object} props.data - The exercise content containing the question and options array
 * @param {string} props.currentAnswer - The currently selected answer text
 * @param {Function} props.onAnswer - Callback to update the parent component's state
 */
const MultipleChoiceExercise = ({ data, currentAnswer, onAnswer }) => {
    return (
        <div className="multiple-choice-container mt-4">
            <div className="d-flex flex-column gap-3 align-items-center">
                
                {/* Végigiterálunk a backendről kapott opciókon */}
                {data?.options?.map((option, index) => {
                    // OKOS VALIDÁCIÓ: Kezeli a sima String listát (DataSeeder) és az Objektum listát (Admin JSON) is!
                    const optionText = typeof option === 'string' ? option : option.text;
                    const optionId = typeof option === 'string' ? `opt-${index}` : option.id;
                    
                    const isSelected = currentAnswer === optionText;

                    return (
                        <Button
                            key={optionId}
                            variant={isSelected ? 'info' : 'outline-light'}
                            // Ha ki van választva, sötét a betű. Ha nincs, engedjük, hogy a hover magától állítsa a színt!
                            className={`w-100 p-3 fw-bold shadow-sm rounded-4 text-start d-flex align-items-center ${isSelected ? 'text-dark' : ''}`}
                            style={{ maxWidth: '400px', transition: 'all 0.2s ease-in-out' }}
                            onClick={() => onAnswer(optionText)}
                        >
                            <Form.Check
                                type="radio"
                                id={`radio-${optionId}`}
                                checked={isSelected}
                                readOnly // Ezt a gomb onClick-je kezeli, nem kell külön onChange
                                className="me-3"
                                style={{ pointerEvents: 'none' }} 
                            />
                            {/* Nincs több rákényszerített text-light, így hovernél szépen besötétedik a szöveg! */}
                            <span>
                                {optionText}
                            </span>
                        </Button>
                    );
                })}
                
            </div>
        </div>
    );
};

export default MultipleChoiceExercise;