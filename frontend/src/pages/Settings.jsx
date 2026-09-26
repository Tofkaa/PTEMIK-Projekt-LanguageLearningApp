import React, { useState, useRef } from 'react';
import { Container, Card, Form, Button, Spinner, Alert, Tabs, Tab, Row, Col } from 'react-bootstrap';
import { useAuth } from '../context/AuthContext.jsx';
import { useNavigate } from 'react-router-dom';
import api from '../services/api.jsx';

const Settings = () => {
    const { user, setUser } = useAuth();
    const navigate = useNavigate();

    // --- STATES ---
    const [difficulty, setDifficulty] = useState(user?.preferredDifficulty || 'MEDIUM');
    const [isUpdatingDiff, setIsUpdatingDiff] = useState(false);
    const [diffMessage, setDiffMessage] = useState({ type: '', text: '' });

    const [isUploadingImage, setIsUploadingImage] = useState(false);
    const fileInputRef = useRef(null);

    // --- IMAGE UPLOAD LOGIC ---
    const handleFileChange = async (e) => {
        const file = e.target.files[0];
        if (!file) return;

        if (!file.type.startsWith('image/')) {
            alert('Kérlek, csak képformátumot (JPG, PNG) tölts fel!');
            return;
        }

        const formData = new FormData();
        formData.append('file', file);

        setIsUploadingImage(true);
        try {
            const response = await api.post('/users/me/profile-picture', formData, {
                headers: { 'Content-Type': 'multipart/form-data' }
            });
            
            setUser((prevUser) => ({ ...prevUser, profilePictureUrl: response.data }));
        } catch (error) {
            console.error('Hiba a profilkép feltöltésekor:', error);
            alert('Nem sikerült feltölteni a képet. Kérlek, próbáld újra!');
        } finally {
            setIsUploadingImage(false);
            if (fileInputRef.current) fileInputRef.current.value = '';
        }
    };

    // --- DIFFICULTY SAVE LOGIC ---
   const handleSavePreferences = async (e) => {
        e.preventDefault();
        setIsUpdatingDiff(true);
        setDiffMessage({ type: '', text: '' });

        try {
            await api.put('/users/me/preferences', { preferredDifficulty: difficulty });
            
            setUser((prevUser) => ({ 
                ...prevUser, 
                preferredDifficulty: difficulty 
            }));
            
            setDiffMessage({ type: 'success', text: 'Tanulási beállítások elmentve! ✅' });
            setTimeout(() => setDiffMessage({ type: '', text: '' }), 3000);
        } catch (err) {
            setDiffMessage({ type: 'danger', text: 'Hiba történt a beállítások mentésekor.' }, err);
        } finally {
            setIsUpdatingDiff(false);
        }
    };

    if (!user) return null;

    return (
        <Container className="mt-5 pt-4 text-light pb-5" style={{ maxWidth: '900px' }}>
            <Button variant="link" className="text-info text-decoration-none p-0 mb-3 fw-bold" onClick={() => navigate('/profile')}>
                ⬅️ Vissza a profilomra
            </Button>
            
            <h2 className="fw-bold mb-4">Beállítások</h2>

            <Card className="bg-dark border-0 shadow-lg rounded-4">
                <Card.Body className="p-0">
                    <Tabs defaultActiveKey="account" className="custom-tabs border-secondary p-3 pb-0" variant="underline">
                        
                        {/* 1. TAB: Account information */}
                        <Tab eventKey="account" title="Fiókadatok" className="p-4">
                            <h5 className="fw-bold text-info mb-4">Profilkép és Felhasználónév</h5>
                            
                            <Row className="align-items-center mb-5">
                                <Col xs="auto">
                                    <input type="file" accept="image/png, image/jpeg, image/webp" ref={fileInputRef} style={{ display: 'none' }} onChange={handleFileChange} />
                                    <div 
                                        className="position-relative" 
                                        style={{ width: '100px', height: '100px', cursor: 'pointer' }}
                                        onClick={() => !isUploadingImage && fileInputRef.current.click()}
                                    >
                                        {isUploadingImage ? (
                                            <div className="w-100 h-100 rounded-circle bg-secondary d-flex justify-content-center align-items-center">
                                                <Spinner animation="border" variant="light" />
                                            </div>
                                        ) : user.profilePictureUrl ? (
                                            <img src={user.profilePictureUrl} alt="Profil" className="w-100 h-100 rounded-circle object-fit-cover border border-2 border-info" />
                                        ) : (
                                            <div className="w-100 h-100 rounded-circle bg-secondary d-flex justify-content-center align-items-center border border-2 border-secondary">
                                                <span style={{ fontSize: '3rem' }}>👤</span>
                                            </div>
                                        )}
                                        {!isUploadingImage && <div className="position-absolute bottom-0 end-0 bg-info rounded-circle p-1" style={{ width: '28px', height: '28px', display: 'flex', justifyContent: 'center', alignItems: 'center' }}>✏️</div>}
                                    </div>
                                </Col>
                                <Col>
                                    <p className="text-secondary mb-0 small">Engedélyezett formátumok: JPG, PNG, WEBP. Maximum 5MB.</p>
                                    <Button variant="outline-light" size="sm" className="mt-2" onClick={() => fileInputRef.current.click()}>Kép módosítása</Button>
                                </Col>
                            </Row>

                            <Form>
                                <Form.Group className="mb-4">
                                    <Form.Label className="text-light opacity-75">Felhasználónév</Form.Label>
                                    <div className="d-flex gap-2">
                                        <Form.Control type="text" className="bg-secondary bg-opacity-25 text-light border-secondary" defaultValue={user.name} disabled />
                                        <Button variant="info" disabled>Mentés</Button>
                                    </div>
                                    <Form.Text className="text-secondary">A felhasználónév módosítása új azonosítót (tag) generál a neved mellé.</Form.Text>
                                </Form.Group>
                            </Form>
                        </Tab>

                        {/* 2. TAB: Learning Preferences */}
                        <Tab eventKey="learning" title="Tanulás" className="p-4">
                            <h5 className="fw-bold text-info mb-3">Adaptív Algoritmus</h5>
                            <p className="text-light opacity-75 mb-4">Itt felülírhatod az adaptív algoritmust, és beállíthatod, hogy milyen nehézségű leckéket szeretnél kapni.</p>
                            
                            <div style={{ minHeight: '50px' }}>
                                {diffMessage.text && <Alert variant={diffMessage.type} className="py-2 border-0 fw-bold">{diffMessage.text}</Alert>}
                            </div>

                            <Form onSubmit={handleSavePreferences}>
                                <Form.Group className="mb-4">
                                    <Form.Label className="fw-bold text-light opacity-75">Célzott Nehézség</Form.Label>
                                    <Form.Select className="bg-secondary bg-opacity-25 text-light border-secondary p-2" value={difficulty} onChange={(e) => setDifficulty(e.target.value)}>
                                        <option value="DYNAMIC" className="text-dark">🔵 Dinamikus (DYNAMIC) - Nehézség a teljesítményed alapján</option>
                                        <option value="EASY" className="text-dark">🟢 Kezdő (EASY) - Több kártyás feladat</option>
                                        <option value="MEDIUM" className="text-dark">🟡 Haladó (MEDIUM) - Vegyes feladatok</option>
                                        <option value="HARD" className="text-dark">🔴 Profi (HARD) - Csak gépelés</option>
                                    </Form.Select>
                                </Form.Group>
                                <Button variant="info" type="submit" className="fw-bold text-dark" disabled={isUpdatingDiff || difficulty === user.preferredDifficulty}>
                                    {isUpdatingDiff ? <Spinner size="sm" /> : 'Beállítások Mentése'}
                                </Button>
                            </Form>
                        </Tab>

                        {/* 3. TAB: Security */}
                        <Tab eventKey="security" title="Biztonság" className="p-4">
                            <h5 className="fw-bold text-danger mb-4">Érzékeny adatok</h5>
                            <p className="text-secondary">Ezen funkciók implementálása folyamatban van.</p>
                            {/* Ide jön majd az E-mail és Jelszó módosító form */}
                        </Tab>
                        
                    </Tabs>
                </Card.Body>
            </Card>
        </Container>
    );
};

export default Settings;