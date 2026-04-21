import React, { useState, useEffect } from 'react';
import { Table, Badge, Spinner, Alert, Card } from 'react-bootstrap';
import { adminApi } from '../../services/adminApi';

const SystemLogs = () => {
    const [logs, setLogs] = useState([]);
    const [isLoading, setIsLoading] = useState(true);
    const [error, setError] = useState('');

    useEffect(() => {
        fetchLogs();
    }, []);

    const fetchLogs = async () => {
        setIsLoading(true);
        try {
            const response = await adminApi.getSystemLogs();
            setLogs(response.data);
            setError('');
        } catch (err) {
            console.error("Hiba a naplók lekérésekor:", err);
            setError('Nem sikerült betölteni a rendszernaplókat.');
        } finally {
            setIsLoading(false);
        }
    };

    // Színkódolás a különböző akciótípusokhoz
    const getActionBadge = (actionType) => {
        switch (actionType) {
            case 'USER_BANNED':
                return <Badge bg="danger" className="shadow-sm">FELFÜGGESZTÉS 🛑</Badge>;
            case 'USER_UNBANNED':
                return <Badge bg="success" className="shadow-sm">VISSZAÁLLÍTÁS ♻️</Badge>;
            case 'ROLE_CHANGED':
                return <Badge bg="warning" text="dark" className="shadow-sm">JOGOSULTSÁG 👑</Badge>;
            case 'LESSON_ADDED':
            case 'CURRICULUM_IMPORTED':
                return <Badge bg="info" text="dark" className="shadow-sm">TARTALOM 📚</Badge>;
            case 'CLASSROOM_DELETED':
                return <Badge bg="danger" className="shadow-sm">OSZTÁLY TÖRLÉS 🗑️</Badge>;
            default:
                return <Badge bg="secondary" className="shadow-sm">{actionType}</Badge>;
        }
    };

    if (isLoading) {
        return <div className="text-center py-5"><Spinner animation="border" variant="light" /></div>;
    }

    return (
        <Card className="bg-dark border-secondary shadow-lg">
            <Card.Body className="p-0">
                {error && (
                    <div className="p-3">
                        <Alert variant="danger" className="m-0">{error}</Alert>
                    </div>
                )}
                
                <div className="table-responsive custom-scrollbar" style={{ maxHeight: '600px' }}>
                    <Table hover variant="dark" className="m-0 align-middle border-secondary" style={{ borderCollapse: 'separate', borderSpacing: 0 }}>
                        <thead className="position-sticky top-0 bg-dark" style={{ zIndex: 1 }}>
                            <tr>
                                <th className="text-secondary border-secondary py-3 px-4">Időpont</th>
                                <th className="text-secondary border-secondary py-3">Adminisztrátor</th>
                                <th className="text-secondary border-secondary py-3">Művelet</th>
                                <th className="text-secondary border-secondary py-3 px-4">Részletek</th>
                            </tr>
                        </thead>
                        <tbody>
                            {logs.length === 0 ? (
                                <tr>
                                    <td colSpan="4" className="text-center text-secondary py-5">
                                        Még nincsenek rögzített események a naplóban.
                                    </td>
                                </tr>
                            ) : (
                                logs.map((log) => (
                                    <tr key={log.logId}>
                                        <td className="text-light px-4 small" style={{ whiteSpace: 'nowrap' }}>
                                            {new Date(log.loggedAt).toLocaleString('hu-HU')}
                                        </td>
                                        <td>
                                            <div className="fw-bold text-light">{log.admin?.name || 'Ismeretlen'}</div>
                                            <div className="text-secondary small">{log.admin?.email}</div>
                                        </td>
                                        <td>
                                            {getActionBadge(log.actionType)}
                                        </td>
                                        <td className="text-light px-4 small">
                                            {log.details}
                                        </td>
                                    </tr>
                                ))
                            )}
                        </tbody>
                    </Table>
                </div>
            </Card.Body>
        </Card>
    );
};

export default SystemLogs;