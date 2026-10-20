import { useId, useRef, useState, useEffect } from 'react';
import { Table, Badge, Button, Form, Spinner, InputGroup } from 'react-bootstrap';
import { adminApi } from '../../services/adminApi'; 
import { formatToLocalDisplay } from '../../utils/dateUtils';
import { useAuth } from '../../context/AuthContext'; 
import LearningFeedback from '../LearningFeedback';

const UserManagement = () => {
    const { user: currentUser } = useAuth(); 
    const [users, setUsers] = useState([]);
    const [isLoading, setIsLoading] = useState(true);
    const [searchTerm, setSearchTerm] = useState('');
    const [message, setMessage] = useState({ text: '', type: '' });
    const [busyUserId, setBusyUserId] = useState(null);
    const [isRefreshing, setIsRefreshing] = useState(false);
    const mutationPending = useRef(false);
    const fetchPending = useRef(false);
    const id = useId();

    useEffect(() => {
        fetchUsers();
    }, []);

    const fetchUsers = async () => {
        if (fetchPending.current || mutationPending.current) return;
        fetchPending.current = true;
        setIsRefreshing(true);
        try {
            const response = await adminApi.getAllUsers();
            setUsers(response.data);
            setMessage({ text: '', type: '' }); 
        } catch (error) {
            console.error("Hiba a felhasználók lekérésekor:", error);
            setMessage({ text: 'Nem sikerült betölteni a felhasználókat. Ellenőrizd a kapcsolatot.', type: 'danger' });
        } finally {
            fetchPending.current = false;
            setIsRefreshing(false);
            setIsLoading(false);
        }
    };

    const handleRoleChange = async (userId, newRole) => {
        if (mutationPending.current || fetchPending.current || userId === currentUser?.userId) return;
        const currentRole = users.find(user => user.userId === userId)?.role?.replace('ROLE_', '');
        if (!['STUDENT', 'TEACHER', 'ADMIN'].includes(newRole) || newRole === currentRole) return;
        mutationPending.current = true;
        setBusyUserId(userId);
        setMessage({ text: '', type: '' });
        try {
            await adminApi.updateUserRole(userId, newRole);
            setUsers(previous => previous.map(user => user.userId === userId ? { ...user, role: newRole } : user));
            setMessage({ text: 'Jogosultság sikeresen frissítve!', type: 'success' });
            window.dispatchEvent(new Event('adminActionOccurred'));
        } catch (error) {
            setMessage({ text: 'Hiba a jogosultság módosításakor.', type: 'danger', error });
        } finally {
            mutationPending.current = false;
            setBusyUserId(null);
        }
    };

    const handleStatusToggle = async (userId, currentStatus) => {
        if (mutationPending.current || fetchPending.current || userId === currentUser?.userId) return;
        const isActivating = !currentStatus;
        const confirmMsg = isActivating 
            ? "Biztosan VISSZAÁLLÍTOD ezt a fiókot?" 
            : "Biztosan FELFÜGGESZTED (letiltod) ezt a fiókot?";

        if (window.confirm(confirmMsg)) {
            mutationPending.current = true;
            setBusyUserId(userId);
            setMessage({ text: '', type: '' });
            try {
                await adminApi.toggleUserStatus(userId, isActivating);
                setUsers(previous => previous.map(user => user.userId === userId ? { ...user, active: isActivating } : user));
                setMessage({ text: `Felhasználó sikeresen ${isActivating ? 'visszaállítva' : 'felfüggesztve'}.`, type: 'success' });
                window.dispatchEvent(new Event('adminActionOccurred'));
            } catch (error) {
                setMessage({ text: 'Hiba a státusz módosításakor.', type: 'danger' , error});
            } finally {
                mutationPending.current = false;
                setBusyUserId(null);
            }
        }
    };

    // Kliensoldali keresés név vagy email alapján
    const filteredUsers = users.filter(u => 
        (u.name || '').toLowerCase().includes(searchTerm.toLowerCase()) ||
        (u.email || '').toLowerCase().includes(searchTerm.toLowerCase())
    );

    if (isLoading) {
        return <div role="status" className="text-center py-5"><Spinner animation="border" variant="light" aria-hidden="true" /> Felhasználók betöltése…</div>;
    }

    return (
        <div>
            <LearningFeedback error={message.type === 'danger' ? message.text : ''}
                feedback={message.type === 'success' ? { type: 'success', msg: message.text } : null} />

            <div className="d-flex flex-wrap gap-3 justify-content-between align-items-center mb-3">
                <InputGroup style={{ maxWidth: '400px' }}>
                    <InputGroup.Text aria-hidden="true" className="bg-dark text-secondary border-secondary">🔍</InputGroup.Text>
                    <Form.Control
                        aria-label="Felhasználók keresése név vagy e-mail alapján"
                        type="text"
                        placeholder="Keresés név vagy email alapján..."
                        className="bg-dark text-light border-secondary"
                        value={searchTerm}
                        onChange={(e) => setSearchTerm(e.target.value)}
                    />
                </InputGroup>
                <Badge role="status" aria-atomic="true" bg="secondary" className="fs-6 px-3 py-2">Összes: {filteredUsers.length} fő</Badge>
                <Button type="button" variant="outline-secondary" aria-disabled={isRefreshing || busyUserId !== null}
                    aria-busy={isRefreshing} onClick={fetchUsers}>Felhasználók újratöltése</Button>
            </div>

            <div role="region" tabIndex={0} aria-label="Felhasználók, görgethető táblázat" className="table-responsive">
                <Table hover variant="dark" className="admin-data-table border-secondary align-middle">
                    <caption className="visually-hidden">Felhasználók, fiókstátuszok és jogosultságok kezelése</caption>
                    <thead className="bg-black bg-opacity-25">
                        <tr className="text-secondary">
                            <th scope="col">Név és e-mail</th>
                            <th scope="col">Regisztrált</th>
                            <th scope="col" className="text-center">Státusz</th>
                            <th scope="col" className="text-center" style={{ width: '200px' }}>Jogosultság</th>
                            <th scope="col" className="text-end">Műveletek</th>
                        </tr>
                    </thead>
                    <tbody>
                        {filteredUsers.length === 0 ? (
                            <tr><td colSpan="5" className="text-center text-light py-4">Nincs találat.</td></tr>
                        ) : (
                            filteredUsers.map(u => {
                                const isSelf = u.userId === currentUser?.userId;

                                return (
                                <tr key={u.userId} className={!u.active ? 'bg-danger bg-opacity-10' : ''}>
                                    <th scope="row">
                                        <div className="fw-bold text-light">
                                            {u.name} <span className="text-secondary small">#{u.userTag}</span>
                                            {isSelf && <Badge bg="primary" className="ms-2">TE VAGY</Badge>}
                                        </div>
                                        <div className="text-light small">{u.email}</div>
                                        {isSelf && <span id={`${id}-self`} className="visually-hidden">Saját fiókod jogosultságát és státuszát itt nem módosíthatod.</span>}
                                    </th>
                                    <td className="small text-secondary">
                                        {formatToLocalDisplay(u.createdAt, false)}
                                    </td>
                                    <td className="text-center">
                                        {u.active ? (
                                            <Badge bg="success">Aktív</Badge>
                                        ) : (
                                            <Badge bg="danger">Felfüggesztve</Badge>
                                        )}
                                    </td>
                                    <td className="text-center">
                                        <Form.Select 
                                            aria-label={`Jogosultság: ${u.name} (${u.email})`}
                                            size="sm" 
                                            className="fw-bold shadow-sm"
                                            value={u.role.replace('ROLE_', '')}
                                            onChange={(e) => handleRoleChange(u.userId, e.target.value)}
                                            style={{ width: '110px', cursor: isSelf ? 'not-allowed' : 'pointer', margin: '0 auto' }}
                                            disabled={isSelf}
                                            aria-describedby={isSelf ? `${id}-self` : undefined}
                                            aria-disabled={isSelf || busyUserId !== null || isRefreshing}
                                            aria-busy={busyUserId === u.userId}
                                            title={isSelf ? "Saját magad jogosultságát nem módosíthatod!" : ""}
                                        >
                                            <option value="STUDENT">Diák</option>
                                            <option value="TEACHER">Tanár</option>
                                            <option value="ADMIN">Admin</option>
                                        </Form.Select>
                                    </td>
                                    <td className="text-end">
                                        {u.active ? (
                                            <Button 
                                                variant="outline-danger" 
                                                size="sm" 
                                                className="rounded-pill px-3 fw-bold d-flex align-items-center gap-2 ms-auto" 
                                                onClick={() => handleStatusToggle(u.userId, u.active)}
                                                aria-label={`Felfüggesztés: ${u.name} (${u.email})`}
                                                disabled={isSelf}
                                                aria-describedby={isSelf ? `${id}-self` : undefined}
                                                aria-disabled={isSelf || busyUserId !== null || isRefreshing}
                                                aria-busy={busyUserId === u.userId}
                                                title={isSelf ? "Saját magadat nem függesztheted fel!" : ""}
                                            >
                                                <span aria-hidden="true">🛑</span> Felfüggesztés
                                            </Button>
                                        ) : (
                                            <Button 
                                                variant="outline-success" 
                                                size="sm" 
                                                className="rounded-pill px-3 fw-bold d-flex align-items-center gap-2 ms-auto" 
                                                onClick={() => handleStatusToggle(u.userId, u.active)}
                                                aria-label={`Visszaállítás: ${u.name} (${u.email})`}
                                                disabled={isSelf}
                                                aria-describedby={isSelf ? `${id}-self` : undefined}
                                                aria-disabled={isSelf || busyUserId !== null || isRefreshing}
                                                aria-busy={busyUserId === u.userId}
                                            >
                                                <span aria-hidden="true">♻️</span> Visszaállítás
                                            </Button>
                                        )}
                                    </td>
                                </tr>
                            )
                        })
                        )}
                    </tbody>
                </Table>
            </div>
        </div>
    );
};

export default UserManagement;
