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
            setMessage({ text: 'Hiba az osztálytermek betöltésekor.', type: 'danger' });
        } finally { setIsLoading(false); }
    };

    const handleStatusToggle = async (id, currentStatus) => {
        const nextStatus = !currentStatus;
        if (window.confirm(`Biztosan ${nextStatus ? 'visszaállítod' : 'felfüggeszted'} ezt az osztálytermet?`)) {
            try {
                await adminApi.toggleClassroomStatus(id, nextStatus);
                setMessage({ text: 'Státusz sikeresen frissítve.', type: 'success' });
                fetchClassrooms();
            } catch (error) {
                setMessage({ text: 'Hiba történt a művelet során.', type: 'danger' });
            }
        }
    };

    if (isLoading) return <div className="text-center py-5"><Spinner animation="border" variant="primary" /></div>;

    return (
        <div>
            {message.text && <Alert variant={message.type} dismissible onClose={() => setMessage({text:'', type:''})}>{message.text}</Alert>}
            <Table hover variant="dark" className="border-secondary align-middle">
                <thead>
                    <tr className="text-secondary">
                        <th>Név & Tanár</th>
                        <th>Kód</th>
                        <th className="text-center">Státusz</th>
                        <th className="text-end">Műveletek</th>
                    </tr>
                </thead>
                <tbody>
                    {classrooms.map(c => (
                        <tr key={c.classroomId} className={!c.active ? 'opacity-50' : ''}>
                            <td>
                                <div className="fw-bold text-light">{c.name}</div>
                                <div className="text-secondary small">Tanár: {c.teacherName} ({c.teacherEmail})</div>
                            </td>
                            <td><code className="text-info">{c.inviteCode}</code></td>
                            <td className="text-center">
                                <Badge bg={c.active ? 'success' : 'danger'}>{c.active ? 'Aktív' : 'Tiltott'}</Badge>
                            </td>
                            <td className="text-end">
                                <Button variant={c.active ? "outline-danger" : "outline-success"} size="sm" onClick={() => handleStatusToggle(c.classroomId, c.active)}>
                                    {c.active ? 'Letiltás' : 'Visszaállítás'}
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