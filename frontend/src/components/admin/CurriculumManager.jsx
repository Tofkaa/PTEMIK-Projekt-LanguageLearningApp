import React, { useState, useEffect } from 'react';
import { Card, Form, Button, Alert, Spinner, Accordion, Badge, ListGroup, Modal } from 'react-bootstrap';
import { adminApi } from '../../services/adminApi';

const CurriculumManager = () => {
    const [selectedFile, setSelectedFile] = useState(null);
    const [isLoading, setIsLoading] = useState(false);
    const [message, setMessage] = useState({ text: '', type: '' });
    const [topics, setTopics] = useState([]);
    const [isLoadingTopics, setIsLoadingTopics] = useState(true);
    
    // Új state az előnézeti ablakhoz
    const [previewModal, setPreviewModal] = useState({ show: false, exercise: null });

    useEffect(() => {
        fetchTopics();
    }, []);

    const fetchTopics = async () => {
        setIsLoadingTopics(true);
        try {
            const response = await adminApi.getAllTopics();
            setTopics(Array.isArray(response.data) ? response.data : []);
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

    const handleDelete = async (id, type, name) => {
        if (window.confirm(`Biztosan törlöd ezt a(z) ${type} elemet? (${name})\nEz a művelet (logikailag) eltávolítja az alatta lévő elemeket is!`)) {
            try {
                if (type === 'Témakör') await adminApi.deleteTopic(id);
                if (type === 'Lecke') await adminApi.deleteLesson(id);
                if (type === 'Feladat') await adminApi.deleteExercise(id);
                
                setMessage({ text: `${type} sikeresen törölve!`, type: 'success' });
                fetchTopics();
            } catch (error) {
                setMessage({ text: `Hiba a(z) ${type} törlésekor.`, type: 'danger', error});
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
            case 'MEDIUM': return 'warning';
            case 'HARD': return 'danger';
            default: return 'secondary';
        }
    };

    // Modal nyitó függvény
    const handleShowPreview = (exercise) => {
        setPreviewModal({ show: true, exercise });
    };

    return (
        <div>
            {message.text && <Alert variant={message.type} className="shadow-sm">{message.text}</Alert>}

            <Card className="bg-dark border-secondary shadow-lg mb-4">
                <Card.Body className="p-4">
                    <h5 className="text-warning fw-bold mb-3">Tananyag JSON Importálása</h5>
                    <Form.Group className="mb-4">
                        <Form.Control id="json-upload-input" type="file" accept=".json" onChange={handleFileChange} className="bg-dark text-light border-secondary" />
                    </Form.Group>
                    <Button variant="warning" className="fw-bold px-4 text-dark" onClick={handleUpload} disabled={!selectedFile || isLoading}>
                        {isLoading ? <Spinner as="span" animation="border" size="sm" className="me-2"/> : 'Feltöltés és Importálás 🚀'}
                    </Button>
                </Card.Body>
            </Card>

            <h5 className="text-light fw-bold mb-3">Meglévő Tananyagok (Kezelés)</h5>
            {isLoadingTopics ? (
                <div className="text-center py-4"><Spinner animation="border" variant="warning" /></div>
            ) : topics.length === 0 ? (
                <p className="text-secondary">Nincs még elérhető tananyag az adatbázisban.</p>
            ) : (
                <Accordion className="border-secondary custom-dark-accordion">
                    {topics.map((topic, index) => (
                        <Accordion.Item eventKey={index.toString()} key={topic.topicId} className="bg-dark border-secondary mb-3 rounded overflow-hidden shadow-sm">
                            <Accordion.Header>
                                <span className="text-light fw-bold fs-5">📘 {topic.topicName}</span>
                            </Accordion.Header>
                            <Accordion.Body className="bg-dark text-light border-top border-secondary p-4">
                                
                                <div className="d-flex justify-content-end mb-4 border-bottom border-secondary pb-3">
                                    <Button variant="outline-danger" size="sm" onClick={() => handleDelete(topic.topicId, 'Témakör', topic.topicName)}>
                                        🗑️ Teljes Témakör Törlése
                                    </Button>
                                </div>

                                {topic.lessons && topic.lessons.length > 0 ? (
                                    <div className="d-flex flex-column gap-3">
                                        {topic.lessons.map(lesson => (
                                            <Card key={lesson.lessonId} className="bg-transparent border border-secondary shadow-sm">
                                                <Card.Header className="d-flex justify-content-between align-items-center bg-dark border-secondary py-3">
                                                    <div>
                                                        <span className="fw-bold text-warning fs-5 me-3">📄 {lesson.title}</span>
                                                        <Badge bg={getDifficultyBadge(lesson.difficulty)}>{lesson.difficulty}</Badge>
                                                    </div>
                                                    <Button variant="outline-danger" size="sm" onClick={() => handleDelete(lesson.lessonId, 'Lecke', lesson.title)}>
                                                        Lecke Törlése 🗑️
                                                    </Button>
                                                </Card.Header>
                                                <Card.Body className="p-0">
                                                    <ListGroup variant="flush">
                                                        {lesson.exercises?.map(exercise => (
                                                            <ListGroup.Item key={exercise.exerciseId} className="bg-dark border-secondary text-light d-flex justify-content-between align-items-center py-3 px-4">
                                                                <div className="d-flex align-items-center text-truncate pe-3">
                                                                    <Badge bg="info" text="dark" className="me-3" style={{ minWidth: '130px' }}>
                                                                        🧩 {exercise.type}
                                                                    </Badge>
                                                                    <span className="text-secondary text-truncate" title={getPreview(exercise.content)}>
                                                                        {getPreview(exercise.content)}
                                                                    </span>
                                                                </div>
                                                                
                                                                {/* Gombok: Előnézet és Törlés egymás mellett */}
                                                                <div className="d-flex gap-3 flex-shrink-0">
                                                                    <Button variant="link" className="text-info p-0 text-decoration-none" onClick={() => handleShowPreview(exercise)}>
                                                                        👁️ Megtekintés
                                                                    </Button>
                                                                    <Button variant="link" className="text-danger p-0 text-decoration-none" onClick={() => handleDelete(exercise.exerciseId, 'Feladat', exercise.type)}>
                                                                        🗑️ Törlés
                                                                    </Button>
                                                                </div>
                                                            </ListGroup.Item>
                                                        ))}
                                                        {(!lesson.exercises || lesson.exercises.length === 0) && (
                                                            <ListGroup.Item className="bg-dark border-secondary text-secondary text-center py-3">
                                                                Nincsenek feladatok a leckében.
                                                            </ListGroup.Item>
                                                        )}
                                                    </ListGroup>
                                                </Card.Body>
                                            </Card>
                                        ))}
                                    </div>
                                ) : (
                                    <p className="text-secondary text-center mb-0">Nincsenek leckék ebben a témakörben.</p>
                                )}
                            </Accordion.Body>
                        </Accordion.Item>
                    ))}
                </Accordion>
            )}

            {/* FELADAT ELŐNÉZET MODAL */}
            <Modal 
                show={previewModal.show} 
                onHide={() => setPreviewModal({ show: false, exercise: null })} 
                centered 
                size="lg"
            >
                <Modal.Header closeButton className="bg-dark border-secondary" variant="dark">
                    <Modal.Title className="text-info fw-bold">
                        🧩 {previewModal.exercise?.type} - Előnézet
                    </Modal.Title>
                </Modal.Header>
                <Modal.Body className="bg-dark text-light">
                    {previewModal.exercise && (
                        <div>
                            <h4 className="text-warning mb-4">
                                {previewModal.exercise.content?.question || 'Nincs megadva szöveges kérdés'}
                            </h4>
                            
                            {/* Válaszlehetőségek (ha vannak) */}
                            {previewModal.exercise.content?.options && (
                                <div className="mb-4">
                                    <strong className="text-secondary text-uppercase small tracking-wide">Válaszlehetőségek:</strong>
                                    <div className="d-flex flex-wrap gap-2 mt-2">
                                        {previewModal.exercise.content.options.map((opt, i) => (
                                            <Badge key={i} bg="secondary" className="fs-6 py-2 px-3 fw-normal">
                                                {opt}
                                            </Badge>
                                        ))}
                                    </div>
                                </div>
                            )}

                            {/* Kép URL előnézet (ha van) */}
                            {previewModal.exercise.content?.imageUrl && (
                                <div className="mb-4">
                                    <strong className="text-secondary text-uppercase small">Kép előnézet:</strong>
                                    <div className="mt-2">
                                        <img src={previewModal.exercise.content.imageUrl} alt="Feladat kép" style={{ maxHeight: '150px', borderRadius: '8px' }} />
                                    </div>
                                </div>
                            )}

                            {/* Hint / Segítség */}
                            {previewModal.exercise.content?.hint && (
                                <div className="mb-4 text-info">
                                    <strong className="text-secondary text-uppercase small">Segítség (Hint):</strong><br />
                                    💡 {previewModal.exercise.content.hint}
                                </div>
                            )}

                            {/* HELYES VÁLASZ (Kiemelve) */}
                            <div className="mt-4 p-3 border border-success rounded bg-success bg-opacity-10 shadow-sm">
                                <strong className="text-success text-uppercase small d-block mb-1">Helyes válasz:</strong>
                                <span className="fs-5 fw-bold text-light">
                                    {previewModal.exercise.correctAnswer?.answer || JSON.stringify(previewModal.exercise.correctAnswer)}
                                </span>
                            </div>

                            {/* Nyers JSON Fejlesztőknek */}
                            <div className="mt-5 pt-3 border-top border-secondary">
                                <strong className="text-secondary small mb-2 d-block">Nyers adatstruktúra (Admin):</strong>
                                <pre className="bg-black text-secondary p-3 rounded small custom-scrollbar" style={{ maxHeight: '200px', overflowY: 'auto' }}>
                                    {JSON.stringify(previewModal.exercise.content, null, 2)}
                                </pre>
                            </div>
                        </div>
                    )}
                </Modal.Body>
                <Modal.Footer className="bg-dark border-secondary">
                    <Button variant="outline-light" onClick={() => setPreviewModal({ show: false, exercise: null })}>
                        Bezárás
                    </Button>
                </Modal.Footer>
            </Modal>

            <style>{`
                .custom-dark-accordion .accordion-button {
                    background-color: var(--bs-dark);
                    color: white;
                }
                .custom-dark-accordion .accordion-button:not(.collapsed) {
                    background-color: #2b3035;
                    color: var(--bs-warning);
                    box-shadow: inset 0 -1px 0 rgba(255,255,255,0.1);
                }
                .custom-dark-accordion .accordion-button::after {
                    filter: invert(1) grayscale(100%) brightness(200%);
                }
                .tracking-wide { letter-spacing: 1px; }
            `}</style>
        </div>
    );
};

export default CurriculumManager;