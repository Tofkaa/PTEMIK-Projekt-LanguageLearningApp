import { useId, useRef, useState } from 'react';
import { Button, Form, Modal } from 'react-bootstrap';
import LanguageText from './LanguageText';
import DurationText from './DurationText';
import WordBankExercise from './exercises/WordBankExercise';
import MultipleChoiceExercise from './exercises/MultipleChoiceExercise';
import ImageChoiceExercise from './exercises/ImageChoiceExercise';
import LearningFeedback from './LearningFeedback';

/** Edits are drafts until the player submits; no answer-check API or solution is exposed here. */
export default function AssignmentReview({ exercises, answers, onAnswerChange, submitting, timeLeft, timingNotice, error }) {
    const id = useId();
    const [editingId, setEditingId] = useState(null);
    const returnFocus = useRef(null);
    const uniqueExercises = [...new Map(exercises.map(exercise => [exercise.exerciseId, exercise])).values()];
    const editing = uniqueExercises.find(exercise => exercise.exerciseId === editingId);
    const answerFor = exerciseId => answers.find(answer => answer.exerciseId === exerciseId)?.answer || '';
    const change = value => onAnswerChange(editingId, value);

    return <>
        <ol className="list-unstyled">
            {uniqueExercises.map((exercise, index) => {
                const answer = answerFor(exercise.exerciseId);
                return <li key={exercise.exerciseId} className="card bg-dark text-light p-3 mb-3">
                    <h2 className="h5">{index + 1}. feladat</h2>
                    <p><LanguageText text={exercise.content?.question || 'Feladat'}
                        lang={exercise.content?.questionLang || 'hu'} parts={exercise.content?.questionParts} /></p>
                    <dl>
                        <dt>Válaszod</dt>
                        <dd lang={answer ? exercise.content?.answerLang || 'hu' : 'hu'}>{answer || 'Nincs válasz'}</dd>
                    </dl>
                    <Button variant="outline-info" className="align-self-start" aria-disabled={submitting}
                        aria-label={`Válasz javítása: ${index + 1}. feladat`}
                        onClick={event => {
                            if (!submitting) { returnFocus.current = event.currentTarget; setEditingId(exercise.exerciseId); }
                        }}>Válasz javítása</Button>
                </li>;
            })}
        </ol>
        <Modal show={!!editing} onHide={() => { if (!submitting) setEditingId(null); }} centered size="lg"
            onExited={() => requestAnimationFrame(() => {
                // A viewport resize while the body was locked can leave the restored trigger off-screen.
                const trigger = returnFocus.current;
                if (trigger?.isConnected && document.activeElement === trigger) trigger.scrollIntoView?.({ block: 'center', inline: 'nearest' });
            })}
            aria-labelledby={`${id}-title`} scrollable>
            <Modal.Header closeButton closeLabel="Vissza az áttekintéshez">
                <Modal.Title as="h2" className="h4" id={`${id}-title`}>Válasz javítása</Modal.Title>
            </Modal.Header>
            <Modal.Body>
                <div role="status" aria-atomic="true">{timingNotice}</div>
                <LearningFeedback error={error} />
                {timeLeft !== null && <p role="timer" aria-live="off"><DurationText seconds={timeLeft / 1000}
                    overtime={timeLeft < 0} label={timeLeft < 0 ? 'Időtúllépés' : 'Hátralévő idő'} /></p>}
                {editing && <>
                    <h3 className="h5"><LanguageText text={editing.content?.question || 'Feladat'}
                        lang={editing.content?.questionLang || 'hu'} parts={editing.content?.questionParts} /></h3>
                    <p className="small text-body-secondary">A módosítás azonnal bekerül az áttekintésbe. Az idő lejártakor a rendszer az aktuális válaszodat küldi be.</p>
                    {editing.type === 'WORD_BANK' && <WordBankExercise data={editing.content}
                        currentAnswer={answerFor(editingId)} onAnswer={change} disabled={submitting} />}
                    {editing.type === 'MULTIPLE_CHOICE' && <MultipleChoiceExercise data={editing.content}
                        currentAnswer={answerFor(editingId)} onAnswer={change} disabled={submitting} />}
                    {editing.type === 'IMAGE_CHOICE' && <ImageChoiceExercise exercise={editing}
                        currentAnswer={answerFor(editingId)} onAnswer={change} disabled={submitting} />}
                    {editing.type === 'TRANSLATION' && <Form.Group controlId={`${id}-answer`}>
                        <Form.Label id={`${id}-answer-label`} lang="hu">Válaszod</Form.Label>
                        <Form.Control as="textarea" rows={3} lang={editing.content?.answerLang || 'hu'}
                            aria-labelledby={`${id}-answer-label`} value={answerFor(editingId)}
                            disabled={submitting} onChange={event => change(event.target.value)} />
                    </Form.Group>}
                </>}
            </Modal.Body>
            <Modal.Footer><Button variant="info" aria-disabled={submitting}
                onClick={() => { if (!submitting) setEditingId(null); }}>Kész, vissza az áttekintéshez</Button></Modal.Footer>
        </Modal>
    </>;
}
