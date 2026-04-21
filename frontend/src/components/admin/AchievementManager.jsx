import React, { useState } from 'react';
import { Card, Form, Button, Alert, Spinner } from 'react-bootstrap';
import { adminApi } from '../../services/adminApi';

const AchievementManager = () => {
    const [selectedFile, setSelectedFile] = useState(null);
    const [isLoading, setIsLoading] = useState(false);
    const [message, setMessage] = useState({ text: '', type: '' });

    const handleFileChange = (e) => {
        const file = e.target.files[0];
        if (file && file.type === "application/json") {
            setSelectedFile(file);
            setMessage({ text: '', type: '' });
        } else {
            setSelectedFile(null);
            setMessage({ text: 'Kérlek, csak érvényes .json fájlt tölts fel!', type: 'danger' });
        }
    };

    const handleUpload = async () => {
        if (!selectedFile) return;

        setIsLoading(true);
        setMessage({ text: '', type: '' });

        const reader = new FileReader();
        
        reader.onload = async (e) => {
            try {
                const jsonData = JSON.parse(e.target.result);

                // API hívás az új végpontra
                await adminApi.importAchievements(jsonData);
                
                setMessage({ text: 'Kitüntetések sikeresen importálva a rendszerbe! 🏆', type: 'success' });
                setSelectedFile(null);
                document.getElementById('achievement-upload-input').value = '';

            } catch (error) {
                console.error("Importálási hiba:", error);
                setMessage({ 
                    text: error.name === 'SyntaxError' 
                        ? 'Hibás JSON formátum! Kérlek ellenőrizd a fájlt.' 
                        : 'Hiba történt a szerver oldalon a feldolgozás során.', 
                    type: 'danger' 
                });
            } finally {
                setIsLoading(false);
            }
        };

        reader.onerror = () => {
            setMessage({ text: 'Hiba a fájl olvasásakor.', type: 'danger' });
            setIsLoading(false);
        };

        reader.readAsText(selectedFile);
    };

    return (
        <Card className="bg-dark border-secondary shadow-lg">
            <Card.Body className="p-4">
                <h5 className="text-success fw-bold mb-3">Kitüntetések (Achievements) JSON Importálása</h5>
                <p className="text-secondary small mb-4">
                    Tölts fel egy érvényes JSON fájlt, amely tartalmazza a rendszer új kitüntetéseinek szabályrendszerét (Criteria) és ikonjait.
                </p>

                {message.text && (
                    <Alert variant={message.type} className="shadow-sm">
                        {message.text}
                    </Alert>
                )}

                <Form.Group className="mb-4">
                    <Form.Control 
                        id="achievement-upload-input"
                        type="file" 
                        accept=".json"
                        onChange={handleFileChange}
                        className="bg-dark text-light border-secondary"
                    />
                </Form.Group>

                <Button 
                    variant="success" 
                    className="fw-bold px-4" 
                    onClick={handleUpload} 
                    disabled={!selectedFile || isLoading}
                >
                    {isLoading ? (
                        <><Spinner as="span" animation="border" size="sm" role="status" aria-hidden="true" className="me-2"/> Feldolgozás...</>
                    ) : (
                        'Kitüntetések Feltöltése 🏆'
                    )}
                </Button>
            </Card.Body>
        </Card>
    );
};

export default AchievementManager;