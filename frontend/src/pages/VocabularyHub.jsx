import { useState, useEffect } from 'react';
import { Container, Card, Table, Button, Form, Spinner, Badge, Row, Col } from 'react-bootstrap';
import { useNavigate } from 'react-router-dom';
import api from '../services/api.jsx';
import { useAuth } from '../context/AuthContext.jsx';
import { useNotifications } from '../context/NotificationContext.jsx';
import { setScopedCounter } from '../utils/viewedStore.js';
import { formatToLocalTimestamp } from '../utils/dateUtils';

const VocabularyHub = () => {
    const navigate = useNavigate();
    const [vocabulary, setVocabulary] = useState([]);
    const [isLoading, setIsLoading] = useState(true);
    const [searchTerm, setSearchTerm] = useState('');
    const [filterLevel, setFilterLevel] = useState('ALL');
    const { user } = useAuth();
    const { notifications } = useNotifications();

    useEffect(() => {
    if (user?.userId && notifications?.dueVocabularyCount != null) {
        setScopedCounter('seenDueVocabCount', user.userId, notifications.dueVocabularyCount);
    }
    }, [user?.userId, notifications?.dueVocabularyCount]);

    useEffect(() => {
        fetchVocabularyHub();
    }, []);

    const fetchVocabularyHub = async () => {
        try {
            const response = await api.get('/vocabulary/hub');
            setVocabulary(response.data);
        } catch (err) {
            console.error("Hiba a szótár hub betöltésekor:", err);
        } finally {
            setIsLoading(false);
        }
    };

    const getSrsBadgeVariant = (level) => {
        if (level === 0) return 'danger';
        if (level <= 2) return 'warning';
        if (level <= 5) return 'info';
        return 'success'; // 6 = mesteri
    };

    const filteredVocabulary = vocabulary.filter(item => {
        const matchesSearch = item.word.toLowerCase().includes(searchTerm.toLowerCase()) || 
                              item.translation.toLowerCase().includes(searchTerm.toLowerCase());
        
        if (filterLevel === 'ALL') return matchesSearch;
        if (filterLevel === 'MASTERED') return matchesSearch && item.mastered;
        if (filterLevel === 'LEARNING') return matchesSearch && !item.mastered;
        return matchesSearch;
    });

    if (isLoading) {
        return (
            <div className="min-vh-100 d-flex flex-column justify-content-center align-items-center text-light">
                <Spinner animation="border" variant="info" className="mb-3" aria-hidden="true" />
                <h1 className="h5">Szószedet betöltése...</h1>
            </div>
        );
    }

    return (
        <Container className="py-5 text-light">
            {/* Header & Practice Triggers */}
            <div className="d-flex justify-content-between align-items-center mb-4 flex-wrap gap-3">
                <div>
                    <h1 className="h2 fw-bold mb-1"><span aria-hidden="true">📖</span> Szótár Hub & Tudástár</h1>
                    <p className="text-light opacity-75 mb-0">Tekintsd át az összes eddig elsajátított szavad és SRS fejlődésed.</p>
                </div>
                <div className="d-flex flex-wrap gap-3">
                    <Button 
                        variant="outline-info" 
                        size="lg" 
                        className="fw-bold rounded-pill px-4 shadow-sm"
                        onClick={() => navigate('/vocabulary/flashcards')}
                    >
                        <span aria-hidden="true">🃏</span> Szabad Gyakorlás
                    </Button>
                    <Button 
                        variant="info" 
                        size="lg" 
                        className="fw-bold text-dark rounded-pill px-4 shadow-sm"
                        onClick={() => navigate('/vocabulary/practice')}
                    >
                        <span aria-hidden="true">⚡</span> Dinamikus Teszt
                    </Button>
                </div>
            </div>

            {/* Filters and Search Bar */}
            <Card className="bg-dark border-secondary shadow-lg mb-4 rounded-4 p-3">
                <Row className="g-3 align-items-center">
                    <Col md={8}>
                        <Form.Label htmlFor="vocabulary-search">Keresés szó vagy jelentés alapján</Form.Label>
                        <Form.Control 
                            id="vocabulary-search"
                            type="text"
                            placeholder="Keresés szó vagy jelentés alapján..."
                            value={searchTerm}
                            onChange={(e) => setSearchTerm(e.target.value)}
                            className="bg-black text-light border-secondary shadow-none"
                        />
                    </Col>
                    <Col md={4}>
                        <Form.Label htmlFor="vocabulary-filter">Tanulási állapot</Form.Label>
                        <Form.Select 
                            id="vocabulary-filter"
                            value={filterLevel}
                            onChange={(e) => setFilterLevel(e.target.value)}
                            className="bg-black text-light border-secondary shadow-none"
                        >
                            <option value="ALL">Összes szó ({vocabulary.length})</option>
                            <option value="LEARNING">Tanulás alatt</option>
                            <option value="MASTERED">Mesteri szintű</option>
                        </Form.Select>
                    </Col>
                </Row>
            </Card>

            {/* Vocabulary Table */}
            <Card className="bg-dark border-secondary shadow-lg rounded-4 overflow-hidden">
                <div className="table-responsive" role="region" tabIndex={0} aria-label="Szószedet, görgethető táblázat">
                <Table hover variant="dark" className="vocabulary-table align-middle mb-0 text-center">
                    <caption className="visually-hidden">Szószedet, jelentések és ismétlési időpontok</caption>
                    <thead>
                        <tr className="text-secondary text-uppercase fs-7" style={{ letterSpacing: '1px' }}>
                            <th scope="col" className="py-3 text-start ps-4">Célszó (Idegen nyelv)</th>
                            <th scope="col" className="py-3 text-start">Jelentés (Magyar)</th>
                            <th scope="col" className="py-3">SRS Szint</th>
                            <th scope="col" className="py-3 pe-4">Következő Ismétlés</th>
                        </tr>
                    </thead>
                    <tbody>
                        {filteredVocabulary.length > 0 ? (
                            filteredVocabulary.map((item, idx) => (
                                <tr key={idx} className="border-secondary">
                                    <th scope="row" lang={user?.activeCourseCode || 'en'} className="text-start ps-4 fw-bold app-accent-text">{item.word}</th>
                                    <td lang="hu" className="text-start text-light opacity-90">{item.translation}</td>
                                    <td>
                                        <Badge bg={getSrsBadgeVariant(item.srsLevel)} className="px-3 py-2 rounded-pill">
                                            Szint {item.srsLevel}
                                        </Badge>
                                    </td>
                                    <td className="pe-4 text-secondary small">
                                        {item.nextPracticeAt ? formatToLocalTimestamp(item.nextPracticeAt) : 'Azonnal esedékes'}
                                    </td>
                                </tr>
                            ))
                        ) : (
                            <tr>
                                <td colSpan="4" className="py-5 text-center text-muted">
                                    Nem található szó a megadott szűrési feltételekkel.
                                </td>
                            </tr>
                        )}
                    </tbody>
                </Table>
                </div>
            </Card>
        </Container>
    );
};

export default VocabularyHub;
