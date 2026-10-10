import React, { useState, useEffect } from 'react';
import { Table, Badge, Button, Form, Spinner, Alert, InputGroup } from 'react-bootstrap';
import { adminApi } from '../../services/adminApi'; 
import { useAuth } from '../../context/AuthContext'; 

const UserManagement = () => {
    const { user: currentUser } = useAuth(); 
    const [users, setUsers] = useState([]);
    const [isLoading, setIsLoading] = useState(true);
    const [searchTerm, setSearchTerm] = useState('');
    const [message, setMessage] = useState({ text: '', type: '' });

    useEffect(() => {
        fetchUsers();
    }, []);

    const fetchUsers = async () => {
        setIsLoading(true);
        try {
            const response = await adminApi.getAllUsers();
            setUsers(response.data);
            setMessage({ text: '', type: '' }); 
        } catch (error) {
            console.error("Hiba a felhasználók lekérésekor:", error);
            setMessage({ text: 'Nem sikerült betölteni a felhasználókat. Ellenőrizd a kapcsolatot.', type: 'danger' });
        } finally {
            setIsLoading(false);
        }
    };

    const handleRoleChange = async (userId, newRole) => {
        try {
            await adminApi.updateUserRole(userId, newRole);
            setMessage({ text: 'Jogosultság sikeresen frissítve!', type: 'success' });
            window.dispatchEvent(new Event('adminActionOccurred'));
            fetchUsers();
        } catch (error) {
            setMessage({ text: 'Hiba a jogosultság módosításakor.', type: 'danger', error });
        }
    };

    const handleStatusToggle = async (userId, currentStatus) => {
        const isActivating = !currentStatus;
        const confirmMsg = isActivating 
            ? "Biztosan VISSZAÁLLÍTOD ezt a fiókot?" 
            : "Biztosan FELFÜGGESZTED (letiltod) ezt a fiókot?";

        if (window.confirm(confirmMsg)) {
            try {
                await adminApi.toggleUserStatus(userId, isActivating);
                setMessage({ text: `Felhasználó sikeresen ${isActivating ? 'visszaállítva' : 'felfüggesztve'}.`, type: 'success' });
                window.dispatchEvent(new Event('adminActionOccurred'));
                fetchUsers();
            } catch (error) {
                setMessage({ text: 'Hiba a státusz módosításakor.', type: 'danger' , error});
            }
        }
    };

    // Kliensoldali keresés név vagy email alapján
    const filteredUsers = users.filter(u => 
        u.name.toLowerCase().includes(searchTerm.toLowerCase()) ||
        u.email.toLowerCase().includes(searchTerm.toLowerCase())
    );

    const getRoleBadgeColor = (role) => {
        if (role === 'ADMIN' || role === 'ROLE_ADMIN') return 'danger';
        if (role === 'TEACHER' || role === 'ROLE_TEACHER') return 'warning';
        return 'info';
    };

    if (isLoading) {
        return <div className="text-center py-5"><Spinner animation="border" variant="light" /></div>;
    }

    return (
        <div>
            {message.text && (
                <Alert variant={message.type} onClose={() => setMessage({ text: '', type: '' })} dismissible>
                    {message.text}
                </Alert>
            )}

            <div className="d-flex justify-content-between align-items-center mb-3">
                <InputGroup style={{ maxWidth: '400px' }}>
                    <InputGroup.Text className="bg-dark text-secondary border-secondary">🔍</InputGroup.Text>
                    <Form.Control
                        type="text"
                        placeholder="Keresés név vagy email alapján..."
                        className="bg-dark text-light border-secondary"
                        value={searchTerm}
                        onChange={(e) => setSearchTerm(e.target.value)}
                    />
                </InputGroup>
                <Badge bg="secondary" className="fs-6 px-3 py-2">Összes: {filteredUsers.length} fő</Badge>
            </div>

            <div className="table-responsive">
                <Table hover variant="dark" className="border-secondary align-middle">
                    <thead className="bg-black bg-opacity-25">
                        <tr className="text-secondary">
                            <th>Név & Email</th>
                            <th>Regisztrált</th>
                            <th className="text-center">Státusz</th>
                            <th className="text-center" style={{ width: '200px' }}>Jogosultság</th>
                            <th className="text-end">Műveletek</th>
                        </tr>
                    </thead>
                    <tbody>
                        {filteredUsers.length === 0 ? (
                            <tr><td colSpan="5" className="text-center text-light py-4">Nincs találat.</td></tr>
                        ) : (
                            filteredUsers.map(u => {
                                const isSelf = u.userId === currentUser?.userId;

                                return (
                                <tr key={u.userId} className={!u.active ? 'opacity-75 bg-danger bg-opacity-10' : ''}>
                                    <td>
                                        <div className="fw-bold text-light">
                                            {u.name} <span className="text-secondary small">#{u.userTag}</span>
                                            {isSelf && <Badge bg="primary" className="ms-2">TE VAGY</Badge>}
                                        </div>
                                        <div className="text-light small">{u.email}</div>
                                    </td>
                                    <td className="small text-secondary">
                                        {new Date(u.createdAt).toLocaleDateString('hu-HU')}
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
                                            size="sm" 
                                            className={`bg-dark text-${getRoleBadgeColor(u.role)} border-${getRoleBadgeColor(u.role)} fw-bold shadow-sm`}
                                            value={u.role.replace('ROLE_', '')}
                                            onChange={(e) => handleRoleChange(u.userId, e.target.value)}
                                            style={{ width: '110px', cursor: isSelf ? 'not-allowed' : 'pointer', margin: '0 auto' }}
                                            disabled={isSelf}
                                            title={isSelf ? "Saját magad jogosultságát nem módosíthatod!" : ""}
                                        >
                                            <option value="STUDENT" className="text-info bg-dark fw-bold">Diák</option>
                                            <option value="TEACHER" className="text-warning bg-dark fw-bold">Tanár</option>
                                            <option value="ADMIN" className="text-danger bg-dark fw-bold">Admin</option>
                                        </Form.Select>
                                    </td>
                                    <td className="text-end">
                                        {u.active ? (
                                            <Button 
                                                variant="outline-danger" 
                                                size="sm" 
                                                className="rounded-pill px-3 fw-bold d-flex align-items-center gap-2 ms-auto" 
                                                onClick={() => handleStatusToggle(u.userId, u.active)}
                                                disabled={isSelf}
                                                title={isSelf ? "Saját magadat nem függesztheted fel!" : ""}
                                            >
                                                <span>🛑</span> Felfüggesztés
                                            </Button>
                                        ) : (
                                            <Button 
                                                variant="outline-success" 
                                                size="sm" 
                                                className="rounded-pill px-3 fw-bold d-flex align-items-center gap-2 ms-auto" 
                                                onClick={() => handleStatusToggle(u.userId, u.active)}
                                                disabled={isSelf}
                                            >
                                                <span>♻️</span> Visszaállítás
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