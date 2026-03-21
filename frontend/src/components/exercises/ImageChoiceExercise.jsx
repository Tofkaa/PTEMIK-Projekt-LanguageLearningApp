import { Row, Col, Card } from 'react-bootstrap';

/**
 * ImageChoiceExercise Component
 * * Renders an image-based multiple-choice exercise.
 * Features an "Elevated Surface" UI design for dark mode, utilizing 
 * semi-transparent backgrounds and dynamic borders to ensure UI elements 
 * remain distinct and interactive against dark themes.
 */
const ImageChoiceExercise = ({ exercise, currentAnswer, onAnswer, disabled }) => {
    const { content, imageUrl } = exercise;
    
    return (
        <div>
            {/* --- Image Display Section --- */}
            {imageUrl && (
                <div className="mb-4 text-center">
                    <img 
                        src={imageUrl} 
                        alt="Exercise visual context" 
                        className="img-fluid rounded-4 shadow-sm border border-info border-opacity-25 p-1"
                        style={{ maxHeight: '250px', objectFit: 'contain', backgroundColor: 'rgba(0,0,0,0.5)' }}
                    />
                </div>
            )}

            {/* --- Options Grid --- */}
            <Row className="g-3 justify-content-center">
                {content?.options?.map((option, idx) => (
                    <Col xs={12} sm={6} md={4} key={idx}>
                        <Card 
                            onClick={() => !disabled && onAnswer(option)}
                            className={`h-100 text-center transition-all ${
                                currentAnswer === option 
                                    ? 'border-info bg-info bg-opacity-25' 
                                    : 'border-secondary text-light' 
                            } ${disabled ? 'opacity-75' : ''} hover-border-info`}
                            style={{ 
                                cursor: disabled ? 'default' : 'pointer',
                                transform: (!disabled && currentAnswer !== option) ? 'scale(1)' : 'scale(0.98)',
                                // UI Enhancement: Elevated surface for unselected cards in dark mode
                                backgroundColor: currentAnswer === option ? '' : 'rgba(255, 255, 255, 0.05)',
                                borderWidth: currentAnswer === option ? '2px' : '1px'
                            }}
                        >
                            <Card.Body className="d-flex align-items-center justify-content-center">
                                <h5 className="mb-0 fw-bold">{option}</h5>
                            </Card.Body>
                        </Card>
                    </Col>
                ))}
            </Row>
        </div>
    );
};

export default ImageChoiceExercise;