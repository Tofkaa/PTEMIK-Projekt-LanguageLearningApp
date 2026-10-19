import { useId } from 'react';
import { Form } from 'react-bootstrap';
import AssignmentTimingFields from './AssignmentTimingFields';

/** Draft accommodations are sent with the assignment, never saved by a separate request. */
export default function AssignmentInitialTiming({ students, value, onChange, timeLimitMinutes, saving }) {
    const id = useId();
    return <details className="mt-4 border-top pt-3">
        <summary className="fw-bold">Egyéni időbeállítások ({Object.keys(value).length} tanuló)</summary>
        <p className="mt-3">Szükség esetén már kiosztás előtt adj többletidőt, időkorlát-mentességet vagy külön beadási határidőt. Az időkeret és a beadási határidő két külön érték. A többi tanulóra a közös beállítások érvényesek.</p>
        {students.length === 0 && <p>Nincs elfogadott tanuló az osztályban.</p>}
        {students.map(student => {
            const name = student.studentName || student.name || student.email || 'Tanuló';
            const selected = Object.hasOwn(value, student.userId);
            return <div key={student.userId} className="border rounded p-3 mb-3">
                <Form.Check id={`${id}-${student.userId}`} label={`Egyéni időbeállítás: ${name}${student.email ? ` (${student.email})` : ''}`}
                    checked={selected} disabled={saving} onChange={e => {
                        const next = { ...value };
                        if (e.target.checked) next[student.userId] = { extraMinutes: 0, unlimited: false, availableUntilOverride: '' };
                        else delete next[student.userId];
                        onChange(next);
                    }} />
                {selected && <fieldset className="mt-3" disabled={saving}>
                    <legend className="fs-6">{name} időbeállításai</legend>
                    <AssignmentTimingFields value={value[student.userId]} timeLimitMinutes={timeLimitMinutes} saving={saving}
                        onChange={timing => onChange({ ...value, [student.userId]: timing })} />
                </fieldset>}
            </div>;
        })}
    </details>;
}
