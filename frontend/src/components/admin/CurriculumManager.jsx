import React, { useState } from 'react';
import { Card, Form, Button, Alert, Spinner } from 'react-bootstrap';
import { adminApi } from '../../services/adminApi';

const CurriculumManager = () => {
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
                // 1. JSON beolvasása és parse-olása kliens oldalon (validáció)
                const jsonData = JSON.parse(e.target.result);

                // 2. Küldés a backendnek (TopicImportRequest formátumot vár a Controller)
                await adminApi.importCurriculum(jsonData);
                
                setMessage({ text: 'Tananyag sikeresen importálva az adatbázisba! 🎉', type: 'success' });
                setSelectedFile(null); // Reset
                
                // Form input resetelése
                document.getElementById('json-upload-input').value = '';

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

        // Fájl beolvasásának indítása
        reader.readAsText(selectedFile);
    };

    return (
        <Card className="bg-dark border-secondary shadow-lg">
            <Card.Body className="p-4">
                <h5 className="text-warning fw-bold mb-3">Tananyag JSON Importálása</h5>
                <p className="text-secondary small mb-4">
                    Tölts fel egy érvényes <code>TopicImportRequest</code> struktúrájú JSON fájlt, 
                    hogy új témaköröket, leckéket és feladatokat adj a rendszerhez. A már létező 
                    leckék nem duplikálódnak.
                </p>

                {message.text && (
                    <Alert variant={message.type} className="shadow-sm">
                        {message.text}
                    </Alert>
                )}

                <Form.Group className="mb-4">
                    <Form.Control 
                        id="json-upload-input"
                        type="file" 
                        accept=".json"
                        onChange={handleFileChange}
                        className="bg-dark text-light border-secondary"
                    />
                </Form.Group>

                <Button 
                    variant="warning" 
                    className="fw-bold px-4" 
                    onClick={handleUpload} 
                    disabled={!selectedFile || isLoading}
                >
                    {isLoading ? (
                        <><Spinner as="span" animation="border" size="sm" role="status" aria-hidden="true" className="me-2"/> Feldolgozás...</>
                    ) : (
                        'Feltöltés és Importálás 🚀'
                    )}
                </Button>
            </Card.Body>
        </Card>
    );
};

export default CurriculumManager;