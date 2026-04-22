import React, { useState, useEffect } from 'react';
import { Card, Form, Button, Alert, Spinner, Accordion, Badge, ListGroup } from 'react-bootstrap';
import { adminApi } from '../../services/adminApi';

const CurriculumManager = () => {
    const [selectedFile, setSelectedFile] = useState(null);
    const [isLoading, setIsLoading] = useState(false);
    const [message, setMessage] = useState({ text: '', type: '' });
    const [topics, setTopics] = useState([]);
    const [isLoadingTopics, setIsLoadingTopics] = useState(true);

    useEffect(() => {
        fetchTopics();
    }, []);

    const fetchTopics = async () => {
        setIsLoadingTopics(true);
        try {
            const response = await adminApi.getAllTopics();
            setTopics(response.data);
        } catch (error) {
            console.error("Hiba a tananyagok betöltésekor:", error);
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
                setMessage({ text: 'Hiba az importálás során.', type: 'danger' });
            } finally { setIsLoading(false); }
        };
        reader.readAsText(selectedFile);
    };

    // Univerzális törlő függvény
    const handleDelete = async (id, type, name) => {
        if (window.confirm(`Biztosan törlöd ezt a(z) ${type} elemet? (${name})\nEz a művelet (logikailag) eltávolítja az alatta lévő elemeket is!`)) {
            try {
                if (type === 'Témakör') await adminApi.deleteTopic(id);
                if (type === 'Lecke') await adminApi.deleteLesson(id);
                if (type === 'Feladat') await adminApi.deleteExercise(id);
                
                setMessage({ text: `${type} sikeresen törölve!`, type: 'success' });
                fetchTopics(); // Újratöltjük a fát
            } catch (error) {
                setMessage({ text: `Hiba a(z) ${type} törlésekor.`, type: 'danger' });
            }
        }
    };

    return (
        <div>
            {message.text && <Alert variant={message.type} className="shadow-sm">{message.text}</Alert>}

            {/* FELTÖLTŐ KÁRTYA */}
            <Card className="bg-dark border-secondary shadow-lg mb-4">
                <Card.Body className="p-4">
                    <h5 className="text-warning fw-bold mb-3">Tananyag JSON Importálása</h5>
                    <Form.Group className="mb-4">
                        <Form.Control id="json-upload-input" type="file" accept=".json" onChange={handleFileChange} className="bg-dark text-light border-secondary" />
                    </Form.Group>
                    <Button variant="warning" className="fw-bold px-4" onClick={handleUpload} disabled={!selectedFile || isLoading}>
                        {isLoading ? <Spinner as="span" animation="border" size="sm" className="me-2"/> : 'Feltöltés és Importálás 🚀'}
                    </Button>
                </Card.Body>
            </Card>

            {/* TANANYAG LISTA ÉS TÖRLÉS */}
            <h5 className="text-light fw-bold mb-3">Meglévő Tananyagok (Kezelés)</h5>
            {isLoadingTopics ? (
                <div className="text-center py-4"><Spinner animation="border" variant="warning" /></div>
            ) : topics.length === 0 ? (
                <p className="text-secondary">Nincs még elérhető tananyag az adatbázisban.</p>
            ) : (
                <Accordion className="border-secondary custom-accordion">
                    {topics.map((topic, index) => (
                        <Accordion.Item eventKey={index.toString()} key={topic.topicId} className="bg-dark border-secondary mb-2">
                            <Accordion.Header>
                                <div className="d-flex justify-content-between w-100 align-items-center pe-3">
                                    <span className="text-light fw-bold">📘 {topic.topicName}</span>
                                    <Button variant="outline-danger" size="sm" onClick={(e) => { e.stopPropagation(); handleDelete(topic.topicId, 'Témakör', topic.topicName); }}>
                                        Törlés 🗑️
                                    </Button>
                                </div>
                            </Accordion.Header>
                            <Accordion.Body className="bg-dark text-light border-top border-secondary pt-0">
                                <ListGroup variant="flush">
                                    {topic.lessons?.map(lesson => (
                                        <ListGroup.Item key={lesson.lessonId} className="bg-dark border-secondary py-3">
                                            <div className="d-flex justify-content-between align-items-center mb-2">
                                                <span className="fw-bold text-warning">📄 {lesson.title} <Badge bg="secondary" className="ms-2">{lesson.difficulty}</Badge></span>
                                                <Button variant="outline-danger" size="sm" onClick={() => handleDelete(lesson.lessonId, 'Lecke', lesson.title)}>Törlés 🗑️</Button>
                                            </div>
                                            
                                            {/* Feladatok listája a leckén belül */}
                                            <div className="ps-4 mt-2 border-start border-secondary">
                                                {lesson.exercises?.map(exercise => (
                                                    <div key={exercise.exerciseId} className="d-flex justify-content-between align-items-center py-1 text-secondary small">
                                                        <span>🧩 {exercise.type} - {exercise.content?.question?.substring(0, 40)}...</span>
                                                        <Button variant="link" className="text-danger p-0 ms-3 text-decoration-none" onClick={() => handleDelete(exercise.exerciseId, 'Feladat', exercise.type)}>Törlés</Button>
                                                    </div>
                                                ))}
                                            </div>
                                        </ListGroup.Item>
                                    ))}
                                </ListGroup>
                            </Accordion.Body>
                        </Accordion.Item>
                    ))}
                </Accordion>
            )}
        </div>
    );
};

export default CurriculumManager;