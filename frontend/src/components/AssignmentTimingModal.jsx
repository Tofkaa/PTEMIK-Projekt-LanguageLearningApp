import { useEffect, useId, useState } from 'react';
import { Alert, Button, Form, Modal, Spinner } from 'react-bootstrap';
import { assignmentApi } from '../services/assignmentApi';
import { formatToLocalDisplay, formatToLocalInput, formatToUtcForServer } from '../utils/dateUtils';
import LearningFeedback from './LearningFeedback';
import AssignmentTimingFields from './AssignmentTimingFields';

/** Timing changes are validated on the server; completed attempts retain their snapshots. */
export default function AssignmentTimingModal({ assignment, onHide }) {
    const id = useId();
    const [students, setStudents] = useState([]);
    const [selectedId, setSelectedId] = useState('');
    const [form, setForm] = useState({ extraMinutes: 0, unlimited: false, availableUntilOverride: '' });
    const [loading, setLoading] = useState(true);
    const [saving, setSaving] = useState(false);
    const [error, setError] = useState('');
    const [message, setMessage] = useState('');

    useEffect(() => {
        let active = true;
        assignmentApi.getAccommodations(assignment.assignmentId).then(res => {
            if (active) setStudents(res.data);
        }).catch(() => { if (active) setError('Az egyéni időbeállítások nem tölthetők be. Nyisd meg újra az ablakot.'); })
            .finally(() => { if (active) setLoading(false); });
        return () => { active = false; };
    }, [assignment.assignmentId]);

    const selected = students.find(s => s.userId === selectedId);
    const selectStudent = userId => {
        const student = students.find(s => s.userId === userId);
        setSelectedId(userId);
        setMessage(''); setError('');
        setForm({ extraMinutes: student?.extraMinutes ?? 0, unlimited: student?.unlimited ?? false,
            availableUntilOverride: formatToLocalInput(student?.availableUntilOverride) });
    };

    const save = async event => {
        event.preventDefault();
        if (saving || !selectedId) return;
        setSaving(true); setError(''); setMessage('');
        try {
            const payload = { extraMinutes: form.unlimited ? 0 : Number(form.extraMinutes), unlimited: form.unlimited,
                availableUntilOverride: form.availableUntilOverride ? formatToUtcForServer(form.availableUntilOverride) : null };
            await assignmentApi.updateAccommodation(assignment.assignmentId, selectedId, payload);
            setStudents(previous => previous.map(s => s.userId === selectedId ? { ...s, ...payload } : s));
            setMessage('Az egyéni időbeállítások mentése sikerült. A folyamatban lévő próbálkozás is frissül.');
        } catch (err) {
            setError(err.response?.data?.message || 'Nem sikerült menteni. Elkezdett próbálkozás időkerete és határideje csak hosszabbítható.');
        } finally { setSaving(false); }
    };

    return <Modal show onHide={() => { if (!saving) onHide(); }} aria-labelledby={`${id}-title`} centered>
        <Modal.Header closeButton closeLabel="Időbeállítások bezárása">
            <Modal.Title id={`${id}-title`}>Egyéni időbeállítások: {assignment.title}</Modal.Title>
        </Modal.Header>
        <Form onSubmit={save}>
            <Modal.Body>
                <p>Alap időkeret: <strong>{assignment.timeLimitMinutes ? `${assignment.timeLimitMinutes} perc` : 'Korlátlan'}</strong>.<br />
                    Közös beadási határidő: <strong>{assignment.availableUntil ? formatToLocalDisplay(assignment.availableUntil) : 'Nincs'}</strong>.</p>
                <Alert variant="info">Az időkeret és a beadási határidő külön beállítás. A korlátlan időkeret nem törli a beadási határidőt. Elkezdett próbálkozás csak hosszabbítható; a már beadott dolgozatok nem változnak.</Alert>
                {loading && <div role="status"><Spinner size="sm" aria-hidden="true" /> Betöltés...</div>}
                {!loading && !error && students.length === 0 && <p>Nincs elfogadott tanuló az osztályban.</p>}
                {!loading && students.length > 0 && <>
                    <Form.Group controlId={`${id}-student`} className="mb-3">
                        <Form.Label>Tanuló</Form.Label>
                        <Form.Select value={selectedId} onChange={e => selectStudent(e.target.value)} disabled={saving} required>
                            <option value="">Válassz tanulót</option>
                            {students.map(s => <option key={s.userId} value={s.userId}>{s.studentName} ({s.studentEmail})</option>)}
                        </Form.Select>
                    </Form.Group>
                    {selected && <>
                        {selected.hasOngoingSession && <Alert variant="warning">A tanulónak folyamatban lévő próbálkozása van. Csökkentés vagy korábbi határidő nem menthető.</Alert>}
                        <AssignmentTimingFields value={form} onChange={setForm} saving={saving} timeLimitMinutes={assignment.timeLimitMinutes} />
                    </>}
                </>}
                <LearningFeedback error={error} feedback={message ? { type: 'success', msg: message } : null} />
            </Modal.Body>
            <Modal.Footer>
                <Button variant="outline-secondary" onClick={onHide} disabled={saving}>Bezárás</Button>
                <Button type="submit" disabled={!selectedId || loading} aria-disabled={saving}>{saving ? 'Mentés...' : 'Időbeállítások mentése'}</Button>
            </Modal.Footer>
        </Form>
    </Modal>;
}
