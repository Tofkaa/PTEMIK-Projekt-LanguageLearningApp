import React, { useState, useEffect } from 'react';
import { Card, Form, Button, Alert, Spinner, Accordion, Badge, ListGroup, Modal } from 'react-bootstrap';
import { adminApi } from '../../services/adminApi';

const CurriculumManager = () => {
    const [selectedFile, setSelectedFile] = useState(null);
    const [isLoading, setIsLoading] = useState(false);
    const [message, setMessage] = useState({ text: '', type: '' });
    const [topics, setTopics] = useState([]);
    const [isLoadingTopics, setIsLoadingTopics] = useState(true);
    const [previewModal, setPreviewModal] = useState({ show: false, exercise: null });

    useEffect(() => {
        fetchTopics();
    }, []);

    const fetchTopics = async () => {
        setIsLoadingTopics(true);
        try {
            const response = await adminApi.getAllTopics();
            let data = Array.isArray(response.data) ? response.data : [];
            
            // Kezdeti stabil rendezés
            data.sort((a, b) => a.topicName.localeCompare(b.topicName));
            data.forEach(topic => {
                if (topic.lessons) {
                    topic.lessons.sort((a, b) => a.title.localeCompare(b.title));
                }
            });

            setTopics(data);
        } catch (error) {
            console.error("Hiba a tananyagok betöltésekor:", error);
            setTopics([]);
        } finally {
            setIsLoadingTopics(false);
        }
    };

    const handleFileChange = (e) => {
        const file = e.target.files[0];
        if (file && file.type === "application/json") {
            setSelectedFile(file); setMessage({ text: '', type: '' });
        } else {
            setSelectedFile(null); setMessage({ text: 'Kérlek, csak érvényes .json fájlt tölts fel!', type: 'danger' });
        }
    };

    const handleUpload = async () => {
        if (!selectedFile) return;
        setIsLoading(true); setMessage({ text: '', type: '' });
        const reader = new FileReader();
        reader.onload = async (e) => {
            try {
                const jsonData = JSON.parse(e.target.result);
                await adminApi.importCurriculum(jsonData);
                setMessage({ text: 'Tananyag sikeresen importálva! 🎉', type: 'success' });
                setSelectedFile(null);
                document.getElementById('json-upload-input').value = '';
                fetchTopics();
            } catch (error) {
                setMessage({ text: 'Hiba az importálás során.', type: 'danger', error });
            } finally { setIsLoading(false); }
        };
        reader.readAsText(selectedFile);
    };

    const handleStatusToggle = async (id, type, name, currentStatus) => {
        const nextStatus = !currentStatus;
        if (window.confirm(`Biztosan ${nextStatus ? 'visszaállítod' : 'felfüggeszted'} ezt a(z) ${type} elemet? (${name})`)) {
            try {
                // LOKÁLIS ÁLLAPOTFRISSÍTÉS - Teljesen kiiktatjuk a UI ugrálást és eltűnést!
                if (type === 'Témakör') {
                    await adminApi.toggleTopicStatus(id, nextStatus);
                    setTopics(prev => prev.map(t => t.topicId === id ? { ...t, active: nextStatus } : t));
                }
                if (type === 'Lecke') {
                    await adminApi.toggleLessonStatus(id, nextStatus);
                    setTopics(prev => prev.map(t => ({
                        ...t,
                        lessons: t.lessons ? t.lessons.map(l => l.lessonId === id ? { ...l, active: nextStatus } : l) : []
                    })));
                }
                if (type === 'Feladat') {
                    await adminApi.toggleExerciseStatus(id, nextStatus);
                    setTopics(prev => prev.map(t => ({
                        ...t,
                        lessons: t.lessons ? t.lessons.map(l => ({
                            ...l,
                            exercises: l.exercises ? l.exercises.map(e => e.exerciseId === id ? { ...e, active: nextStatus } : e) : []
                        })) : []
                    })));
                }
                
                setMessage({ text: `${type} státusza frissítve!`, type: 'success' });
            } catch (error) {
                setMessage({ text: `Hiba a(z) ${type} módosításakor.`, type: 'danger', error});
            }
        }
    };

    const getPreview = (content) => {
        if (!content) return "Nincs elérhető tartalom...";
        if (content.question) return content.question;
        return "Összetett feladat (kép / hang)...";
    };

    const getDifficultyBadge = (diff) => {
        switch(diff) {
            case 'EASY': return 'success';
            case 'MEDIUM': return 'warning text-dark';
            case 'HARD': return 'danger';
            default: return 'secondary';
        }
    };

    const handleShowPreview = (exercise) => {
        setPreviewModal({ show: true, exercise });
    };

    return (
        <div>
            {message.text && <Alert variant={message.type} className="shadow-sm rounded-4">{message.text}</Alert>}

            <Card className="bg-dark border-secondary shadow-lg mb-4 rounded-4">
                <Card.Body className="p-4">
                    <h5 className="text-info fw-bold mb-3">Tananyag JSON Importálása</h5>
                    <Form.Group className="mb-4">
                        <Form.Control id="json-upload-input" type="file" accept=".json" onChange={handleFileChange} className="bg-dark text-light border-secondary rounded-3" />
                    </Form.Group>
                    <Button variant="info" className="fw-bold px-4 text-dark rounded-pill" onClick={handleUpload} disabled={!selectedFile || isLoading}>
                        {isLoading ? <Spinner as="span" animation="border" size="sm" className="me-2"/> : 'Feltöltés és Importálás 🚀'}
                    </Button>
                </Card.Body>
            </Card>

            <h5 className="text-light fw-bold mb-3">Meglévő Tananyagok (Kezelés)</h5>
            {isLoadingTopics ? (
                <div className="text-center py-4"><Spinner animation="border" variant="info" /></div>
            ) : topics.length === 0 ? (
                <p className="text-secondary">Nincs még elérhető tananyag az adatbázisban.</p>
            ) : (
                <Accordion className="border-secondary custom-dark-accordion">
                    {topics.map((topic, index) => (
                        <Accordion.Item eventKey={index.toString()} key={topic.topicId} className={`bg-dark border-secondary mb-3 rounded-4 overflow-hidden shadow-sm`}>
                            <Accordion.Header className={!topic.active ? 'opacity-75 bg-danger bg-opacity-10' : ''}>
                                <div className="d-flex w-100 justify-content-between align-items-center pe-3">
                                    <span className="text-light fw-bold fs-5">
                                        📘 {topic.topicName} {!topic.active && <Badge bg="danger" className="ms-3 fs-6">Felfüggesztve</Badge>}
                                    </span>
                                    <Button 
                                        variant={topic.active ? "outline-danger" : "outline-success"} 
                                        size="sm" 
                                        className="rounded-pill px-3 fw-bold d-flex align-items-center gap-2" 
                                        onClick={(e) => { e.stopPropagation(); handleStatusToggle(topic.topicId, 'Témakör', topic.topicName, topic.active); }}
                                    >
                                        <span>{topic.active ? '🛑' : '♻️'}</span> {topic.active ? 'Témakör Felfüggesztése' : 'Témakör Visszaállítása'}
                                    </Button>
                                </div>
                            </Accordion.Header>
                            <Accordion.Body className={`bg-dark text-light border-top border-secondary p-4 ${!topic.active ? 'bg-danger bg-opacity-10' : ''}`}>

                                {topic.lessons && topic.lessons.length > 0 ? (
                                    <div className="d-flex flex-column gap-4">
                                        {topic.lessons.map(lesson => (
                                            <Card key={lesson.lessonId} className={`bg-transparent border border-secondary shadow-sm rounded-4 ${!lesson.active ? 'bg-danger bg-opacity-10' : ''}`}>
                                                <Card.Header className="d-flex justify-content-between align-items-center bg-black bg-opacity-25 border-bottom border-secondary py-3 px-4">
                                                    <div>
                                                        <span className="fw-bold text-info fs-5 me-3">📄 {lesson.title}</span>
                                                        <Badge bg={getDifficultyBadge(lesson.difficulty)} pill>{lesson.difficulty}</Badge>
                                                        {!lesson.active && <Badge bg="danger" className="ms-3 fs-6">Felfüggesztve</Badge>}
                                                    </div>
                                                    <Button 
                                                        variant={lesson.active ? "outline-danger" : "outline-success"} 
                                                        size="sm" 
                                                        className="rounded-pill px-3 fw-bold d-flex align-items-center gap-2" 
                                                        onClick={() => handleStatusToggle(lesson.lessonId, 'Lecke', lesson.title, lesson.active)}
                                                    >
                                                        <span>{lesson.active ? '🛑' : '♻️'}</span> {lesson.active ? 'Felfüggesztés' : 'Visszaállítás'}
                                                    </Button>
                                                </Card.Header>
                                                <Card.Body className="p-0">
                                                    <ListGroup variant="flush">
                                                        {lesson.exercises?.map(exercise => (
                                                            <ListGroup.Item key={exercise.exerciseId} className={`bg-transparent border-bottom border-secondary border-opacity-50 text-light d-flex flex-wrap justify-content-between align-items-center py-3 px-4 ${!exercise.active ? 'bg-danger bg-opacity-10' : ''}`}>
                                                                <div className="d-flex align-items-center text-truncate pe-3 mb-2 mb-md-0">
                                                                    <Badge bg="info" text="dark" pill className="me-3 px-3 py-2 fw-bold" style={{ minWidth: '130px' }}>
                                                                        🧩 {exercise.type}
                                                                    </Badge>
                                                                    <span className="text-secondary text-truncate" title={getPreview(exercise.content)}>
                                                                        {getPreview(exercise.content)}
                                                                    </span>
                                                                    {!exercise.active && <Badge bg="danger" className="ms-3">Felfüggesztve</Badge>}
                                                                </div>
                                                                
                                                                <div className="d-flex align-items-center gap-3 flex-shrink-0">
                                                                    <Button variant="outline-info" size="sm" className="rounded-pill px-3 fw-bold d-flex align-items-center gap-2" onClick={() => handleShowPreview(exercise)}>
                                                                        <span>👁️</span> Megtekintés
                                                                    </Button>
                                                                    <Button 
                                                                        variant={exercise.active ? "outline-danger" : "outline-success"} 
                                                                        size="sm" 
                                                                        className="rounded-pill px-3 fw-bold d-flex align-items-center gap-2" 
                                                                        onClick={() => handleStatusToggle(exercise.exerciseId, 'Feladat', exercise.type, exercise.active)}
                                                                    >
                                                                        <span>{exercise.active ? '🛑' : '♻️'}</span> {exercise.active ? 'Felfüggesztés' : 'Visszaállítás'}
                                                                    </Button>
                                                                </div>
                                                            </ListGroup.Item>
                                                        ))}
                                                        {(!lesson.exercises || lesson.exercises.length === 0) && (
                                                            <ListGroup.Item className="bg-transparent border-0 text-secondary text-center py-4 fst-italic">
                                                                Nincsenek feladatok a leckében.
                                                            </ListGroup.Item>
                                                        )}
                                                    </ListGroup>
                                                </Card.Body>
                                            </Card>
                                        ))}
                                    </div>
                                ) : (
                                    <p className="text-secondary text-center mb-0 fst-italic">Nincsenek leckék ebben a témakörben.</p>
                                )}
                            </Accordion.Body>
                        </Accordion.Item>
                    ))}
                </Accordion>
            )}

            {/* FELADAT ELŐNÉZET MODAL */}
            <Modal show={previewModal.show} onHide={() => setPreviewModal({ show: false, exercise: null })} centered size="lg">
                <Modal.Header closeButton className="bg-dark border-secondary" variant="dark">
                    <Modal.Title className="text-info fw-bold">
                        🧩 {previewModal.exercise?.type} - Előnézet
                    </Modal.Title>
                </Modal.Header>
                <Modal.Body className="bg-dark text-light p-4">
                    {previewModal.exercise && (
                        <div>
                            <h4 className="text-info mb-4">
                                {previewModal.exercise.content?.question || 'Nincs megadva szöveges kérdés'}
                            </h4>
                            
                            {previewModal.exercise.content?.options && (
                                <div className="mb-4">
                                    <strong className="text-secondary text-uppercase small tracking-wide">Válaszlehetőségek:</strong>
                                    <div className="d-flex flex-wrap gap-2 mt-2">
                                        {previewModal.exercise.content.options.map((opt, i) => (
                                            <Badge key={i} bg="secondary" className="fs-6 py-2 px-3 fw-normal rounded-pill">
                                                {opt}
                                            </Badge>
                                        ))}
                                    </div>
                                </div>
                            )}

                            {previewModal.exercise.correctAnswer && (
                                <div className="mt-4 p-3 border border-success rounded-4 bg-success bg-opacity-10 shadow-sm">
                                    <strong className="text-success text-uppercase small d-block mb-1">Helyes válasz:</strong>
                                    <span className="fs-5 fw-bold text-light">
                                        {previewModal.exercise.correctAnswer?.answer || JSON.stringify(previewModal.exercise.correctAnswer)}
                                    </span>
                                </div>
                            )}
                        </div>
                    )}
                </Modal.Body>
            </Modal>

            <style>{`
                .custom-dark-accordion .accordion-button { background-color: var(--bs-dark); color: white; border-radius: 12px; }
                .custom-dark-accordion .accordion-button:not(.collapsed) { background-color: #2b3035; color: var(--bs-info); box-shadow: inset 0 -1px 0 rgba(255,255,255,0.1); }
                .custom-dark-accordion .accordion-button::after { filter: invert(1) grayscale(100%) brightness(200%); }
                .hover-scale:hover { transform: scale(1.02); }
            `}</style>
        </div>
    );
};

export default CurriculumManager;