import { useId } from 'react';
import { Row, Col } from 'react-bootstrap';

/** Native radio inputs provide selection state and arrow-key navigation. */
export default function ChoiceOptions({ options = [], currentAnswer, onAnswer, disabled = false, grid = false, lang }) {
    const groupId = useId();
    return <fieldset disabled={disabled} className="exercise-choices">
        <legend className="visually-hidden">Válaszlehetőségek</legend>
        <Row className="g-3 justify-content-center">
            {(options || []).map((option, index) => <Col xs={12} sm={grid ? 6 : 12} md={grid ? 4 : 12} key={index}>
                <label className="exercise-choice" htmlFor={`${groupId}-${index}`}>
                    <input id={`${groupId}-${index}`} lang={lang} type="radio" name={groupId}
                        checked={currentAnswer === option} value={option}
                        disabled={disabled} onChange={() => onAnswer(option)} />
                    <span lang={lang} className="fw-bold">{option}</span>
                    {currentAnswer === option && <span className="exercise-choice-mark" aria-hidden="true">✓</span>}
                </label>
            </Col>)}
        </Row>
    </fieldset>;
}
