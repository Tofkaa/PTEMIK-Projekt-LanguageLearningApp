import React, { useState, useRef, useCallback, useEffect } from 'react';
import { Container, Card, Form, Button, Spinner, Alert, Tabs, Tab, Row, Col, Modal } from 'react-bootstrap';
import { useAuth } from '../context/AuthContext.jsx';
import { useLocation, useNavigate } from 'react-router-dom';
import api from '../services/api.jsx';
import Cropper from 'react-easy-crop';
import getCroppedImg from '../utils/cropImage.js';
import ImageCropControls from '../components/ImageCropControls';
import LearningFeedback from '../components/LearningFeedback';
import AccessibleCode from '../components/AccessibleCode';

const Settings = () => {
    const { user, setUser, logout } = useAuth();
    const isOAuthUser = user?.authProvider && user.authProvider !== 'LOCAL';
    const navigate = useNavigate();
    const location = useLocation();

    // --- STATES ---
    const [difficulty, setDifficulty] = useState(user?.preferredDifficulty || 'MEDIUM');
    const [isUpdatingDiff, setIsUpdatingDiff] = useState(false);
    const [diffMessage, setDiffMessage] = useState({ type: '', text: '' });

    const [isUploadingImage, setIsUploadingImage] = useState(false);
    const [imageMessage, setImageMessage] = useState({ type: '', text: '' });
    const fileInputRef = useRef(null);

    const [imageSrc, setImageSrc] = useState(null);
    const [showCropModal, setShowCropModal] = useState(false);
    const [crop, setCrop] = useState({ x: 0, y: 0 });
    const [zoom, setZoom] = useState(1);
    const [croppedAreaPixels, setCroppedAreaPixels] = useState(null);
    const [cropMediaSize, setCropMediaSize] = useState(null);
    const [cropViewportSize, setCropViewportSize] = useState(null);

    const MAX_FILE_SIZE_MB = 5;
    const ALLOWED_TYPES = ['image/jpeg', 'image/png', 'image/webp'];

    const [name, setName] = useState(user?.name || '');
    const [isUpdatingName, setIsUpdatingName] = useState(false);
    const [nameMessage, setNameMessage] = useState({ type: '', text: '' });

    const [passwords, setPasswords] = useState({ currentPassword: '', newPassword: '', confirmPassword: '' });
    const [isUpdatingPassword, setIsUpdatingPassword] = useState(false);
    const [passwordMessage, setPasswordMessage] = useState({ type: '', text: '' });

    const [emailStep, setEmailStep] = useState('REQUEST'); // 'REQUEST' vagy 'VERIFY'
    const [emailForm, setEmailForm] = useState({ newEmail: '', currentPassword: '', otpCode: '' });
    const [isProcessingEmail, setIsProcessingEmail] = useState(false);
    const [emailMessage, setEmailMessage] = useState({ type: '', text: '' });
    const emailCodeRef = useRef(null);
    const emailRequestRef = useRef(null);
    const previousEmailStep = useRef(emailStep);
    useEffect(() => {
        if (emailStep === 'VERIFY') emailCodeRef.current?.focus();
        else if (previousEmailStep.current === 'VERIFY') emailRequestRef.current?.focus();
        previousEmailStep.current = emailStep;
    }, [emailStep]);

    const [isExporting, setIsExporting] = useState(false);
    
    const [showDeleteModal, setShowDeleteModal] = useState(false);
    const [isDeleting, setIsDeleting] = useState(false);
    const [deletePassword, setDeletePassword] = useState('');
    const [deleteConfirmed, setDeleteConfirmed] = useState(false);
    const [deleteMessage, setDeleteMessage] = useState({ type: '', text: '' });


    // --- TABS LOGIC ---
    const [activeTab, setActiveTab] = useState(location.state?.settingsTab === 'account' ? 'account' : sessionStorage.getItem('settingsActiveTab') || 'account');

    const handleTabChange = (tabKey) => {
        setActiveTab(tabKey);
        sessionStorage.setItem('settingsActiveTab', tabKey);
    };

    // --- IMAGE UPLOAD LOGIC ---
    const handleFileChange = (e) => {
        const file = e.target.files[0];
        if (!file) return;

        setImageMessage({ type: '', text: '' });

        if (!ALLOWED_TYPES.includes(file.type)) {
            const extension = file.name.includes('.') 
                ? `.${file.name.split('.').pop().toLowerCase()}` 
                : 'ismeretlen';

            setImageMessage({
                type: 'danger',
                text: `Nem támogatott fájlformátum (${extension})! Kérlek, csak JPG, PNG vagy WEBP képet válassz.`
            });
            e.target.value = '';
            return;
        }

        const fileSizeMB = file.size / (1024 * 1024);
        if (fileSizeMB > MAX_FILE_SIZE_MB) {
            setImageMessage({
                type: 'danger',
                text: `A kiválasztott kép túl nagy (${fileSizeMB.toFixed(1)} MB)! A maximális megengedett méret ${MAX_FILE_SIZE_MB} MB.`
            });
            e.target.value = '';
            return;
        }

        const reader = new FileReader();
        reader.addEventListener('load', () => {
            setImageSrc(reader.result);
            setCrop({ x: 0, y: 0 });
            setZoom(1);
            setCroppedAreaPixels(null);
            setCropMediaSize(null);
            setCropViewportSize(null);
            setShowCropModal(true);
        });
        reader.readAsDataURL(file);
    };

    const onCropComplete = useCallback((croppedArea, croppedAreaPixels) => {
        setCroppedAreaPixels(croppedAreaPixels);
    }, []);


    const handleCropAndUpload = async () => {
        if (isUploadingImage || !imageSrc || !croppedAreaPixels) return;

        setIsUploadingImage(true);
        setImageMessage({ type: '', text: '' });

        try {
            const croppedBlob = await getCroppedImg(imageSrc, croppedAreaPixels);
            if (!croppedBlob) throw new Error('A kép kivágása nem sikerült.');
            const formData = new FormData();
            formData.append('file', croppedBlob, 'profile.jpg');

            const response = await api.post('/users/me/profile-picture', formData, {
                headers: { 'Content-Type': 'multipart/form-data' }
            });

            setUser((prevUser) => ({ ...prevUser, profilePictureUrl: response.data }));
            setShowCropModal(false);
            setImageSrc(null);
            setImageMessage({ type: 'success', text: 'Profilkép sikeresen frissítve! ✅' });
        } catch (error) {
            console.error('Hiba a profilkép feltöltésekor:', error);
            const backendError = error.response?.data;
            setImageMessage({
                type: 'danger',
                text: typeof backendError === 'string'
                    ? backendError
                    : 'Nem sikerült feltölteni a képet a szerverre. Kérlek, próbáld újra!'
            });
        } finally {
            setIsUploadingImage(false);
            if (fileInputRef.current) fileInputRef.current.value = '';
        }
    };

    const handleCloseCropModal = () => {
        if (isUploadingImage) return;
        setShowCropModal(false);
        setImageSrc(null);
        if (fileInputRef.current) fileInputRef.current.value = '';
    };

    // --- DIFFICULTY SAVE LOGIC ---
   const handleSavePreferences = async (e) => {
        e.preventDefault();
        if (isUpdatingDiff || difficulty === user.preferredDifficulty) return;
        setIsUpdatingDiff(true);
        setDiffMessage({ type: '', text: '' });

        try {
            await api.put('/users/me/preferences', { preferredDifficulty: difficulty });
            
            setUser((prevUser) => ({ 
                ...prevUser, 
                preferredDifficulty: difficulty 
            }));
            
            setDiffMessage({ type: 'success', text: 'Tanulási beállítások elmentve!' });
        } catch (err) {
            setDiffMessage({ type: 'danger', text: 'Hiba történt a beállítások mentésekor.' }, err);
        } finally {
            setIsUpdatingDiff(false);
        }
    };

    // --- USERNAME SAVE LOGIC ---
    const handleSaveName = async (e) => {
        e.preventDefault();
        if (isUpdatingName) return;
        const trimmed = name.trim();
        if (trimmed === user.name) return;
        if (trimmed.length < 3) {
            setNameMessage({ type: 'danger', text: 'A névnek legalább 3 karakternek kell lennie!' });
            return;
        }

        setIsUpdatingName(true);
        setNameMessage({ type: '', text: '' });

        try {
            const response = await api.put('/users/me/name', { name: trimmed });
            
            // Frissítjük a globális user objektumot az új névvel és az új generált userTag-gel
            setUser((prevUser) => ({
                ...prevUser,
                name: response.data.name,
                userTag: response.data.userTag
            }));

            setNameMessage({ 
                type: 'success', 
                text: `Név sikeresen frissítve! Az új azonosítód: ${response.data.name} #${response.data.userTag}`
            });
        } catch (err) {
            const errorMsg = err.response?.data?.message || 'Hiba történt a név módosításakor.';
            setNameMessage({ type: 'danger', text: errorMsg });
        } finally {
            setIsUpdatingName(false);
        }
    };

    // --- PASSWORD CHANGE LOGIC
    const handleSavePassword = async (e) => {
        e.preventDefault();
        if (isUpdatingPassword) return;
        setPasswordMessage({ type: '', text: '' });

        if (passwords.newPassword !== passwords.confirmPassword) {
            setPasswordMessage({ type: 'danger', text: 'Az új jelszavak nem egyeznek!' });
            return;
        }

        if (passwords.newPassword.length < 6) {
            setPasswordMessage({ type: 'danger', text: 'Az új jelszónak legalább 6 karakternek kell lennie!' });
            return;
        }

        setIsUpdatingPassword(true);
        try {
            await api.put('/users/me/password', {
                currentPassword: passwords.currentPassword,
                newPassword: passwords.newPassword
            });

            setPasswordMessage({ type: 'success', text: 'Jelszó sikeresen módosítva!' });
            setPasswords({ currentPassword: '', newPassword: '', confirmPassword: '' });
        } catch (err) {
            const errorMsg = err.response?.data?.message || err.response?.data || 'Hiba történt a jelszó módosításakor.';
            setPasswordMessage({ type: 'danger', text: typeof errorMsg === 'string' ? errorMsg : 'Hiba történt!' });
        } finally {
            setIsUpdatingPassword(false);
        }
    };

    const handleRequestEmailChange = async (e) => {
        e.preventDefault();
        if (isProcessingEmail) return;
        setEmailMessage({ type: '', text: '' });
        setIsProcessingEmail(true);

        try {
            await api.post('/users/me/email-change/request', {
                newEmail: emailForm.newEmail.trim(),
                currentPassword: emailForm.currentPassword
            });
            setEmailStep('VERIFY');
            setEmailMessage({ 
                type: 'info', 
                text: `Küldtünk egy 6-jegyű ellenőrző kódot a(z) ${emailForm.newEmail} címre! (15 percig érvényes)` 
            });
        } catch (err) {
            const errorMsg = err.response?.data?.message || err.response?.data || 'Hiba történt a kérés indításakor.';
            setEmailMessage({ type: 'danger', text: typeof errorMsg === 'string' ? errorMsg : 'Hiba történt!' });
        } finally {
            setIsProcessingEmail(false);
        }
    };

    const handleVerifyEmailChange = async (e) => {
        e.preventDefault();
        if (isProcessingEmail || emailForm.otpCode.length !== 6) return;
        setEmailMessage({ type: '', text: '' });
        setIsProcessingEmail(true);

        try {
            await api.post('/users/me/email-change/verify', {
                otpCode: emailForm.otpCode.trim()
            });
            await logout();
            navigate('/login', { replace: true, state: { successMessage: 'E-mail cím sikeresen frissítve! Jelentkezz be az új e-mail címeddel.' } });
        } catch (err) {
            const errorMsg = err.response?.data?.message || err.response?.data || 'Hibás vagy lejárt ellenőrző kód.';
            setEmailMessage({ type: 'danger', text: typeof errorMsg === 'string' ? errorMsg : 'Hiba történt!' });
            setIsProcessingEmail(false);
        }
    };

    // --- GDPR: DATA EXPORT ---
    const handleExportData = async () => {
        setIsExporting(true);
        try {
            const response = await api.get('/users/me/export');
            
            // 1. JSON formatting
            const dataStr = JSON.stringify(response.data, null, 2);
            
            // 2. Blob creation in memory
            const blob = new Blob([dataStr], { type: 'application/json' });
            const url = window.URL.createObjectURL(blob);
            
            // 3. Click to download
            const link = document.createElement('a');
            link.href = url;
            link.download = `my_languageapp_data_${user.name.replace(/\s+/g, '_')}.json`;
            document.body.appendChild(link);
            link.click();
            
            // 4. Cleanup
            document.body.removeChild(link);
            window.URL.revokeObjectURL(url);
            
        } catch (error) {
            console.error("Adat letöltés hiba:", error);
            alert("Nem sikerült letölteni az adatokat. Kérlek, próbáld újra később.");
        } finally {
            setIsExporting(false);
        }
    };

    // --- GDPR: SOFT DELETE ---
    const handleRequestOAuthDeletion = async () => {
        if (isDeleting) return;
        setIsDeleting(true);
        setDeleteMessage({ type: '', text: '' });
        try {
            const { data } = await api.post('/users/me/deletion/request');
            setDeleteMessage({ type: 'info', text: `A jóváhagyó levél küldését elindítottuk a(z) ${data.email} címre. A kézbesítés eltarthat egy ideig. A linket ebben a böngészőprofilban nyisd meg, és addig ne jelentkezz ki.` });
        } catch (error) {
            setDeleteMessage({ type: 'danger', text: error.response?.data?.message || 'A jóváhagyó link kérése nem sikerült. Próbáld újra később.' });
        } finally {
            setIsDeleting(false);
        }
    };

   const handleDeleteAccount = async () => {
        if (isDeleting) return;
        if (isOAuthUser || !deletePassword || !deleteConfirmed) {
            setDeleteMessage({ type: 'danger', text: 'Add meg a jelenlegi jelszavadat, és erősítsd meg a törlési szándékodat a jelölőnégyzettel.' });
            return;
        }

        setIsDeleting(true);
        setDeleteMessage({ type: '', text: '' });

        try {
            await api.delete('/users/me', { data: { password: deletePassword, confirmed: deleteConfirmed } });
            setShowDeleteModal(false);
            await logout(true); 
        } catch (error) {
            console.error("Fiók törlése hiba:", error);
            const errorMsg = error.response?.data?.message || error.response?.data || 'Hiba történt a fióktörlés során.';
            setDeleteMessage({ type: 'danger', text: typeof errorMsg === 'string' ? errorMsg : 'Hibás megerősítés vagy hálózati hiba.' });
        } finally {
            setIsDeleting(false);
        }
    };

    if (!user) return null;

    return (
        <Container className="mt-5 pt-4 text-light pb-5" style={{ maxWidth: '900px' }}>
            <Button variant="link" className="text-info text-decoration-none p-0 mb-3 fw-bold" onClick={() => navigate('/profile')}>
                ⬅️ Vissza a profilomra
            </Button>
            
            <h1 className="h2 fw-bold mb-4">Beállítások</h1>

            <Card className="bg-dark border-0 shadow-lg rounded-4">
                <Card.Body className="p-0">
                    <Tabs 
                        id="settings-tabs"
                        aria-label="Beállítások kategóriái"
                        activeKey={activeTab} 
                        onSelect={handleTabChange} 
                        className="custom-tabs border-secondary p-3 pb-0" 
                        variant="underline"
                    >
                        
                        {/* 1. TAB: Account information */}
                        <Tab eventKey="account" title="Fiókadatok" className="p-4">
                            <h2 className="h5 fw-bold text-info mb-4">Profilkép és Felhasználónév</h2>
                            <LearningFeedback label="Profilkép" error={!showCropModal && imageMessage.type === 'danger' ? imageMessage.text : null}
                                feedback={imageMessage.type === 'success' ? { type: 'success', msg: imageMessage.text } : null} />
                            <Row className="align-items-center mb-5">
                                <Col xs="auto">
                                    <input type="file" aria-label="Új profilkép fájlja" accept="image/png, image/jpeg, image/webp" ref={fileInputRef} style={{ display: 'none' }} onChange={handleFileChange} />
                                    <button type="button" aria-label="Profilkép kiválasztása" disabled={isUploadingImage}
                                        className="position-relative border-0 bg-transparent p-0"
                                        style={{ width: '100px', height: '100px', cursor: 'pointer' }}
                                        onClick={() => !isUploadingImage && fileInputRef.current.click()}
                                    >
                                        {isUploadingImage ? (
                                            <span className="w-100 h-100 rounded-circle bg-secondary d-flex justify-content-center align-items-center">
                                                <Spinner aria-hidden="true" animation="border" variant="light" />
                                            </span>
                                        ) : user.profilePictureUrl ? (
                                            <img src={user.profilePictureUrl} alt="" className="w-100 h-100 rounded-circle object-fit-cover border border-2 border-info" />
                                        ) : (
                                            <span className="w-100 h-100 rounded-circle bg-secondary d-flex justify-content-center align-items-center border border-2 border-secondary">
                                                <span aria-hidden="true" style={{ fontSize: '3rem' }}>👤</span>
                                            </span>
                                        )}
                                        {!isUploadingImage && <span aria-hidden="true" className="position-absolute bottom-0 end-0 bg-info rounded-circle p-1" style={{ width: '28px', height: '28px', display: 'flex', justifyContent: 'center', alignItems: 'center' }}>✏️</span>}
                                    </button>
                                </Col>
                                <Col>
                                    <p className="text-secondary mb-0 small">Engedélyezett formátumok: JPG, PNG, WEBP. Maximum 5MB.</p>
                                    <Button variant="outline-secondary" size="sm" disabled={isUploadingImage} className="mt-2" onClick={() => fileInputRef.current.click()}>Kép módosítása</Button>
                                </Col>
                            </Row>

                            <LearningFeedback label="Felhasználónév" error={nameMessage.type === 'danger' ? nameMessage.text : ''}
                                feedback={nameMessage.type === 'success' ? { type: 'success', msg: nameMessage.text } : null} />

                            <Form onSubmit={handleSaveName}>
                                <Form.Group className="mb-4" controlId="settings-name">
                                    <Form.Label className="text-light opacity-75">
                                        Felhasználónév <span className="text-info ms-1">#{user.userTag}</span>
                                    </Form.Label>
                                    <div className="d-flex flex-wrap flex-sm-nowrap gap-2">
                                        <Form.Control 
                                            type="text" 
                                            className="bg-secondary bg-opacity-25 text-light border-secondary" 
                                            value={name}
                                            autoComplete="nickname"
                                            aria-describedby="settings-name-help"
                                            onChange={(e) => setName(e.target.value)}
                                            minLength={3}
                                            maxLength={30}
                                            required
                                            readOnly={isUpdatingName}
                                        />
                                        <Button 
                                            variant="info" 
                                            type="submit" 
                                            className="fw-bold text-dark px-4 flex-shrink-0 text-nowrap"
                                            aria-disabled={isUpdatingName || name.trim() === user.name || name.trim().length < 3}
                                            aria-busy={isUpdatingName}
                                        >
                                            {isUpdatingName && <Spinner size="sm" aria-hidden="true" className="me-2" />}Mentés
                                        </Button>
                                    </div>
                                    <Form.Text id="settings-name-help" className="text-secondary">
                                        A felhasználónév módosítása új, négyjegyű azonosítót (#tag) generál a neved mellé, a barátkódod (<AccessibleCode code={user.friendCode} />) viszont változatlan marad.
                                    </Form.Text>
                                </Form.Group>
                            </Form>
                        </Tab>

                        {/* 2. TAB: Learning Preferences */}
                        <Tab eventKey="learning" title="Tanulás" className="p-4">
                            <h2 className="h5 fw-bold text-info mb-3">Adaptív Algoritmus</h2>
                            <p className="text-light opacity-75 mb-4">Itt felülírhatod az adaptív algoritmust, és beállíthatod, hogy milyen nehézségű leckéket szeretnél kapni.</p>
                            
                            <div style={{ minHeight: '50px' }}>
                                <LearningFeedback label="Tanulási beállítások" error={diffMessage.type === 'danger' ? diffMessage.text : ''}
                                    feedback={diffMessage.type === 'success' ? { type: 'success', msg: diffMessage.text } : null} />
                            </div>

                            <Form onSubmit={handleSavePreferences}>
                                <Form.Group className="mb-4" controlId="preferred-difficulty">
                                    <Form.Label className="fw-bold text-light opacity-75">Célzott Nehézség</Form.Label>
                                    <Form.Select className="border-secondary p-2" value={difficulty} onChange={(e) => setDifficulty(e.target.value)}>
                                        <option value="DYNAMIC">🔵 Dinamikus (DYNAMIC) - Nehézség a teljesítményed alapján</option>
                                        <option value="EASY">Kezdő (EASY) – Kezdő szintű feladatok</option>
                                        <option value="MEDIUM">Haladó (MEDIUM) – Haladó szintű feladatok</option>
                                        <option value="HARD">Profi (HARD) – Profi szintű feladatok</option>
                                    </Form.Select>
                                </Form.Group>
                                <Button variant="info" type="submit" className="fw-bold text-dark" aria-disabled={isUpdatingDiff || difficulty === user.preferredDifficulty} aria-busy={isUpdatingDiff}>
                                    {isUpdatingDiff && <Spinner size="sm" aria-hidden="true" className="me-2" />}Beállítások Mentése
                                </Button>
                            </Form>
                        </Tab>

                        {/* 3. TAB: Security */}
                        <Tab eventKey="security" title="Biztonság" className="p-4">
                            
                            {/* --- E-MAIL ADDRESS CHANGE (OTP FLOW) --- */}
                            <h2 className="h5 fw-bold text-info mb-3"><span aria-hidden="true">📧 </span>E-mail cím módosítása</h2>
                            <p className="text-secondary small mb-3">
                                Jelenlegi e-mail címed: <strong className="text-light">{user.email}</strong>
                            </p>

                            <LearningFeedback label="E-mail cím" error={emailMessage.type === 'danger' ? emailMessage.text : ''}
                                feedback={emailMessage.type !== 'danger' && emailMessage.text ? { type: emailMessage.type, msg: emailMessage.text } : null} />

                            {emailStep === 'REQUEST' ? (
                                <Form onSubmit={handleRequestEmailChange} className="mb-5 pb-4 border-bottom border-secondary" style={{ maxWidth: '500px' }}>
                                    <Form.Group className="mb-3" controlId="settings-new-email">
                                        <Form.Label className="text-light opacity-75">Új e-mail cím</Form.Label>
                                        <Form.Control 
                                            type="email" 
                                            autoComplete="email"
                                            className="bg-secondary bg-opacity-25 text-light border-secondary"
                                            placeholder="uj.cim@pelda.hu"
                                            aria-describedby="settings-email-request-help"
                                            value={emailForm.newEmail}
                                            onChange={(e) => setEmailForm({ ...emailForm, newEmail: e.target.value })}
                                            required
                                            readOnly={isProcessingEmail}
                                        />
                                    </Form.Group>

                                    <Form.Group className="mb-4" controlId="settings-email-password">
                                        <Form.Label className="text-light opacity-75">Jelenlegi jelszó (megerősítéshez)</Form.Label>
                                        <Form.Control 
                                            type="password" 
                                            autoComplete="current-password"
                                            className="bg-secondary bg-opacity-25 text-light border-secondary"
                                            value={emailForm.currentPassword}
                                            onChange={(e) => setEmailForm({ ...emailForm, currentPassword: e.target.value })}
                                            required
                                            readOnly={isProcessingEmail}
                                        />
                                    </Form.Group>

                                    <p id="settings-email-request-help" className="small text-secondary">
                                        Az ellenőrző kód a kérés indításától számított 15 percig érvényes. Lejárat után új kódot kérhetsz.
                                    </p>
                                    <Button 
                                        ref={emailRequestRef}
                                        variant="info" 
                                        type="submit" 
                                        className="fw-bold text-dark px-4"
                                        aria-disabled={isProcessingEmail || !emailForm.newEmail || !emailForm.currentPassword}
                                        aria-busy={isProcessingEmail}
                                    >
                                        {isProcessingEmail && <Spinner size="sm" aria-hidden="true" className="me-2" />}Ellenőrző kód küldése
                                    </Button>
                                </Form>
                            ) : (
                                <Form onSubmit={handleVerifyEmailChange} className="mb-5 pb-4 border-bottom border-secondary" style={{ maxWidth: '500px' }}>
                                    <Form.Group className="mb-4" controlId="settings-email-code">
                                        <Form.Label className="text-light opacity-75">6-jegyű ellenőrző kód (OTP)</Form.Label>
                                        <Form.Control 
                                            ref={emailCodeRef}
                                            type="text" 
                                            inputMode="numeric"
                                            autoComplete="one-time-code"
                                            aria-describedby="settings-code-help"
                                            className="bg-secondary bg-opacity-25 text-light border-info fs-4 text-center font-monospace tracking-wide"
                                            placeholder="123456"
                                            maxLength={6}
                                            value={emailForm.otpCode}
                                            onChange={(e) => setEmailForm({ ...emailForm, otpCode: e.target.value.replace(/\D/g, '') })}
                                            required
                                            readOnly={isProcessingEmail}
                                        />
                                        <Form.Text id="settings-code-help" className="text-secondary">
                                            Add meg az új e-mail címre küldött 6 számjegyű kódot. A kódot be is másolhatod.
                                            {' '}Ha lejárt, a Mégse gombbal visszatérhetsz az új kód kéréséhez; a megadott adataid megmaradnak.
                                        </Form.Text>
                                    </Form.Group>

                                    <div className="d-flex gap-2">
                                        <Button 
                                            variant="success" 
                                            type="submit" 
                                            className="fw-bold px-4"
                                            aria-disabled={isProcessingEmail || emailForm.otpCode.length !== 6}
                                            aria-busy={isProcessingEmail}
                                        >
                                            {isProcessingEmail && <Spinner size="sm" aria-hidden="true" className="me-2" />}Kód megerősítése
                                        </Button>
                                        <Button 
                                            variant="outline-secondary" 
                                            type="button"
                                            disabled={isProcessingEmail}
                                            onClick={() => {
                                                setEmailStep('REQUEST');
                                                setEmailMessage({ type: '', text: '' });
                                                setEmailForm({ ...emailForm, otpCode: '' });
                                            }}
                                        >
                                            Mégse
                                        </Button>
                                    </div>
                                </Form>
                            )}

                            {/* --- PASSWORD CHANGE --- */}
                            <h2 className="h5 fw-bold text-info mb-3"><span aria-hidden="true">🔒 </span>Jelszó módosítása</h2>
                            
                            <LearningFeedback label="Jelszó" error={passwordMessage.type === 'danger' ? passwordMessage.text : ''}
                                feedback={passwordMessage.type === 'success' ? { type: 'success', msg: passwordMessage.text } : null} />

                            <Form onSubmit={handleSavePassword} style={{ maxWidth: '500px' }}>
                                <Form.Group className="mb-3" controlId="settings-current-password">
                                    <Form.Label className="text-light opacity-75">Jelenlegi jelszó</Form.Label>
                                    <Form.Control 
                                        type="password" 
                                        autoComplete="current-password"
                                        className="bg-secondary bg-opacity-25 text-light border-secondary"
                                        value={passwords.currentPassword}
                                        onChange={(e) => setPasswords({ ...passwords, currentPassword: e.target.value })}
                                        required
                                        readOnly={isUpdatingPassword}
                                    />
                                </Form.Group>

                                <Form.Group className="mb-3" controlId="settings-new-password">
                                    <Form.Label className="text-light opacity-75">Új jelszó</Form.Label>
                                    <Form.Control 
                                        type="password" 
                                        autoComplete="new-password"
                                        className="bg-secondary bg-opacity-25 text-light border-secondary"
                                        placeholder="Legalább 6 karakter"
                                        value={passwords.newPassword}
                                        onChange={(e) => setPasswords({ ...passwords, newPassword: e.target.value })}
                                        minLength={6}
                                        required
                                        readOnly={isUpdatingPassword}
                                    />
                                </Form.Group>

                                <Form.Group className="mb-4" controlId="settings-confirm-password">
                                    <Form.Label className="text-light opacity-75">Új jelszó megerősítése</Form.Label>
                                    <Form.Control 
                                        type="password" 
                                        autoComplete="new-password"
                                        className="bg-secondary bg-opacity-25 text-light border-secondary"
                                        value={passwords.confirmPassword}
                                        onChange={(e) => setPasswords({ ...passwords, confirmPassword: e.target.value })}
                                        minLength={6}
                                        required
                                        readOnly={isUpdatingPassword}
                                    />
                                </Form.Group>

                                <Button 
                                    variant="info" 
                                    type="submit" 
                                    className="fw-bold text-dark px-4"
                                    aria-disabled={isUpdatingPassword || !passwords.currentPassword || !passwords.newPassword || !passwords.confirmPassword}
                                    aria-busy={isUpdatingPassword}
                                >
                                    {isUpdatingPassword && <Spinner size="sm" aria-hidden="true" className="me-2" />}Jelszó frissítése
                                </Button>
                            </Form>
                        </Tab>

                        {/* 4. TAB: GDPR */}
                        <Tab eventKey="privacy" title="Adatvédelem" className="p-4">
                            <h2 className="h5 fw-bold text-info mb-3"><span aria-hidden="true">🛡️ </span>Adatvédelmi Beállítások</h2>
                            <p className="text-secondary small mb-4">
                                Itt kezelheted a GDPR-hoz kapcsolódó jogaidat: letöltheted a rendszerben tárolt adataidat, vagy véglegesen törölheted a fiókodat.
                            </p>

                           {/* Legal docs */}
                            <div className="mb-4 pb-4 border-bottom border-secondary">
                                <h3 className="h6 text-light fw-bold">Jogi dokumentumok</h3>
                                <p className="text-secondary small mb-3">
                                    Bármikor újraolvashatod a szolgáltatás használatára vonatkozó szabályzatokat.
                                </p>
                                <div className="d-flex gap-3 flex-wrap">
                                    <Button 
                                        variant="outline-secondary" 
                                        size="sm" 
                                        className="fw-bold text-light"
                                        onClick={() => navigate('/terms')}
                                    >
                                        📄 Általános Szerződési Feltételek
                                    </Button>
                                    <Button 
                                        variant="outline-secondary" 
                                        size="sm" 
                                        className="fw-bold text-light"
                                        onClick={() => navigate('/privacy')}
                                    >
                                        🔒 Adatkezelési Tájékoztató
                                    </Button>
                                </div>
                            </div>

                            <div className="mb-5 pb-4 border-bottom border-secondary">
                                <h3 className="h6 text-light fw-bold">Adataim Letöltése</h3>
                                <p className="text-secondary small">
                                    Kérj egy teljes másolatot a fiókodról, a tanulási előzményeidről és a profiladataidról, amelyeket gépileg olvasható JSON formátumban kapsz meg.
                                </p>
                                <Button 
                                    variant="outline-info" 
                                    className="fw-bold px-4" 
                                    onClick={handleExportData}
                                    disabled={isExporting}
                                >
                                    {isExporting ? <><Spinner size="sm" className="me-2" />Letöltés folyamatban...</> : '📥 Adat letöltése (.json)'}
                                </Button>
                            </div>

                            <div>
                                <h3 className="h6 text-danger fw-bold">Fiók Törlése</h3>
                                <p className="text-secondary small">
                                    A fiókod és a személyes adataid (PII) véglegesen törlésre kerülnek. A tanulási eredményeid név nélkül (anonimizálva) megmaradnak, hogy az osztálytermek és a közösség statisztikái ne sérüljenek. <strong className="text-light">Ez a művelet nem vonható vissza!</strong>
                                </p>
                                <Button 
                                    variant="danger" 
                                    className="fw-bold px-4" 
                                    onClick={() => {
                                        setDeletePassword('');
                                        setDeleteConfirmed(false);
                                        setDeleteMessage({type:'', text:''});
                                        setShowDeleteModal(true);
                                    }}
                                >
                                    🗑️ Fiók végleges törlése
                                </Button>
                            </div>
                        </Tab>
                    </Tabs>
                </Card.Body>
            </Card>
            {/* --- PROFILE PICTURE CROPPING AND ZOOMING MODAL --- */}
            <Modal show={showCropModal} onHide={handleCloseCropModal} aria-labelledby="avatar-crop-title" centered backdrop="static" contentClassName="bg-dark text-light border-secondary">
                <Modal.Header closeButton closeLabel="Ablak bezárása" className="border-secondary">
                    <Modal.Title id="avatar-crop-title" className="fw-bold app-accent-text">Profilkép beállítása</Modal.Title>
                </Modal.Header>
                <Modal.Body className="p-4">
                    <div className="position-relative w-100 rounded-3 overflow-hidden mb-4" style={{ height: '320px', backgroundColor: '#111' }}>
                        {imageSrc && (
                            <Cropper
                                image={imageSrc}
                                crop={crop}
                                zoom={zoom}
                                aspect={1}
                                cropShape="round"
                                showGrid={false}
                                onCropChange={next => { if (!isUploadingImage) setCrop(next); }}
                                onCropComplete={onCropComplete}
                                onZoomChange={next => { if (!isUploadingImage) setZoom(next); }}
                                onMediaLoaded={setCropMediaSize}
                                onCropSizeChange={setCropViewportSize}
                                cropperProps={{ role: 'group', 'aria-label': 'Profilkép kivágási területe', tabIndex: isUploadingImage ? -1 : 0 }}
                                mediaProps={{ alt: 'Profilkép kivágási előnézete' }}
                            />
                        )}
                    </div>

                    <ImageCropControls crop={crop} onCropChange={setCrop} zoom={zoom} onZoomChange={setZoom}
                        bounds={{ x: cropMediaSize && cropViewportSize ? Math.abs((cropMediaSize.width * zoom - cropViewportSize.width) / 2) : 0,
                            y: cropMediaSize && cropViewportSize ? Math.abs((cropMediaSize.height * zoom - cropViewportSize.height) / 2) : 0 }}
                        disabled={isUploadingImage || !cropMediaSize || !cropViewportSize} />
                    <LearningFeedback error={imageMessage.type === 'danger' ? imageMessage.text : null} />
                    <div role="status" aria-atomic="true">{isUploadingImage ? 'Profilkép feltöltése folyamatban.' : ''}</div>
                </Modal.Body>
                <Modal.Footer className="border-secondary">
                    <Button variant="outline-secondary" onClick={handleCloseCropModal} disabled={isUploadingImage}>
                        Mégse
                    </Button>
                    <Button variant="info" className="fw-bold text-dark px-4" onClick={handleCropAndUpload}
                        disabled={!croppedAreaPixels} aria-disabled={isUploadingImage} aria-busy={isUploadingImage}>
                        {isUploadingImage ? <><Spinner size="sm" className="me-2" />Feltöltés...</> : 'Kivágás és Mentés'}
                    </Button>
                </Modal.Footer>
            </Modal>
            {/* --- GDPR: ACCOUNT DELETION CONFIRMATION  MODAL --- */}
           <Modal show={showDeleteModal} onHide={() => !isDeleting && setShowDeleteModal(false)} aria-labelledby="account-delete-title" centered backdrop="static" contentClassName="bg-dark text-light border-danger">
                <Modal.Header closeButton closeLabel="Ablak bezárása" className="border-secondary bg-danger bg-opacity-25">
                    <Modal.Title as="h2" id="account-delete-title" className="h4 fw-bold text-danger"><span aria-hidden="true">⚠️</span> Fiók Törlése</Modal.Title>
                </Modal.Header>
                <Modal.Body className="p-4">
                    <LearningFeedback error={deleteMessage.type === 'danger' ? deleteMessage.text : ''}
                        feedback={deleteMessage.type === 'info' ? { type: 'info', msg: deleteMessage.text } : null} />
                    
                    <p className="fs-5">Biztosan törölni szeretnéd a fiókodat?</p>
                    <p className="text-secondary small">
                        Ez a művelet <strong>VÉGLEGES</strong> és nem vonható vissza! Fiókod megszűnik, profiladataid anonimizálódnak.
                        {' '}A korábbi tanulási eredmények törölt felhasználóhoz kapcsolva megmaradhatnak.
                        {!isOAuthUser && ' A folytatáshoz kérlek, add meg a jelenlegi jelszavadat, és erősítsd meg a törlési szándékodat.'}
                    </p>
                    
                    <Form.Group className="mt-4">
                        {!isOAuthUser ? (
                            <>
                                <Form.Label htmlFor="delete-password" className="text-light opacity-75 small">Jelszó</Form.Label>
                                <Form.Control 
                                    id="delete-password"
                                    type="password" 
                                    className="bg-secondary bg-opacity-25 text-light border-danger mb-3"
                                    value={deletePassword}
                                    onChange={(e) => setDeletePassword(e.target.value)}
                                    disabled={isDeleting}
                                    autoComplete="current-password"
                                />
                                <Form.Check
                                    id="delete-account-confirmation"
                                    type="checkbox"
                                    label="Megértettem a fióktörlés következményeit, és kérem a fiókom törlését."
                                    checked={deleteConfirmed}
                                    onChange={(e) => setDeleteConfirmed(e.target.checked)}
                                    disabled={isDeleting}
                                />
                            </>
                        ) : (
                            <>
                                <p>{user.authProvider} fiókodhoz jóváhagyó linket küldünk a már rögzített e-mail-címedre: <strong>{user.email}</strong>.</p>
                                <p className="small text-secondary">A link 15 percig érvényes. A levél kérése és a link megnyitása még nem töröl fiókot. Ugyanebben a böngészőprofilban megnyitva külön áttekintés, megerősítő jelölőnégyzet és végső törlésgomb következik. Új belépés után új link szükséges.</p>
                            </>
                        )}
                    </Form.Group>
                </Modal.Body>
                <Modal.Footer className="border-secondary">
                    <Button variant="outline-secondary" onClick={() => setShowDeleteModal(false)} disabled={isDeleting}>
                        Mégse
                    </Button>
                    <Button 
                        variant="danger" 
                        className="fw-bold px-4" 
                        onClick={isOAuthUser ? handleRequestOAuthDeletion : handleDeleteAccount}
                        disabled={isDeleting || (!isOAuthUser && (!deletePassword || !deleteConfirmed))}
                    >
                        {isDeleting ? <><Spinner size="sm" className="me-2" aria-hidden="true" />{isOAuthUser ? 'Kérés folyamatban...' : 'Törlés folyamatban...'}</> : isOAuthUser ? 'Jóváhagyó link kérése' : 'Igen, törlöm a fiókom!'}
                    </Button>
                </Modal.Footer>
            </Modal>
        </Container>
    );
};

export default Settings;
