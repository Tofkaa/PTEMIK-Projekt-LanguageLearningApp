import { Alert } from 'react-bootstrap';

/** Keep live regions mounted so feedback updates can be announced without moving focus. */
export default function LearningFeedback({ feedback, error, label }) {
    return <div className="learning-feedback w-100">
        <div role={feedback ? 'status' : undefined} aria-live="polite" aria-label={label && feedback ? `${label}: állapot` : undefined} aria-atomic="true">
            {feedback && <Alert role="presentation" variant={feedback.type} className="mt-4 fw-bold text-start">
                {feedback.msg}
            </Alert>}
        </div>
        <div role={error ? 'alert' : undefined} aria-live="assertive" aria-label={label && error ? `${label}: hiba` : undefined} aria-atomic="true">
            {error && <Alert role="presentation" variant="danger" className="mt-4 fw-bold text-start">{error}</Alert>}
        </div>
    </div>;
}
