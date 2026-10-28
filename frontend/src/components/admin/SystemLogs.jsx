import { safeError } from '../../utils/safeError';
import React, { useState, useEffect } from 'react';
import { Table, Badge, Spinner, Alert, Card } from 'react-bootstrap';
import { adminApi } from '../../services/adminApi';
import { formatToLocalTimestamp } from '../../utils/dateUtils';

const SystemLogs = () => {
    const [logs, setLogs] = useState([]);
    const [isLoading, setIsLoading] = useState(true);
    const [error, setError] = useState('');

    useEffect(() => {
        fetchLogs();

        // 1. Feliratkozás az egyedi eseményre (Event Listener)
        const handleAdminAction = () => {
            fetchLogs(true); // Keep the table mounted while refreshing after an admin action.
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
            console.error("Hiba a naplók lekérésekor:", safeError(err));
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
                return <Badge bg="danger" className="shadow-sm">FELFÜGGESZTÉS <span aria-hidden="true">🛑</span></Badge>;
            case 'USER_UNBANNED':
                return <Badge bg="success" className="shadow-sm">VISSZAÁLLÍTÁS <span aria-hidden="true">♻️</span></Badge>;
            case 'ROLE_CHANGED':
                return <Badge bg="warning" text="dark" className="shadow-sm">JOGOSULTSÁG <span aria-hidden="true">👑</span></Badge>;
            
            // Tananyag CMS - Új elemek (Import)
            case 'CURRICULUM_IMPORTED':
            case 'LESSON_IMPORTED':
            case 'EXERCISE_IMPORTED':
                return <Badge bg="info" text="dark" className="shadow-sm">TARTALOM IMPORT <span aria-hidden="true">📥</span></Badge>;
            
            // Tananyag CMS - Soft Delete
            case 'TOPIC_SUSPENDED':
            case 'LESSON_SUSPENDED':
            case 'EXERCISE_SUSPENDED':
                return <Badge bg="danger" className="shadow-sm">TARTALOM LETILTVA <span aria-hidden="true">🚫</span></Badge>;
            case 'TOPIC_RESTORED':
            case 'LESSON_RESTORED':
            case 'EXERCISE_RESTORED':
                return <Badge bg="success" className="shadow-sm">TARTALOM VISSZAÁLLÍTVA <span aria-hidden="true">♻️</span></Badge>;

            // Kitüntetések
            case 'ACHIEVEMENT_IMPORTED':
                return <Badge bg="info" text="dark" className="shadow-sm">KITÜNTETÉS IMPORT <span aria-hidden="true">📥</span></Badge>;
            case 'ACHIEVEMENT_SUSPENDED':
                return <Badge bg="danger" className="shadow-sm">KITÜNTETÉS LETILTVA <span aria-hidden="true">🚫</span></Badge>;
            case 'ACHIEVEMENT_RESTORED':
                return <Badge bg="success" className="shadow-sm">KITÜNTETÉS VISSZAÁLLÍTVA <span aria-hidden="true">♻️</span></Badge>;

            // Osztálytermek
            case 'CLASSROOM_BANNED':
                return <Badge bg="danger" className="shadow-sm">OSZTÁLY FELFÜGGESZTVE <span aria-hidden="true">🛑</span></Badge>;
            case 'CLASSROOM_RESTORED':
                return <Badge bg="success" className="shadow-sm">OSZTÁLY VISSZAÁLLÍTVA <span aria-hidden="true">♻️</span></Badge>;

            default:
                return <Badge bg="secondary" className="shadow-sm">{actionType}</Badge>;
        }
    };

    if (isLoading) {
        return <div role="status" className="text-center py-5"><Spinner animation="border" variant="light" aria-hidden="true" /> Rendszernapló betöltése…</div>;
    }

    return (
        <Card className="bg-dark border-secondary shadow-lg">
            <Card.Body className="p-0">
                {error && (
                    <div className="p-3">
                        <Alert variant="danger" className="m-0">{error}</Alert>
                    </div>
                )}
                
                <div role="region" aria-label="Rendszernapló, görgethető táblázat" tabIndex={0} className="table-responsive custom-scrollbar" style={{ maxHeight: '600px' }}>
                    <Table hover variant="dark" className="admin-data-table m-0 align-middle border-secondary" style={{ borderCollapse: 'separate', borderSpacing: 0 }}>
                        <caption className="visually-hidden">Adminisztrációs események időpontja, végrehajtója és részletei</caption>
                        <thead className="position-sticky top-0 bg-dark" style={{ zIndex: 1 }}>
                            <tr>
                                <th scope="col" className="text-secondary border-secondary py-3 px-4">Időpont</th>
                                <th scope="col" className="text-secondary border-secondary py-3">Adminisztrátor</th>
                                <th scope="col" className="text-secondary border-secondary py-3">Művelet</th>
                                <th scope="col" className="text-secondary border-secondary py-3 px-4">Részletek</th>
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
                                            {formatToLocalTimestamp(log.loggedAt)}
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
