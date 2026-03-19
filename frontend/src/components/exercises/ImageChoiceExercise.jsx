import { Card, Button, Row, Col } from 'react-bootstrap';

/**
 * ImageChoiceExercise Component
 * Displays a main image and multiple text options for the user to choose from.
 * Perfect for vocabulary building (e.g., Picture of an apple -> Options: "Apple", "Car", "Dog").
 */
const ImageChoiceExercise = ({ exercise, onAnswer, currentAnswer }) => {
    const question = exercise.content?.question || "Válaszd ki a képhez illő szót!";
    const options = exercise.content?.options || [];

    return (
        <div className="text-center w-100">
            <h4 className="text-light mb-4 fw-bold">{question}</h4>
            
            {exercise.imageUrl && (
                <div className="mb-5 d-flex justify-content-center">
                    <img 
                        src={exercise.imageUrl} 
                        alt="Exercise visual" 
                        className="img-fluid rounded-4 shadow-lg border border-secondary"
                        style={{ maxHeight: '250px', objectFit: 'cover' }}
                    />
                </div>
            )}

            <Row className="g-3 justify-content-center">
                {options.map((option, index) => (
                    <Col xs={12} sm={6} key={index}>
                        <Button
                            variant={currentAnswer === option ? "info" : "outline-secondary"}
                            size="lg"
                            className={`w-100 py-3 fw-bold rounded-4 transition-all duration-300 ${
                                currentAnswer === option ? 'text-dark shadow' : 'text-light'
                            }`}
                            onClick={() => onAnswer(option)}
                        >
                            {option}
                        </Button>
                    </Col>
                ))}
            </Row>
        </div>
    );
};


export default ImageChoiceExercise;