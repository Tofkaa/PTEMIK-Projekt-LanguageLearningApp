import React, { useState, useEffect } from 'react';
import { Table, Badge, Spinner, Alert, Card } from 'react-bootstrap';
import { adminApi } from '../../services/adminApi';

const SystemLogs = () => {
    const [logs, setLogs] = useState([]);
    const [isLoading, setIsLoading] = useState(true);
    const [error, setError] = useState('');

    useEffect(() => {
        fetchLogs();

        // 1. Feliratkozás az egyedi eseményre (Event Listener)
        const handleAdminAction = () => {
            fetchLogs(false); // Csendes frissítés spinner nélkül
        };

        window.addEventListener('adminActionOccurred', handleAdminAction);

        // 2. Takarítás (Cleanup) amikor a komponens megsemmisül
        return () => {
            window.removeEventListener('adminActionOccurred', handleAdminAction);
        };
    }, []);

    // Ha isQuiet paraméter igaz, nem villogtatjuk a felületet (spinner)
    const fetchLogs = async (isQuiet = false) => {
        if (!isQuiet) setIsLoading(true);
        try {
            const response = await adminApi.getSystemLogs();
            setLogs(response.data);
            setError('');
        } catch (err) {
            console.error("Hiba a naplók lekérésekor:", err);
            setError('Nem sikerült betölteni a rendszernaplókat.');
        } finally {
            if (!isQuiet) setIsLoading(false);
        }
    };

    // Kibővített színkódolás a különböző akciótípusokhoz
    const getActionBadge = (actionType) => {
        switch (actionType) {
            // Felhasználók
            case 'USER_BANNED':
                return <Badge bg="danger" className="shadow-sm">FELFÜGGESZTÉS 🛑</Badge>;
            case 'USER_UNBANNED':
                return <Badge bg="success" className="shadow-sm">VISSZAÁLLÍTÁS ♻️</Badge>;
            case 'ROLE_CHANGED':
                return <Badge bg="warning" text="dark" className="shadow-sm">JOGOSULTSÁG 👑</Badge>;
            
            // Tananyag CMS - Új elemek (Import)
            case 'CURRICULUM_IMPORTED':
            case 'LESSON_IMPORTED':
            case 'EXERCISE_IMPORTED':
                return <Badge bg="info" text="dark" className="shadow-sm">TARTALOM IMPORT 📥</Badge>;
            
            // Tananyag CMS - Soft Delete
            case 'TOPIC_SUSPENDED':
            case 'LESSON_SUSPENDED':
            case 'EXERCISE_SUSPENDED':
                return <Badge bg="danger" className="shadow-sm">TARTALOM LETILTVA 🚫</Badge>;
            case 'TOPIC_RESTORED':
            case 'LESSON_RESTORED':
            case 'EXERCISE_RESTORED':
                return <Badge bg="success" className="shadow-sm">TARTALOM VISSZAÁLLÍTVA ♻️</Badge>;

            // Kitüntetések
            case 'ACHIEVEMENT_IMPORTED':
                return <Badge bg="info" text="dark" className="shadow-sm">KITÜNTETÉS IMPORT 📥</Badge>;
            case 'ACHIEVEMENT_SUSPENDED':
                return <Badge bg="danger" className="shadow-sm">KITÜNTETÉS LETILTVA 🚫</Badge>;
            case 'ACHIEVEMENT_RESTORED':
                return <Badge bg="success" className="shadow-sm">KITÜNTETÉS VISSZAÁLLÍTVA ♻️</Badge>;

            // Osztálytermek
            case 'CLASSROOM_BANNED':
                return <Badge bg="danger" className="shadow-sm">OSZTÁLY FELFÜGGESZTVE 🛑</Badge>;
            case 'CLASSROOM_RESTORED':
                return <Badge bg="success" className="shadow-sm">OSZTÁLY VISSZAÁLLÍTVA ♻️</Badge>;

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
                                            {new Date(log.loggedAt.endsWith('Z') ? log.loggedAt : log.loggedAt + 'Z').toLocaleString('hu-HU')}
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