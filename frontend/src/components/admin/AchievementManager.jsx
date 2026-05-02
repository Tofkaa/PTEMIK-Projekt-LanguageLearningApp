import React, { useState, useEffect } from 'react';
import { Card, Form, Button, Alert, Spinner, Table, Badge } from 'react-bootstrap';
import { adminApi } from '../../services/adminApi';

const AchievementManager = () => {
    const [selectedFile, setSelectedFile] = useState(null);
    const [isLoading, setIsLoading] = useState(false);
    const [message, setMessage] = useState({ text: '', type: '' });
    const [achievements, setAchievements] = useState([]);
    const [isLoadingAch, setIsLoadingAch] = useState(true);

    useEffect(() => {
        fetchAchievements();
    }, []);

    const fetchAchievements = async () => {
        setIsLoadingAch(true);
        try {
            const response = await adminApi.getAllAchievements();
            setAchievements(Array.isArray(response.data) ? response.data : []);
        } catch (error) {
            console.error("Hiba a kitüntetések lekérésekor:", error);
            setAchievements([]);
        } finally {
            setIsLoadingAch(false);
        }
    };

    const handleFileChange = (e) => {
        const file = e.target.files[0];
        if (file && file.type === "application/json") {
            setSelectedFile(file); setMessage({ text: '', type: '' });
        } else {
            setSelectedFile(null); setMessage({ text: 'Csak .json fájlt!', type: 'danger' });
        }
    };

    const handleUpload = async () => {
        if (!selectedFile) return;
        setIsLoading(true); setMessage({ text: '', type: '' });
        const reader = new FileReader();
        reader.onload = async (e) => {
            try {
                const jsonData = JSON.parse(e.target.result);
                await adminApi.importAchievements(jsonData);
                setMessage({ text: 'Kitüntetések sikeresen importálva! 🏆', type: 'success' });
                setSelectedFile(null);
                document.getElementById('achievement-upload-input').value = '';
                fetchAchievements(); 
            } catch (error) {
                setMessage({ text: 'Hiba az importálás során.', type: 'danger' , error});
            } finally { setIsLoading(false); }
        };
        reader.readAsText(selectedFile);
    };

    const handleDelete = async (id, name) => {
        if (window.confirm(`Biztosan törlöd ezt a kitüntetést? (${name})`)) {
            try {
                await adminApi.deleteAchievement(id);
                setMessage({ text: 'Kitüntetés sikeresen eltávolítva!', type: 'success' });
                fetchAchievements();
            } catch (error) {
                setMessage({ text: 'Hiba a törlés során.', type: 'danger' , error});
            }
        }
    };

    return (
        <div>
            {message.text && <Alert variant={message.type} className="shadow-sm rounded-4">{message.text}</Alert>}

            <Card className="bg-dark border-secondary shadow-lg mb-4 rounded-4">
                <Card.Body className="p-4">
                    <h5 className="text-info fw-bold mb-3">Kitüntetések JSON Importálása</h5>
                    <Form.Group className="mb-4">
                        <Form.Control id="achievement-upload-input" type="file" accept=".json" onChange={handleFileChange} className="bg-dark text-light border-secondary rounded-3" />
                    </Form.Group>
                    <Button variant="info" className="fw-bold px-4 text-dark rounded-pill" onClick={handleUpload} disabled={!selectedFile || isLoading}>
                        {isLoading ? <Spinner as="span" animation="border" size="sm" className="me-2"/> : 'Kitüntetések Feltöltése 🏆'}
                    </Button>
                </Card.Body>
            </Card>

            <h5 className="text-light fw-bold mb-3">Menedzselt Kitüntetések</h5>
            {isLoadingAch ? (
                <div className="text-center py-4"><Spinner animation="border" variant="info" /></div>
            ) : achievements.length === 0 ? (
                <p className="text-secondary fst-italic">Még nincsenek kitüntetések a rendszerben.</p>
            ) : (
                <div className="table-responsive rounded-4 border border-secondary">
                    <Table hover variant="dark" className="m-0 align-middle">
                        <thead className="bg-black bg-opacity-25">
                            <tr className="text-secondary">
                                <th className="py-3 px-4">Ikon & Név</th>
                                <th className="py-3">Leírás</th>
                                <th className="py-3">Szabály (Criteria)</th>
                                <th className="text-end py-3 px-4">Művelet</th>
                            </tr>
                        </thead>
                        <tbody>
                            {achievements.map(ach => (
                                <tr key={ach.achievementId}>
                                    <td className="px-4">
                                        <span className="fs-4 me-3">{ach.iconUrl}</span>
                                        <span className="fw-bold text-light">{ach.name}</span>
                                    </td>
                                    <td className="text-secondary small">{ach.description}</td>
                                    <td><Badge bg="secondary" pill className="px-3 py-2">{ach.criteria?.type}</Badge></td>
                                    <td className="text-end px-4">
                                        <Button variant="outline-danger" size="sm" className="rounded-pill px-3 fw-bold d-flex align-items-center gap-2 ms-auto" onClick={() => handleDelete(ach.achievementId, ach.name)}>
                                            <span>🗑️</span> Törlés
                                        </Button>
                                    </td>
                                </tr>
                            ))}
                        </tbody>
                    </Table>
                </div>
            )}
        </div>
    );
};

export default AchievementManager;