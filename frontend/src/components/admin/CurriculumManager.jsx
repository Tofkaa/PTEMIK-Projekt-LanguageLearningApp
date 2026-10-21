import React, { useState, useEffect } from 'react';
import { Card, Form, Button, Spinner, Accordion, Badge, ListGroup, Modal, Row, Col } from 'react-bootstrap';
import { adminApi } from '../../services/adminApi';
import LanguageText from '../LanguageText';
import ExerciseImage from '../ExerciseImage';
import LearningFeedback from '../LearningFeedback';

const AVAILABLE_COURSES = {
    'en': { name: 'Angol', flag: '🇬🇧' },
    'es': { name: 'Spanyol', flag: '🇪🇸' },
    'de': { name: 'Német', flag: '🇩🇪' }
};

const EXERCISE_TYPE_LABELS = {
    MULTIPLE_CHOICE: 'Feleletválasztás',
    WORD_BANK: 'Szóbank',
    TRANSLATION: 'Fordítás',
    IMAGE_CHOICE: 'Képes választás',
};
const exerciseTypeLabel = type => EXERCISE_TYPE_LABELS[type] || 'Ismeretlen feladattípus';

const CurriculumManager = () => {
    const [selectedFile, setSelectedFile] = useState(null);
    const [isLoading, setIsLoading] = useState(false);
    const [statusBusy, setStatusBusy] = useState(false);
    const [message, setMessage] = useState({ text: '', type: '' });
    
    const [topics, setTopics] = useState([]);
    const [isLoadingTopics, setIsLoadingTopics] = useState(true);
    const [previewModal, setPreviewModal] = useState({ show: false, exercise: null });

    const [importLevel, setImportLevel] = useState('FULL_TOPIC');
    const [targetTopicId, setTargetTopicId] = useState('');
    const [targetLessonId, setTargetLessonId] = useState('');
    
    const [importCourseCode, setImportCourseCode] = useState('en');

    useEffect(() => {
        fetchTopics();
    }, []);

    const fetchTopics = async () => {
        setIsLoadingTopics(true);
        try {
            const response = await adminApi.getAllTopics();
            let data = Array.isArray(response.data) ? response.data : [];
            
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
            setMessage({ text: 'Nem sikerült betölteni a tananyagokat. Próbáld újra az oldal frissítésével.', type: 'danger' });
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
        if (!selectedFile || isLoading) return;

        if (importLevel === 'LESSON' && !targetTopicId) {
            setMessage({ text: 'Kérlek válaszd ki a cél Témakört!', type: 'warning' });
            return;
        }
        if (importLevel === 'EXERCISE' && !targetLessonId) {
            setMessage({ text: 'Kérlek válaszd ki a cél Leckét!', type: 'warning' });
            return;
        }

        setIsLoading(true); setMessage({ text: '', type: '' });
        const reader = new FileReader();
        reader.onload = async (e) => {
            try {
                let jsonData = JSON.parse(e.target.result);
                
                if (importLevel === 'FULL_TOPIC') {
                    if (Array.isArray(jsonData)) {
                        jsonData = jsonData.map(topic => ({ ...topic, courseCode: importCourseCode }));
                    } else {
                        jsonData.courseCode = importCourseCode;
                    }
                    await adminApi.importCurriculum(jsonData);
                } else if (importLevel === 'LESSON') {
                    await adminApi.importLessons(targetTopicId, jsonData);
                } else if (importLevel === 'EXERCISE') {
                    await adminApi.importExercises(targetLessonId, jsonData);
                }

                setMessage({ text: 'Tananyag sikeresen importálva!', type: 'success' });
                window.dispatchEvent(new Event('adminActionOccurred'));
                setSelectedFile(null);
                document.getElementById('json-upload-input').value = '';
               
                setImportLevel('FULL_TOPIC');
                setTargetTopicId('');
                setTargetLessonId('');
                
                fetchTopics();
            } catch (error) {
                setMessage({ text: error.response?.data?.message || 'Hiba az importálás során. Ellenőrizd a JSON formátumát!', type: 'danger' });
            } finally { setIsLoading(false); }
        };
        reader.onerror = () => {
            setMessage({ text: 'Nem sikerült elolvasni a fájlt. Válaszd ki újra, és próbáld meg ismét!', type: 'danger' });
            setIsLoading(false);
        };
        reader.readAsText(selectedFile);
    };

    const handleStatusToggle = async (id, type, name, currentStatus) => {
        if (statusBusy) return;
        const nextStatus = !currentStatus;
        if (window.confirm(`Biztosan ${nextStatus ? 'visszaállítod' : 'felfüggeszted'} ezt a(z) ${type} elemet? (${name})`)) {
            setStatusBusy(true);
            try {
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
                            exercises: l.exercises ? l.exercises.map(ex => ex.exerciseId === id ? { ...ex, active: nextStatus } : ex) : []
                        })) : []
                    })));
                }
                
                setMessage({ text: `${type} státusza frissítve!`, type: 'success' });
                window.dispatchEvent(new Event('adminActionOccurred'));
            } catch (error) {
                setMessage({ text: `Hiba a(z) ${type} módosításakor.`, type: 'danger', error});
            } finally {
                setStatusBusy(false);
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

    const availableLessonsForTarget = topics.find(t => t.topicId === targetTopicId)?.lessons || [];

    // --- Témakörök csoportosítása nyelvek (courseCode) szerint ---
    const groupedTopics = topics.reduce((acc, topic) => {
        const code = topic.courseCode || 'en'; // Fallback
        if (!acc[code]) {
            acc[code] = [];
        }
        acc[code].push(topic);
        return acc;
    }, {});

    return (
        <div>
            <LearningFeedback error={message.type === 'danger' ? message.text : ''}
                feedback={message.type !== 'danger' && message.text ? { type: message.type, msg: message.text } : null} />

            <Card className="bg-dark border-secondary shadow-lg mb-4 rounded-4">
                <Card.Body className="p-4">
                    <h3 className="h5 text-info fw-bold mb-4">Tananyag JSON Importálása</h3>
                    
                    <Row className="mb-4">
                        <Col md={3}>
                            <Form.Group controlId="curriculum-import-level">
                                <Form.Label className="text-secondary small fw-bold">Mit szeretnél importálni?</Form.Label>
                                <Form.Select 
                                    className="bg-dark text-light border-secondary shadow-sm"
                                    value={importLevel}
                                    disabled={isLoading}
                                    onChange={(e) => {
                                        const nextLevel = e.currentTarget.value;
                                        if (!['FULL_TOPIC', 'LESSON', 'EXERCISE'].includes(nextLevel) || nextLevel === importLevel) return;
                                        setImportLevel(nextLevel);
                                        setTargetTopicId('');
                                        setTargetLessonId('');
                                    }}
                                >
                                    <option value="FULL_TOPIC">Új Témakör (Teljes struktúra)</option>
                                    <option value="LESSON">Új Lecke (Meglévő témakörbe)</option>
                                    <option value="EXERCISE">Új Feladatok (Meglévő leckéhez)</option>
                                </Form.Select>
                            </Form.Group>
                        </Col>

                        {/* Csak Témakör esetén bekérjük a Nyelvet */}
                        {importLevel === 'FULL_TOPIC' && (
                            <Col md={3}>
                                <Form.Group controlId="curriculum-import-course">
                                    <Form.Label className="text-secondary small fw-bold">Nyelv (Kurzus)</Form.Label>
                                    <Form.Select 
                                        className="bg-dark text-light border-secondary shadow-sm"
                                        value={importCourseCode}
                                        disabled={isLoading}
                                        onChange={(e) => setImportCourseCode(e.target.value)}
                                    >
                                        {Object.entries(AVAILABLE_COURSES).map(([code, data]) => (
                                            <option key={code} value={code}>
                                                {data.flag} {data.name}
                                            </option>
                                        ))}
                                    </Form.Select>
                                </Form.Group>
                            </Col>
                        )}

                        {/* Csak akkor mutatjuk, ha Leckét VAGY Feladatot importál */}
                        {(importLevel === 'LESSON' || importLevel === 'EXERCISE') && (
                            <Col md={4}>
                                <Form.Group controlId="curriculum-import-topic">
                                    <Form.Label className="text-secondary small fw-bold">Cél Témakör</Form.Label>
                                    <Form.Select 
                                        className="bg-dark text-light border-secondary shadow-sm"
                                        value={targetTopicId}
                                        disabled={isLoading}
                                        onChange={(e) => {
                                            setTargetTopicId(e.target.value);
                                            setTargetLessonId(''); // Reset lecke ha témakört vált
                                        }}
                                    >
                                        <option value="">-- Válassz Témakört --</option>
                                        {topics.map(t => (
                                            <option key={t.topicId} value={t.topicId}>
                                                [{AVAILABLE_COURSES[t.courseCode || 'en']?.flag}] {t.topicName}
                                            </option>
                                        ))}
                                    </Form.Select>
                                </Form.Group>
                            </Col>
                        )}

                        {/* Csak akkor mutatjuk, ha Feladatot importál */}
                        {importLevel === 'EXERCISE' && (
                            <Col md={4}>
                                <Form.Group controlId="curriculum-import-lesson">
                                    <Form.Label className="text-secondary small fw-bold">Cél Lecke</Form.Label>
                                    <Form.Select 
                                        className="bg-dark text-light border-secondary shadow-sm"
                                        value={targetLessonId}
                                        onChange={(e) => setTargetLessonId(e.target.value)}
                                        disabled={!targetTopicId || isLoading}
                                    >
                                        <option value="">-- Válassz Leckét --</option>
                                        {availableLessonsForTarget.map(l => (
                                           <option key={l.lessonId} value={l.lessonId}>
                                                {l.title} ({l.difficulty})
                                            </option>
                                        ))}
                                    </Form.Select>
                                </Form.Group>
                            </Col>
                        )}
                    </Row>

                    <Form.Group className="mb-4" controlId="json-upload-input">
                        <Form.Label>Tananyag JSON-fájlja</Form.Label>
                        <Form.Control type="file" accept=".json" disabled={isLoading} aria-describedby="curriculum-import-help" onChange={handleFileChange} className="bg-dark text-light border-secondary rounded-3" />
                        <Form.Text id="curriculum-import-help" className="text-secondary">
                            {importLevel === 'FULL_TOPIC' && "Tölts fel egy JSON array-t, ami TopicImportRequest objektumokat tartalmaz."}
                            {importLevel === 'LESSON' && "Tölts fel egy JSON array-t, ami LessonImportRequest objektumokat tartalmaz."}
                            {importLevel === 'EXERCISE' && "Tölts fel egy JSON array-t, ami ExerciseImportRequest objektumokat tartalmaz."}
                        </Form.Text>
                    </Form.Group>

                    <Button variant="info" className="fw-bold px-4 text-dark rounded-pill" onClick={handleUpload} aria-disabled={!selectedFile || isLoading} aria-busy={isLoading}>
                        {isLoading && <Spinner as="span" animation="border" size="sm" aria-hidden="true" className="me-2"/>}Kiválasztott JSON Importálása
                    </Button>
                </Card.Body>
            </Card>

            <h3 className="h4 text-light fw-bold mb-4">Meglévő Tananyagok (Kezelés)</h3>
            
            {isLoadingTopics ? (
                <div role="status" className="text-center py-4"><Spinner animation="border" variant="info" aria-hidden="true" /> Tananyagok betöltése…</div>
            ) : topics.length === 0 ? (
                <p className="text-secondary">Nincs még elérhető tananyag az adatbázisban.</p>
            ) : (
                Object.entries(groupedTopics).map(([courseCode, courseTopics]) => {
                    const courseData = AVAILABLE_COURSES[courseCode] || { name: 'Ismeretlen', flag: '🏳️' };
                    
                    return (
                        <div key={courseCode} className="mb-5">
                            {/* Csoport Fejléc */}
                            <h4 className="h5 text-info fw-bold mb-3 border-bottom border-secondary pb-2 d-flex align-items-center">
                                <span aria-hidden="true" className="fs-3 me-2">{courseData.flag}</span>
                                {courseData.name} Kurzus ({courseTopics.length} témakör)
                            </h4>
                            
                            <Accordion className="border-secondary custom-dark-accordion">
                                {courseTopics.map((topic) => (
                                    <Accordion.Item eventKey={topic.topicId} key={topic.topicId} className={`bg-dark border-secondary mb-3 rounded-4 shadow-sm`}>
                                        <div className="d-flex flex-wrap align-items-center gap-2 pe-3">
                                        <Accordion.Header as="h5" className={`flex-grow-1 ${!topic.active ? 'bg-danger bg-opacity-10' : ''}`}>
                                                <span className="text-light fw-bold fs-5">
                                                    <span aria-hidden="true">📘 </span>{topic.topicName} {!topic.active && <Badge bg="danger" className="ms-3 fs-6">Felfüggesztve</Badge>}
                                                </span>
                                        </Accordion.Header>
                                                <Button
                                                    aria-label={`${topic.active ? 'Témakör Felfüggesztése' : 'Témakör Visszaállítása'}: ${topic.topicName}`}
                                                    aria-disabled={statusBusy}
                                                    variant={topic.active ? "outline-danger" : "outline-success"} 
                                                    size="sm" 
                                                    className="rounded-pill px-3 fw-bold d-flex align-items-center gap-2" 
                                                    onClick={() => handleStatusToggle(topic.topicId, 'Témakör', topic.topicName, topic.active)}
                                                >
                                                    <span aria-hidden="true">{topic.active ? '🛑' : '♻️'}</span> {topic.active ? 'Témakör Felfüggesztése' : 'Témakör Visszaállítása'}
                                                </Button>
                                            </div>
                                        <Accordion.Body className={`bg-dark text-light border-top border-secondary p-4 ${!topic.active ? 'bg-danger bg-opacity-10' : ''}`}>

                                            {topic.lessons && topic.lessons.length > 0 ? (
                                                <div className="d-flex flex-column gap-4">
                                                    {topic.lessons.map(lesson => (
                                                        <Card key={lesson.lessonId} className={`bg-transparent border border-secondary shadow-sm rounded-4 ${!lesson.active ? 'bg-danger bg-opacity-10' : ''}`}>
                                                            <Card.Header className="d-flex flex-wrap gap-2 justify-content-between align-items-center bg-black bg-opacity-25 border-bottom border-secondary py-3 px-4">
                                                                <div>
                                                                    <h6 className="fw-bold text-info fs-5 me-3 mb-0"><span aria-hidden="true">📄 </span>{lesson.title} (Sorszám: {lesson.orderIndex})</h6>
                                                                    {!lesson.active && <Badge bg="danger" className="ms-3 fs-6">Felfüggesztve</Badge>}
                                                                </div>
                                                                <Button
                                                                    aria-label={`${lesson.active ? 'Felfüggesztés' : 'Visszaállítás'}: ${lesson.title}`}
                                                                    aria-disabled={statusBusy}
                                                                    variant={lesson.active ? "outline-danger" : "outline-success"} 
                                                                    size="sm" 
                                                                    className="rounded-pill px-3 fw-bold d-flex align-items-center gap-2" 
                                                                    onClick={() => handleStatusToggle(lesson.lessonId, 'Lecke', lesson.title, lesson.active)}
                                                                >
                                                                    <span aria-hidden="true">{lesson.active ? '🛑' : '♻️'}</span> {lesson.active ? 'Felfüggesztés' : 'Visszaállítás'}
                                                                </Button>
                                                            </Card.Header>
                                                            <Card.Body className="p-0">
                                                                <ListGroup variant="flush">
                                                                    {lesson.exercises?.map((exercise, exerciseIndex) => (
                                                                        <ListGroup.Item key={exercise.exerciseId} className={`bg-transparent border-bottom border-secondary border-opacity-50 text-light d-flex flex-wrap justify-content-between align-items-center py-3 px-4 ${!exercise.active ? 'bg-danger bg-opacity-10' : ''}`}>
                                                                            <div className="d-flex flex-wrap align-items-center gap-2 pe-3 mb-2 mb-md-0">
                                                                                <span className="text-secondary">{exerciseIndex + 1}. feladat</span>
                                                                               <Badge bg="info" text="dark" pill className="me-3 px-3 py-2 fw-bold" style={{ minWidth: '130px' }}>
                                                                                    <span aria-hidden="true">🧩 </span>{exerciseTypeLabel(exercise.type)}
                                                                                </Badge>
                                                                                <Badge bg={getDifficultyBadge(exercise.difficulty)} pill className="me-3">{exercise.difficulty}</Badge>
                                                                                <span className="text-secondary">
                                                                                    <LanguageText text={getPreview(exercise.content)}
                                                                                        lang={exercise.content?.questionLang || 'hu'} parts={exercise.content?.questionParts} />
                                                                                </span>
                                                                                {!exercise.active && <Badge bg="danger" className="ms-3">Felfüggesztve</Badge>}
                                                                            </div>
                                                                            
                                                                            <div className="d-flex flex-wrap align-items-center gap-2">
                                                                                <Button aria-label={`Megtekintés: ${lesson.title}, ${exerciseIndex + 1}. feladat`} variant="outline-info" size="sm" className="rounded-pill px-3 fw-bold d-flex align-items-center gap-2" onClick={() => handleShowPreview(exercise)}>
                                                                                    <span aria-hidden="true">👁️</span> Megtekintés
                                                                                </Button>
                                                                                <Button
                                                                                    aria-label={`${exercise.active ? 'Felfüggesztés' : 'Visszaállítás'}: ${lesson.title}, ${exerciseIndex + 1}. feladat`}
                                                                                    aria-disabled={statusBusy}
                                                                                    variant={exercise.active ? "outline-danger" : "outline-success"} 
                                                                                    size="sm" 
                                                                                    className="rounded-pill px-3 fw-bold d-flex align-items-center gap-2" 
                                                                                    onClick={() => handleStatusToggle(exercise.exerciseId, 'Feladat', exercise.type, exercise.active)}
                                                                                >
                                                                                    <span aria-hidden="true">{exercise.active ? '🛑' : '♻️'}</span> {exercise.active ? 'Felfüggesztés' : 'Visszaállítás'}
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
                        </div>
                    );
                })
            )}

            {/* FELADAT ELŐNÉZET MODAL */}
            <Modal show={previewModal.show} onHide={() => setPreviewModal({ show: false, exercise: null })} aria-labelledby="curriculum-preview-title" centered size="lg">
                <Modal.Header closeButton closeLabel="Előnézet bezárása" className="bg-dark border-secondary">
                    <Modal.Title as="h2" lang="hu" id="curriculum-preview-title" className="h4 app-accent-text fw-bold">
                        <span aria-hidden="true">🧩 </span>{exerciseTypeLabel(previewModal.exercise?.type)} – Előnézet
                    </Modal.Title>
                </Modal.Header>
                <Modal.Body className="bg-dark text-light p-4">
                    {previewModal.exercise && (
                        <div>
                            <h3 className="h4 text-info mb-4">
                                <LanguageText text={previewModal.exercise.content?.question || 'Nincs megadva szöveges kérdés'}
                                    lang={previewModal.exercise.content?.questionLang || 'hu'} parts={previewModal.exercise.content?.questionParts} />
                            </h3>
                            
                            <ExerciseImage exercise={previewModal.exercise} maxHeight={180} />
                            {previewModal.exercise.content?.options && (
                                <div className="mb-4">
                                    <strong className="text-secondary text-uppercase small tracking-wide">Válaszlehetőségek:</strong>
                                    <div className="d-flex flex-wrap gap-2 mt-2" role="group" tabIndex={0}
                                        lang={previewModal.exercise.content?.answerLang || 'hu'}
                                        aria-label={previewModal.exercise.content.options.join(', ')}>
                                        {previewModal.exercise.content.options.map((opt, i) => (
                                            <Badge key={i} lang={previewModal.exercise.content?.answerLang} bg="secondary" className="fs-6 py-2 px-3 fw-normal rounded-pill">
                                                {opt}
                                            </Badge>
                                        ))}
                                    </div>
                                </div>
                            )}

                            {previewModal.exercise.correctAnswer && (
                                <div className="mt-4 p-3 border border-success rounded-4 bg-success bg-opacity-10 shadow-sm">
                                    <strong className="text-success text-uppercase small d-block mb-1">Helyes válasz:</strong>
                                    <span lang={previewModal.exercise.content?.answerLang} className="fs-5 fw-bold text-light">
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
