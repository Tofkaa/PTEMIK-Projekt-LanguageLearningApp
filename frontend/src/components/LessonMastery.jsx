import { useState } from 'react';
import { Button, Form } from 'react-bootstrap';
import MasteryStars from './MasteryStars';
import { LEVEL_NAMES } from '../utils/mastery';

export default function LessonMastery({ lesson, onReplay }) {
    const available = lesson.availableDifficulties || [];
    const completed = lesson.completedDifficulties || [];
    const [level, setLevel] = useState(() => available.find(item => !completed.includes(item)) || available[0] || '');
    const [expanded, setExpanded] = useState(false);
    const detailsId = `mastery-details-${lesson.lessonId}`;
    if (!available.length) return null;
    return <div className="mastery-panel">
        <div className="mastery-overview">
            <div className="d-flex align-items-center gap-2">
                <MasteryStars lesson={lesson} compact />
                <span className="small" aria-hidden="true">{completed.length}/{available.length}</span>
                {lesson.mastered && <span className="visually-hidden">Leckemastery teljesítve.</span>}
            </div>
            <Button variant="link" size="sm" className="mastery-toggle" aria-expanded={expanded}
                aria-controls={detailsId} aria-label={`Szintek és gyakorlás: ${lesson.title}`}
                onClick={() => setExpanded(value => !value)}>
                Szintek és gyakorlás <span aria-hidden="true">{expanded ? '▴' : '▾'}</span>
            </Button>
        </div>
        <div className="visually-hidden" role="progressbar"
            aria-valuemin={0} aria-valuemax={available.length} aria-valuenow={completed.length}
            aria-label={`${lesson.title}: teljesített nehézségi szintek`}
            aria-valuetext={`${available.length} elérhető szintből ${completed.length} teljesítve`} />
        <div id={detailsId} hidden={!expanded} className="mastery-details">
        <MasteryStars lesson={lesson} selectedLevel={level} onSelect={lesson.completed ? setLevel : undefined} />
        {!lesson.completed && <p className="small text-body-secondary mb-0">A szintválasztás a lecke első sikeres teljesítése után érhető el.</p>}
        {lesson.completed && <Form onSubmit={event => { event.preventDefault(); onReplay(level); }}>
            <Form.Group controlId={`replay-level-${lesson.lessonId}`}>
                <Form.Label className="small">Ismétlés szintje: {lesson.title}</Form.Label>
                <Form.Select value={level} onChange={event => setLevel(event.target.value)} size="sm"
                    aria-describedby={`replay-help-${lesson.lessonId}`}>
                    {available.map(item => <option key={item} value={item}>
                        {LEVEL_NAMES[item]} – {completed.includes(item) ? 'teljesítve' : 'új csillag'}
                    </option>)}
                </Form.Select>
            </Form.Group>
            <p id={`replay-help-${lesson.lessonId}`} className="small text-body-secondary mt-2 mb-2">
                Válaszd ki, melyik elérhető szintet szeretnéd gyakorolni.
            </p>
            <Button type="submit" size="sm" variant="outline-info" aria-label={`Gyakorlás a választott szinten: ${lesson.title}`}>
                Gyakorlás a választott szinten
            </Button>
        </Form>}
        </div>
    </div>;
}
