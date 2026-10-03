import React, { useState, useEffect } from 'react';
import { Table, Badge, Button, Spinner, Alert } from 'react-bootstrap';
import { adminApi } from '../../services/adminApi';

const ClassroomManager = () => {
    const [classrooms, setClassrooms] = useState([]);
    const [isLoading, setIsLoading] = useState(true);
    const [message, setMessage] = useState({ text: '', type: '' });

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
        const nextStatus = !currentStatus;
        if (window.confirm(`Biztosan ${nextStatus ? 'visszaállítod' : 'felfüggeszted'} ezt az osztálytermet?`)) {
            try {
                await adminApi.toggleClassroomStatus(id, nextStatus);
                setMessage({ text: 'Státusz sikeresen frissítve.', type: 'success' });
                window.dispatchEvent(new Event('adminActionOccurred'));
                fetchClassrooms();
            } catch (error) {
                setMessage({ text: 'Hiba történt a művelet során.', type: 'danger' }, error);
            }
        }
    };

    if (isLoading) return <div className="text-center py-5"><Spinner animation="border" variant="primary" /></div>;

    return (
        <div>
            {message.text && <Alert variant={message.type} dismissible onClose={() => setMessage({text:'', type:''})}>{message.text}</Alert>}
            <Table hover variant="dark" className="border-secondary align-middle">
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
                       <tr key={c.classroomId} className={!c.active ? 'opacity-75 bg-danger bg-opacity-10' : ''}>
                            <td>
                                <div className="fw-bold text-light">{c.name}</div>
                                <div className="text-secondary small">Tanár: {c.teacherName} ({c.teacherEmail})</div>
                            </td>
                            <td><code className="text-info">{c.inviteCode}</code></td>
                            <td className="text-center">
                                <Badge bg={c.active ? 'success' : 'danger'}>{c.active ? 'Aktív' : 'Tiltott'}</Badge>
                            </td>
                            <td className="text-end">
                                {c.active ? (
                                    <Button variant="outline-danger" size="sm" className="rounded-pill px-3 fw-bold d-flex align-items-center gap-2 ms-auto" onClick={() => handleStatusToggle(c.classroomId, c.active)}>
                                        <span>🛑</span> Letiltás
                                    </Button>
                                ) : (
                                    <Button variant="outline-success" size="sm" className="rounded-pill px-3 fw-bold d-flex align-items-center gap-2 ms-auto" onClick={() => handleStatusToggle(c.classroomId, c.active)}>
                                        <span>♻️</span> Visszaállítás
                                    </Button>
                                )}
                            </td>
                        </tr>
                    ))}
                </tbody>
            </Table>
        </div>
    );
};

export default ClassroomManager;