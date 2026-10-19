import React, { useState, useEffect } from 'react';
import { Table, Badge, Button, Spinner } from 'react-bootstrap';
import { adminApi } from '../../services/adminApi';
import LearningFeedback from '../LearningFeedback';

const ClassroomManager = () => {
    const [classrooms, setClassrooms] = useState([]);
    const [isLoading, setIsLoading] = useState(true);
    const [message, setMessage] = useState({ text: '', type: '' });
    const [statusBusy, setStatusBusy] = useState(false);

    useEffect(() => { fetchClassrooms(); }, []);

    const fetchClassrooms = async () => {
        setIsLoading(true);
        try {
            const response = await adminApi.getAllClassrooms();
            setClassrooms(response.data);
        } catch (error) {
            setMessage({ text: 'Hiba az osztálytermek betöltésekor.', type: 'danger' }, error);
        } finally { setIsLoading(false); }
    };

    const handleStatusToggle = async (id, currentStatus) => {
        if (statusBusy) return;
        const nextStatus = !currentStatus;
        if (window.confirm(`Biztosan ${nextStatus ? 'visszaállítod' : 'felfüggeszted'} ezt az osztálytermet?`)) {
            setStatusBusy(true);
            try {
                await adminApi.toggleClassroomStatus(id, nextStatus);
                setMessage({ text: 'Státusz sikeresen frissítve.', type: 'success' });
                window.dispatchEvent(new Event('adminActionOccurred'));
                setClassrooms(previous => previous.map(classroom => classroom.classroomId === id ? { ...classroom, active: nextStatus } : classroom));
            } catch (error) {
                setMessage({ text: 'Hiba történt a művelet során.', type: 'danger' }, error);
            } finally {
                setStatusBusy(false);
            }
        }
    };

    if (isLoading) return <div role="status" className="text-center py-5"><Spinner animation="border" variant="primary" aria-hidden="true" /> Osztálytermek betöltése…</div>;

    return (
        <div>
            <LearningFeedback error={message.type === 'danger' ? message.text : ''}
                feedback={message.type === 'success' ? { type: 'success', msg: message.text } : null} />
            <Table responsive hover variant="dark" className="border-secondary align-middle">
                <caption className="visually-hidden">Osztálytermek moderációja</caption>
                <thead className="bg-black bg-opacity-25">
                    <tr className="text-secondary">
                        <th>Név & Tanár</th>
                        <th>Kód</th>
                        <th className="text-center">Státusz</th>
                        <th className="text-end">Műveletek</th>
                    </tr>
                </thead>
                <tbody>
                    {classrooms.map(c => (
                       <tr key={c.classroomId} className={!c.active ? 'bg-danger bg-opacity-10' : ''}>
                            <td>
                                <div className="fw-bold text-light">{c.name}</div>
                                <div className="text-secondary small">Tanár: {c.teacherName} ({c.teacherEmail})</div>
                            </td>
                            <td><code className="text-info">{c.inviteCode}</code></td>
                            <td className="text-center">
                                <Badge bg={c.active ? 'success' : 'danger'}>{c.active ? 'Aktív' : 'Tiltott'}</Badge>
                            </td>
                            <td className="text-end">
                                <Button variant={c.active ? 'outline-danger' : 'outline-success'} size="sm"
                                    className="rounded-pill px-3 fw-bold d-flex align-items-center gap-2 ms-auto"
                                    aria-label={`${c.active ? 'Letiltás' : 'Visszaállítás'}: ${c.name}, ${c.teacherName}`}
                                    aria-disabled={statusBusy} onClick={() => handleStatusToggle(c.classroomId, c.active)}>
                                    <span aria-hidden="true">{c.active ? '🛑' : '♻️'}</span> {c.active ? 'Letiltás' : 'Visszaállítás'}
                                </Button>
                            </td>
                        </tr>
                    ))}
                </tbody>
            </Table>
        </div>
    );
};

export default ClassroomManager;
