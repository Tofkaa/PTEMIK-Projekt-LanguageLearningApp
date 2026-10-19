import { useId } from 'react';
import { Form } from 'react-bootstrap';

/** Shared fields for draft and already published assignment timing. Dates remain local until submission. */
export default function AssignmentTimingFields({ value, onChange, timeLimitMinutes, saving = false }) {
    const id = useId();
    return <>
        <Form.Check id={`${id}-unlimited`} label="Időkorlát-mentesség" checked={value.unlimited} disabled={saving}
            onChange={e => onChange({ ...value, unlimited: e.target.checked })} className="mb-3" />
        <Form.Group controlId={`${id}-extra`} className="mb-3">
            <Form.Label>Többletidő (perc)</Form.Label>
            <Form.Control type="number" min="0" step="1" value={value.extraMinutes} required
                disabled={value.unlimited || !Number(timeLimitMinutes)} readOnly={saving}
                onChange={e => onChange({ ...value, extraMinutes: e.target.value })} />
        </Form.Group>
        <Form.Group controlId={`${id}-deadline`}>
            <Form.Label>Egyéni beadási határidő (helyi idő)</Form.Label>
            <Form.Control type="datetime-local" value={value.availableUntilOverride} readOnly={saving}
                aria-describedby={`${id}-deadline-help`} onChange={e => onChange({ ...value, availableUntilOverride: e.target.value })} />
            <Form.Text id={`${id}-deadline-help`}>Üresen hagyva a közös határidő érvényes. A többletidő önmagában nem tolja ki ezt a határidőt.</Form.Text>
        </Form.Group>
    </>;
}
