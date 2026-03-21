import { Row, Col, Card } from 'react-bootstrap';

/**
 * MultipleChoiceExercise Component
 * * Standard text-based multiple-choice quiz component.
 * Implements accessible, full-width clickable cards with elevated dark-mode styling
 * and visual feedback for the currently selected answer.
 */
const MultipleChoiceExercise = ({ data, currentAnswer, onAnswer, disabled }) => {
    return (
        <Row className="g-3">
            {data?.options?.map((option, idx) => (
                <Col xs={12} key={idx}>
                    <Card 
                        onClick={() => !disabled && onAnswer(option)}
                        className={`text-center transition-all ${
                            currentAnswer === option 
                                ? 'border-info bg-info bg-opacity-25' 
                                : 'border-secondary text-light' 
                        } ${disabled ? 'opacity-75' : ''} hover-border-info`}
                        style={{ 
                            cursor: disabled ? 'default' : 'pointer',
                            // UI Enhancement: Subtle background elevation
                            backgroundColor: currentAnswer === option ? '' : 'rgba(255, 255, 255, 0.05)',
                            borderWidth: currentAnswer === option ? '2px' : '1px'
                        }}
                    >
                        <Card.Body>
                            <h5 className="mb-0 fw-bold">{option}</h5>
                        </Card.Body>
                    </Card>
                </Col>
            ))}
        </Row>
    );
};

export default MultipleChoiceExercise;