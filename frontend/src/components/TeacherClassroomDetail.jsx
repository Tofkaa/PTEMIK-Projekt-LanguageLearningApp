import InvitationShare from './InvitationShare';
import React, { useState, useEffect, useCallback, useRef } from 'react';
import { useParams, useNavigate, useLocation } from 'react-router-dom';
import { Container, Tab, Tabs, Card, Button, Badge, Row, Col, ListGroup, Modal, Form, Spinner, Table, ProgressBar } from 'react-bootstrap';
import { classroomApi } from '../services/classroomApi';
import { assignmentApi } from '../services/assignmentApi';
import { lessonApi } from '../services/lessonApi';
import { useNotifications } from '../context/NotificationContext';
import { parseServerDate, formatToLocalDisplay, formatToLocalTimestamp, formatToUtcForServer } from '../utils/dateUtils';
import { useCurrentTime } from '../hooks/useCurrentTime';
import ExercisePreviewCard from '../components/ExercisePreviewCard';
import { useAuth } from '../context/AuthContext';
import { getUnseenIds, markAsViewed } from '../utils/viewedStore';
import AssignmentTimingModal from './AssignmentTimingModal';
import AssignmentInitialTiming from './AssignmentInitialTiming';
import LearningFeedback from './LearningFeedback';
import TimedSuccessFeedback from './TimedSuccessFeedback';
import LanguageText from './LanguageText';

/**
 * Dashboard component for teachers to manage a specific classroom.
 * Provides functionalities to monitor student progress, grade submissions,
 * manage members, and create dynamic/fixed assignments.
 * 
 * @component
 */
const TeacherClassroomDetail = ({ classroom }) => {
    const { id: classroomId } = useParams();
    const navigate = useNavigate();
    const location = useLocation();
    
    const classroomName = classroom?.name || location.state?.className || 'Osztályterem Kezelése';
    const classroomCourseCode = classroom?.courseCode || location.state?.courseCode || 'en';

    // State definitions
    const [pendingMembers, setPendingMembers] = useState([]);
    const [acceptedMembers, setAcceptedMembers] = useState([]);
    const [memberBusy, setMemberBusy] = useState(false);
    const [memberError, setMemberError] = useState('');
    const [memberFeedback, setMemberFeedback] = useState(null);
    const memberHeadingRef = useRef(null);
    const memberFeedbackSequence = useRef(0);
    const restoreMemberFocus = useRef(false);
    useEffect(() => {
        if (restoreMemberFocus.current) {
            if (document.activeElement === document.body) memberHeadingRef.current?.focus();
            restoreMemberFocus.current = false;
        }
    }, [pendingMembers, acceptedMembers]);
    const [assignments, setAssignments] = useState([]);
    const [isAssignmentModalOpen, setIsAssignmentModalOpen] = useState(false);
    const [timingAssignment, setTimingAssignment] = useState(null);
    const [initialTiming, setInitialTiming] = useState({});
    const [assignmentError, setAssignmentError] = useState('');
    
    const [lessons, setLessons] = useState([]);
    const [selectedLessonId, setSelectedLessonId] = useState('');
    const [availableExercises, setAvailableExercises] = useState([]);
    
    const [selectedExercisesData, setSelectedExercisesData] = useState([]);
    const [assignmentMode, setAssignmentMode] = useState('TEST'); 

    const [stats, setStats] = useState(null);
    const [advancedStats, setAdvancedStats] = useState(null);
    const [expandedMistakeIndex, setExpandedMistakeIndex] = useState(null);

    const { user } = useAuth();
    const [activeMainTab, setActiveMainTab] = useState('assignments');

    const toggleMistakePreview = (index) => {
        setExpandedMistakeIndex(prev => prev === index ? null : index);
    };

    const { notifications } = useNotifications();

     const currentTime = useCurrentTime();

    /**
     * @typedef {Object} AssignmentFormState
     * Represents the configuration payload for creating a new assignment.
     */
    const [assignmentForm, setAssignmentForm] = useState({
        title: '',
        description: '',
        hasFeedback: false,
        allowDictionary: true,
        isTest: false,
        isRandomized: true,
        allowRetries: false,
        allowLateSubmission: false,
        maxAttempts: '',
        timeLimitMinutes: '',
        availableFrom: '',
        availableUntil: '',
        exerciseIds: [],
        generationMode: 'FIXED', 
        questionCount: ''        
    });

    const [previewExerciseId, setPreviewExerciseId] = useState(null);
    const [isSubmitting, setIsSubmitting] = useState(false);
    const [isLoading, setIsLoading] = useState(true);


    /**
     * Fetches core classroom data including members, assignments, and statistics.
     */
   const fetchDashboardData = useCallback(async (silent = false) => {
        if (!silent) setIsLoading(true);
        try {
            const [pendingRes, acceptedRes, assignmentsRes] = await Promise.all([
                classroomApi.getMembers(classroomId, 'PENDING'),
                classroomApi.getMembers(classroomId, 'ACCEPTED'),
                assignmentApi.getClassroomAssignments(classroomId)
            ]);
            setPendingMembers(pendingRes.data);
            setAcceptedMembers(acceptedRes.data);
            setAssignments(assignmentsRes.data);

            const statsRes = await assignmentApi.getClassroomStatistics(classroomId);
            setStats(statsRes.data);

            const advStatsRes = await assignmentApi.getAdvancedClassroomStatistics(classroomId);
            setAdvancedStats(advStatsRes.data);
        } catch (error) {
            console.error("Hiba az adatok lekérésekor:", error);
        } finally {
            if (!silent) setIsLoading(false);
        }
    }, [classroomId]);

    const classroomPendingIds = (notifications?.teacherPendingJoinRequestIds || []).filter(id => id.startsWith(`${classroomId}:`));
    const classroomUngradedIds = (notifications?.teacherUngradedSubmissionIds || []).filter(id => id.startsWith(`${classroomId}:`));

    const unseenPendingList = getUnseenIds('viewedTeacherPending', user?.userId, classroomPendingIds);
    const unseenUngradedList = getUnseenIds('viewedTeacherUngraded', user?.userId, classroomUngradedIds);

    // JAVÍTVA: Valódi DTO mezőnevek figyelése
    const pingTrigger = JSON.stringify([classroomPendingIds, classroomUngradedIds]);

    useEffect(() => {
        fetchDashboardData(false);
    }, [fetchDashboardData]);

    useEffect(() => {
        if (!isLoading) {
            fetchDashboardData(true);
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [pingTrigger]);

    // Ha a tanár a "Tagság kezelése" fülön áll, minden érkező kérelmet azonnal olvasottnak jelölünk
    useEffect(() => {
        if (!isLoading && activeMainTab === 'members' && unseenPendingList.length > 0) {
            markAsViewed('viewedTeacherPending', user?.userId, unseenPendingList);
        }
    }, [isLoading, activeMainTab, unseenPendingList, user?.userId]);

    const handleViewSubmissions = (assignment) => {
        const prefix = `${classroomId}:${assignment.assignmentId}:`.toLowerCase();
        const matchingIds = unseenUngradedList.filter(id => id.toLowerCase().startsWith(prefix));
        markAsViewed('viewedTeacherUngraded', user?.userId, matchingIds);
        navigate(`/assignment/${assignment.assignmentId}/submissions`, {
            state: { assignmentTitle: assignment.title, classroomName: classroomName, timeLimitMinutes: assignment.timeLimitMinutes }
        });
    };

    // --- MEMBER MANAGEMENT ---
    
    /**
     * Accepts or rejects a pending join request.
     */
    const handleMemberAction = async (studentId, action, origin) => {
        if (memberBusy) return;
        setMemberBusy(true);
        setMemberError('');
        setMemberFeedback(null);
        try {
            if (action === 'remove') await classroomApi.kickMember(classroomId, studentId);
            else await classroomApi.moderateMember(classroomId, studentId, action === 'approve');
            restoreMemberFocus.current = document.activeElement === origin;
            await fetchDashboardData(true);
            setMemberFeedback({ id: ++memberFeedbackSequence.current, text: 'Az osztálytagság módosítása sikerült.' });
        } catch (error) {
            setMemberError(error.response?.data?.message || 'Nem sikerült módosítani az osztálytagságot. Próbáld újra!');
        } finally {
            setMemberBusy(false);
        }
    };

    /**
     * Removes an accepted student from the classroom.
     */
    const handleKick = async (studentId, origin) => {
        if (window.confirm("Biztosan el akarod távolítani ezt a diákot az osztályból?")) {
            await handleMemberAction(studentId, 'remove', origin);
        }
    };

    // --- ASSIGNMENT CREATION LOGIC ---
    
    const openAssignmentModal = async () => {
        try {
            const res = await lessonApi.getAllLessonsForChallenge(classroomCourseCode);
            setLessons(res.data);
            setIsAssignmentModalOpen(true);
        } catch (error) {
            console.error("Hiba a leckék betöltésekor:", error);
        }
    };

    const handleLessonChange = async (e) => {
        const lessonId = e.target.value;
        setSelectedLessonId(lessonId);
        if (lessonId) {
            try {
                const res = await lessonApi.getLessonExercises(lessonId);
                setAvailableExercises(res.data);
            } catch (error) { console.error("Hiba a feladatok betöltésekor:", error); }
        } else {
            setAvailableExercises([]);
        }
    };

    const handleDeleteAssignment = async (assignmentId) => {
        if (window.confirm("Biztosan törölni szeretnéd ezt a feladatot? Figyelem: Ha már vannak diákok, akik elkezdték vagy beküldték, az ő eredményeik is véglegesen elvesznek!")) {
            try {
                await assignmentApi.deleteAssignment(assignmentId);
                fetchDashboardData(true);
            } catch (error) {
                console.error("Hiba a feladat törlésekor:", error);
                alert("Nem sikerült törölni a feladatot.");
            }
        }
    };

    /**
     * Toggles the selection state of a specific exercise within the creation modal.
     * 
     * @param {Object} exercise - The exercise data object.
     */
    const toggleExerciseSelection = (exercise) => {
        const exerciseId = exercise.exerciseId;
        const isSelected = assignmentForm.exerciseIds.includes(exerciseId);

        if (isSelected) {
            setAssignmentForm(prev => ({ ...prev, exerciseIds: prev.exerciseIds.filter(id => id !== exerciseId) }));
            setSelectedExercisesData(prev => prev.filter(ex => ex.id !== exerciseId));
        } else {
            setAssignmentForm(prev => ({ ...prev, exerciseIds: [...prev.exerciseIds, exerciseId] }));
            setSelectedExercisesData(prev => [...prev, { id: exerciseId, title: exercise.content?.question || exercise.type }]);
        }
    };

    /**
     * Selects all exercises available in the currently selected lesson.
     */
    const handleSelectAllExercises = () => {
        const newIds = [];
        const newData = [];

        availableExercises.forEach(ex => {
            if (!assignmentForm.exerciseIds.includes(ex.exerciseId)) {
                newIds.push(ex.exerciseId);
                newData.push({ id: ex.exerciseId, title: ex.content?.question || ex.type });
            }
        });

        if (newIds.length > 0) {
            setAssignmentForm(prev => ({ ...prev, exerciseIds: [...prev.exerciseIds, ...newIds] }));
            setSelectedExercisesData(prev => [...prev, ...newData]);
        }
    };

    const removeFromSummary = (id) => {
        setAssignmentForm(prev => ({ ...prev, exerciseIds: prev.exerciseIds.filter(exId => exId !== id) }));
        setSelectedExercisesData(prev => prev.filter(ex => ex.id !== id));
    };

    const togglePreview = (exerciseId) => {
        setPreviewExerciseId(prev => prev === exerciseId ? null : exerciseId);
    };

    /**
     * Constructs the payload and submits the new assignment to the backend API.
     * Formats local dates to UTC to prevent cross-timezone execution blocks.
     */
    const handleAssignmentSubmit = async (e) => {
        e.preventDefault();
        if (isSubmitting) return; 
        setIsSubmitting(true);
        setAssignmentError('');

        try {
            const payload = {
                title: assignmentForm.title,
                description: assignmentForm.description,
                isTest: assignmentMode === 'TEST',
                hasFeedback: assignmentMode === 'TEST' ? assignmentForm.hasFeedback : true,
                allowDictionary: assignmentMode === 'TEST' ? assignmentForm.allowDictionary : true,
                allowLateSubmission: assignmentMode === 'TEST' ? assignmentForm.allowLateSubmission : true,
                randomized: assignmentForm.isRandomized,
                allowRetries: assignmentMode === 'TEST' ? assignmentForm.allowRetries : true,
                maxAttempts: assignmentMode === 'TEST' && assignmentForm.maxAttempts ? parseInt(assignmentForm.maxAttempts) : null,
                timeLimitMinutes: assignmentForm.timeLimitMinutes ? parseInt(assignmentForm.timeLimitMinutes) : null,

                availableFrom: formatToUtcForServer(assignmentForm.availableFrom),
                availableUntil: formatToUtcForServer(assignmentForm.availableUntil),
                accommodations: Object.entries(initialTiming).map(([userId, timing]) => ({
                    userId, extraMinutes: timing.unlimited ? 0 : Number(timing.extraMinutes), unlimited: timing.unlimited,
                    availableUntilOverride: formatToUtcForServer(timing.availableUntilOverride),
                })),

                exerciseIds: assignmentForm.exerciseIds,

                generationMode: assignmentForm.generationMode,
                questionCount: assignmentForm.generationMode === 'RANDOM_SUBSET' && assignmentForm.questionCount
                                ? parseInt(assignmentForm.questionCount)
                                : null
            };

            await assignmentApi.createAssignment(classroomId, payload);
            setIsAssignmentModalOpen(false);
            setInitialTiming({});
            setAssignmentForm({
                title: '', description: '', hasFeedback: false, isTest: false, isRandomized: true, allowDictionary: true, allowLateSubmission: false,
                allowRetries: false, maxAttempts: '', timeLimitMinutes: '', availableFrom: '', availableUntil: '', exerciseIds: [],
                generationMode: 'FIXED', questionCount: ''
            });
            setSelectedExercisesData([]);
            setAssignmentMode('TEST');
            setSelectedLessonId('');
            setAvailableExercises([]);
            fetchDashboardData(); 
        } catch (error) {
            console.error("Hiba a feladat kiosztásakor:", error);
            setAssignmentError(error.response?.data?.message || (error instanceof RangeError ? error.message : "Hiba történt a mentés során. A beállításaid megmaradtak; próbáld újra."));
        } finally {
            setIsSubmitting(false); 
        }
    };

    if (isLoading) {
        return <Container className="d-flex justify-content-center align-items-center" style={{ height: '50vh' }}><div className="spinner-border text-primary"></div></Container>;
    }

   
    
    const activeAssignments = assignments.filter(a => {
        const from = parseServerDate(a.availableFrom);
        const until = parseServerDate(a.availableUntil);
        return (!from || from.getTime() <= currentTime) && (!until || until.getTime() > currentTime);
    });

    const scheduledAssignments = assignments.filter(a => {
        const from = parseServerDate(a.availableFrom);
        return from && from.getTime() > currentTime;
    });

    const expiredAssignments = assignments.filter(a => {
        const until = parseServerDate(a.availableUntil);
        return until && until.getTime() <= currentTime;
    });

   const hasUngradedSubmission = (assignmentId) => {
        const prefix = `${classroomId}:${assignmentId}:`.toLowerCase();
        return unseenUngradedList.some(id => id.toLowerCase().startsWith(prefix));
    };

    const activeUngradedCount = activeAssignments.filter(a => hasUngradedSubmission(a.assignmentId)).length;
    const scheduledUngradedCount = scheduledAssignments.filter(a => hasUngradedSubmission(a.assignmentId)).length;
    const expiredUngradedCount = expiredAssignments.filter(a => hasUngradedSubmission(a.assignmentId)).length;

    
    const getHeatmapColor = (score) => {
        if (score === null || score === undefined) return 'bg-secondary bg-opacity-25 text-muted';
        if (score >= 80) return 'bg-success text-light fw-bold';
        if (score >= 50) return 'bg-warning text-dark fw-bold';
        return 'bg-danger text-light fw-bold';
    };

    const exportToCSV = () => {
        if (!advancedStats || !advancedStats.heatmapData || advancedStats.heatmapData.length === 0) {
            alert("Nincs exportálható adat.");
            return;
        }

        // CSV Fejléc összeállítása (Európai szabvány szerint pontosvesszővel elválasztva az Excelhez)
        const headers = ['Diák Neve', ...advancedStats.assignmentHeaders.map(h => h.title)];
        let csvContent = headers.join(';') + '\n';

        // Sorok (Diákok és pontszámaik)
        advancedStats.heatmapData.forEach(row => {
            const rowData = [row.studentName]; // Első oszlop a név
            
            advancedStats.assignmentHeaders.forEach(header => {
                const score = row.scores[header.assignmentId];
                rowData.push(score !== null ? `${score}%` : 'Nincs beadva');
            });
            
            csvContent += rowData.join(';') + '\n';
        });

        // UTF-8 BOM hozzáadása, hogy az Excel helyesen kezelje a magyar ékezeteket
        const bom = new Uint8Array([0xEF, 0xBB, 0xBF]);
        const blob = new Blob([bom, csvContent], { type: 'text/csv;charset=utf-8;' });
        
        // Letöltés triggerelése egy láthatatlan linkkel
        const link = document.createElement('a');
        const url = URL.createObjectURL(blob);
        link.setAttribute('href', url);
        link.setAttribute('download', `${classroomName.replace(/\s+/g, '_')}_Statisztika.csv`);
        link.style.visibility = 'hidden';
        
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
    };

    /**
     * Renders an individual assignment card with dynamic badges based on status and metadata.
     * 
     * @param {Object} a - The assignment object.
     * @param {string} status - Enum-like string ('active', 'scheduled', 'expired').
     */
    const renderAssignmentCard = (a, status) => (
        <Col md={6} lg={4} key={a.assignmentId}>
            <Card className="h-100 bg-dark text-light border-secondary shadow-sm">
                <Card.Body className="d-flex flex-column">
                    <div className="d-flex flex-wrap gap-2 justify-content-between align-items-start mb-2">
                        <Card.Title as="h2" className="h5 fw-bold text-primary m-0">{a.title}</Card.Title>
                        <div>
                            {hasUngradedSubmission(a.assignmentId) && (
                                <Badge bg="danger" className="animate-pulse shadow-sm me-2 border border-light">🔴 ÚJ BEKÜLDÉS</Badge>
                            )}
                            {status === 'scheduled' && <Badge bg="warning" text="dark" className="me-2">Ütemezett</Badge>}
                            {a.test ? <Badge bg="danger">Teszt</Badge> : <Badge bg="success">Gyakorló</Badge>}
                        </div>
                    </div>
                    
                    <Card.Text className="text-secondary mb-3 small flex-grow-1">{a.description}</Card.Text>

                    <div className="mb-3 p-2 bg-black bg-opacity-25 rounded border border-secondary" style={{ fontSize: '0.8rem' }}>
                        <div className="d-flex justify-content-between mb-1">
                            <span className="text-secondary">📅 Elérhető:</span>
                            <span className="text-info fw-bold">{formatToLocalDisplay(a.availableFrom)}</span>
                        </div>
                        <div className="d-flex justify-content-between">
                            <span className="text-secondary">🕒 Határidő:</span>
                            <span className={`fw-bold ${status === 'expired' ? 'text-danger' : 'text-warning'}`}>
                                {formatToLocalDisplay(a.availableUntil)}
                            </span>
                        </div>
                    </div>

                    <div className="mt-auto pt-2 border-top border-secondary d-flex flex-wrap gap-2">
                        <Button variant="outline-secondary" size="sm" aria-label={`Egyéni időbeállítások: ${a.title}`} onClick={() => setTimingAssignment(a)}>Egyéni időbeállítások</Button>
                        <Button 
                            aria-label={`Eredmények: ${a.title}`}
                            variant="outline-info" 
                            size="sm" 
                            className="flex-grow-1 fw-bold"
                           onClick={() => handleViewSubmissions(a)}
                        >
                            <span aria-hidden="true">📊 </span>Eredmények
                        </Button>
                        <Button aria-label={`törlés: ${a.title}`} variant="outline-danger" size="sm" onClick={() => handleDeleteAssignment(a.assignmentId)}>törlés<span aria-hidden="true">🗑️</span></Button>
                    </div>
                </Card.Body>
            </Card>
        </Col>
    );

    return (
        <Container className="py-4 text-light">
            {timingAssignment && <AssignmentTimingModal assignment={timingAssignment} onHide={() => setTimingAssignment(null)} />}
            <div className="d-flex justify-content-between align-items-center mb-4">
                <div>
                    <Button variant="outline-secondary" size="sm" onClick={() => navigate('/classrooms')} className="mb-2">← Vissza</Button>
                    <h1 className="h2 fw-bold m-0">{classroomName}</h1>
                </div>
            </div>

          <InvitationShare kind="classroom" classroomId={classroomId} targetName={classroomName} />
          <Tabs id="teacher-classroom-tabs" aria-label="Osztályterem kezelése" activeKey={activeMainTab} onSelect={(key) => setActiveMainTab(key)} className="mb-4 custom-dark-tabs">

                <Tab eventKey="assignments" title={
                    <span className="fw-bold">
                        📝 Feladatok & Tesztek
                        {unseenUngradedList.length > 0 && <Badge bg="danger" pill className="ms-2 animate-pulse">{unseenUngradedList.length}</Badge>}
                    </span>}>
                    
                    <div className="mb-4 mt-3">
                        <Button variant="primary" className="fw-bold px-4 shadow-sm" onClick={openAssignmentModal}>+ Új Feladat / Teszt Kiírása</Button>
                    </div>

            <Tabs id="teacher-assignment-tabs" aria-label="Kiosztott feladatok állapota" defaultActiveKey="active" className="mb-3 border-secondary custom-dark-tabs">
                <Tab eventKey="active" title={
                    <span className="fw-bold text-info">
                        🔥 Aktív ({activeAssignments.length})
                        {activeUngradedCount > 0 && <Badge bg="danger" pill className="ms-2 animate-pulse shadow-sm">{activeUngradedCount}</Badge>}
                    </span>
                }>
                    <Row className="g-4 mt-1">
                        {activeAssignments.map(a => renderAssignmentCard(a, 'active'))}
                    </Row>
                </Tab>

                <Tab eventKey="scheduled" title={
                    <span className="fw-bold text-warning">
                        📅 Ütemezett ({scheduledAssignments.length})
                        {scheduledUngradedCount > 0 && <Badge bg="danger" pill className="ms-2 animate-pulse shadow-sm">{scheduledUngradedCount}</Badge>}
                    </span>
                }>
                    <Row className="g-4 mt-1">
                        {scheduledAssignments.map(a => renderAssignmentCard(a, 'scheduled'))}
                    </Row>
                </Tab>

                <Tab eventKey="expired" title={
                    <span className="fw-bold text-secondary">
                        ⏳ Lejárt ({expiredAssignments.length})
                        {expiredUngradedCount > 0 && <Badge bg="danger" pill className="ms-2 animate-pulse shadow-sm">{expiredUngradedCount}</Badge>}
                    </span>
                }>
                    <Row className="g-4 mt-1">
                        {expiredAssignments.map(a => renderAssignmentCard(a, 'expired'))}
                    </Row>
                </Tab>
            </Tabs>
        </Tab>

                <Tab eventKey="members" title={
                    <span className="fw-bold">
                        <span aria-hidden="true">👥 </span>Tagság kezelése
                        {unseenPendingList.length > 0 && <Badge bg="danger" pill className="ms-2 animate-pulse">{unseenPendingList.length}</Badge>}
                    </span>}>

                    <h2 ref={memberHeadingRef} tabIndex={-1} className="h4 mt-3">Tagság kezelése</h2>
                    <LearningFeedback error={memberError} />
                    <TimedSuccessFeedback message={memberFeedback} focusTarget={memberHeadingRef}
                        onDismiss={() => setMemberFeedback(current => current?.id === memberFeedback?.id ? null : current)} />
                    <Row className="g-4 mt-1">
                        <Col md={6}>
                            <Card className="bg-dark border-secondary h-100">
                                <Card.Header className="bg-dark border-secondary d-flex justify-content-between">
                                    <h3 className="h5 m-0 text-warning">Várakozó</h3>
                                        <Badge bg={pendingMembers.length > 0 ? "danger" : "warning"} text={pendingMembers.length > 0 ? "light" : "dark"} className={pendingMembers.length > 0 ? "shadow animate-pulse" : ""}>
                                            {pendingMembers.length}
                                        </Badge>
                                </Card.Header>
                                <ListGroup variant="flush">
                                    {pendingMembers.map(member => (
                                        <ListGroup.Item key={member.memberId} className="bg-dark text-light border-secondary d-flex flex-wrap gap-3 justify-content-between">
                                            {/* JAVÍTÁS: userTag hozzáadása a várakozókhoz is */}
                                            <div>
                                                <div className="fw-bold">
                                                    {member.studentName || member.name}
                                                    <span className="text-secondary small ms-1">#{member.userTag}</span>
                                                </div>
                                                <div className="text-secondary small">{member.email}</div>
                                            </div>
                                            <div className="d-flex gap-2">
                                                <Button aria-label={`Csatlakozás elfogadása: ${member.studentName || member.name}#${member.userTag}`} aria-disabled={memberBusy} variant="success" size="sm" onClick={event => handleMemberAction(member.userId, 'approve', event.currentTarget)}><span aria-hidden="true">✓</span></Button>
                                                <Button aria-label={`Csatlakozás elutasítása: ${member.studentName || member.name}#${member.userTag}`} aria-disabled={memberBusy} variant="outline-danger" size="sm" onClick={event => handleMemberAction(member.userId, 'reject', event.currentTarget)}><span aria-hidden="true">✕</span></Button>
                                            </div>
                                        </ListGroup.Item>
                                    ))}
                                </ListGroup>
                            </Card>
                        </Col>
                        <Col md={6}>
                            <Card className="bg-dark border-secondary h-100">
                                <Card.Header className="bg-dark border-secondary d-flex justify-content-between">
                                    <h3 className="h5 m-0 text-info">Aktív</h3><Badge bg="info" text="dark">{acceptedMembers.length}</Badge>
                                </Card.Header>
                                <ListGroup variant="flush">
                                    {acceptedMembers.map(member => (
                                        <ListGroup.Item key={member.memberId} className="bg-dark text-light border-secondary d-flex flex-wrap gap-3 justify-content-between">
                                            <div><div className="fw-bold">{member.studentName || member.name} <span className="text-light small">#{member.userTag}</span></div><div className="text-secondary small">Belépett: {formatToLocalDisplay(member.joinedAt, false)}</div></div>
                                            <Button aria-label={`Kirúgás: ${member.studentName || member.name}#${member.userTag}`} aria-disabled={memberBusy} variant="outline-danger" size="sm" onClick={event => handleKick(member.userId, event.currentTarget)}>Kirúgás</Button>
                                        </ListGroup.Item>
                                    ))}
                                </ListGroup>
                            </Card>
                        </Col>
                    </Row>
                </Tab>

                <Tab eventKey="statistics" title={<span className="fw-bold"><span aria-hidden="true">📊 </span>Statisztika & Haladás</span>}>
                    {stats && advancedStats && (
                        <div className="mt-4">
                            <h2 className="h4">Osztálystatisztikák</h2>
                            <Tabs id="teacher-statistics-tabs" aria-label="Osztálystatisztikák nézetei" defaultActiveKey="overview" className="mb-3 border-secondary custom-dark-tabs small">
                                
                                {/* DEFAULT OVERVIEW */}
                                <Tab eventKey="overview" title="Áttekintés">
                                    <Row className="mb-4 text-center mt-3">
                                        <Col md={6}>
                                            <Card className="bg-dark border-info p-3">
                                                <h3 className="h6 text-secondary">Osztályterem Átlag</h3>
                                                <p className="h2 text-info fw-bold">{stats.classAverage}%</p>
                                            </Card>
                                        </Col>
                                        <Col md={6}>
                                            <Card className="bg-dark border-success p-3">
                                                <h3 className="h6 text-secondary">Kiadott feladatok</h3>
                                                <p className="h2 text-success fw-bold">{stats.totalAssignments} db</p>
                                            </Card>
                                        </Col>
                                    </Row>

                                    <Card className="bg-dark text-light border-secondary shadow-lg">
                                        <Card.Header className="bg-dark border-secondary fw-bold text-primary">
                                            <h3 className="h6 m-0">Diákok Aggregált Teljesítménye</h3>
                                        </Card.Header>
                                        <div className="table-responsive" role="region" tabIndex={0} aria-label="Diákok aggregált teljesítménye, görgethető táblázat">
                                        <Table hover variant="dark" className="classroom-statistics-table m-0">
                                            <caption className="visually-hidden">Diákok aggregált teljesítménye</caption>
                                            <thead>
                                                <tr className="text-secondary">
                                                    <th scope="col">Diák Neve</th>
                                                    <th scope="col" className="text-center">Befejezett</th>
                                                    <th scope="col" className="text-center">Haladás</th>
                                                    <th scope="col" className="text-center">Átlagpont</th>
                                                    <th scope="col">Utolsó Aktivitás</th>
                                                </tr>
                                            </thead>
                                            <tbody>
                                                {stats.studentProgress.map((s, idx) => (
                                                    <tr key={idx} className="align-middle">
                                                        <th scope="row" className="fw-normal">
                                                            <div className="fw-bold">{s.studentName}</div>
                                                            <div className="small text-secondary">{s.studentEmail}</div>
                                                        </th>
                                                        <td className="text-center"><span aria-hidden="true">{s.completedCount} / {stats.totalAssignments}</span><span className="visually-hidden">Összesen {stats.totalAssignments} feladatból {s.completedCount} befejezett</span></td>
                                                        <td className="text-center" style={{ width: '150px' }}>
                                                            <ProgressBar style={{ height: '8px' }}><ProgressBar now={stats.totalAssignments > 0 ? (s.completedCount / stats.totalAssignments) * 100 : 0}
                                                                aria-label={`${s.studentName} haladása`}
                                                                aria-valuetext={`Összesen ${stats.totalAssignments} feladatból ${s.completedCount} befejezett`}
                                                                variant="info" /></ProgressBar>
                                                        </td>
                                                        <td className="text-center">
                                                            <Badge bg={s.averageScore >= 80 ? "success" : s.averageScore >= 50 ? "warning" : "danger"} className="fs-6">
                                                                {s.averageScore}%
                                                            </Badge>
                                                        </td>
                                                        <td className="text-secondary small">
                                                            {s.lastActivity !== "Nincs adat" ? formatToLocalTimestamp(s.lastActivity) : "Még nem kezdte el"}
                                                        </td>
                                                    </tr>
                                                ))}
                                            </tbody>
                                        </Table>
                                        </div>
                                    </Card>
                                </Tab>

                                {/* HEATMAP */}
                                <Tab eventKey="heatmap" title="Nehézségi Hőtérkép">
                                    <Card className="bg-dark text-light border-secondary shadow-lg mt-3">
                                        <Card.Header className="bg-dark border-secondary d-flex justify-content-between align-items-center">
                                            <h3 className="h6 m-0 fw-bold text-warning">Teljesítmény Mátrix</h3>
                                            <Button variant="outline-success" size="sm" onClick={exportToCSV} className="fw-bold d-flex align-items-center gap-2">
                                                <span>⬇️</span> CSV Export
                                            </Button>
                                        </Card.Header>
                                        <div className="table-responsive" role="region" tabIndex={0} aria-label="Teljesítménymátrix, görgethető táblázat">
                                            <Table bordered variant="dark" className="classroom-heatmap m-0 text-center align-middle" style={{ minWidth: '800px' }}>
                                                <caption className="visually-hidden">Tanulók feladatonkénti eredményei</caption>
                                                {/* X-plane: Assignment names */}
                                                <thead className="border-bottom border-2 border-info bg-black bg-opacity-50">
                                                    <tr>
                                                        <th scope="col" className="text-start text-info text-uppercase p-3 border-end border-2 border-info" style={{ minWidth: '220px' }}>
                                                            Diák \ Feladat
                                                        </th>
                                                        {advancedStats.assignmentHeaders.map(header => (
                                                            <th scope="col" key={header.assignmentId} title={header.title} className="text-light p-3">
                                                                <div className="heatmap-assignment-title">
                                                                    {header.title}
                                                                </div>
                                                            </th>
                                                        ))}
                                                    </tr>
                                                </thead>
                                                
                                                {/* Y-plane and data: Student names and the percentages */}
                                                <tbody>
                                                    {advancedStats.heatmapData.map((row, idx) => (
                                                        <tr key={idx}>
                                                            <th scope="row" className="text-start fw-bold text-light bg-black bg-opacity-25 p-3 border-end border-2 border-info">
                                                                {row.studentName}
                                                            </th>
                                                            
                                                            {advancedStats.assignmentHeaders.map(header => {
                                                                const score = row.scores[header.assignmentId];
                                                                const hasScore = typeof score === 'number' && Number.isFinite(score);
                                                                return (
                                                                    <td key={header.assignmentId} className={`p-0 ${getHeatmapColor(score)}`}>
                                                                        <div className="p-3 w-100 h-100 d-flex align-items-center justify-content-center">
                                                                            {hasScore ? `${score}%` : 'Nincs beadva'}
                                                                        </div>
                                                                    </td>
                                                                );
                                                            })}
                                                        </tr>
                                                    ))}
                                                    {advancedStats.heatmapData.length === 0 && (
                                                        <tr>
                                                            <td colSpan={advancedStats.assignmentHeaders.length + 1} className="text-muted py-4">Nincsenek adatok a hőtérképhez.</td>
                                                        </tr>
                                                    )}
                                                </tbody>
                                            </Table>
                                        </div>
                                    </Card>
                                </Tab>

                             <Tab eventKey="mistakes" title="Gyakori Hibák">
                                    <Card className="bg-dark text-light border-secondary shadow-lg mt-3">
                                        <Card.Header className="bg-dark border-secondary fw-bold text-danger">
                                            <h3 className="h6 m-0">Top 5 Leggyakrabban Elrontott Kérdés</h3>
                                        </Card.Header>
                                        <ListGroup variant="flush">
                                            {advancedStats.topMistakes.length === 0 ? (
                                                <ListGroup.Item className="bg-dark text-light text-center py-4 border-0">
                                                    Még nincsenek rögzített hibák az osztályban.
                                                </ListGroup.Item>
                                            ) : (
                                                advancedStats.topMistakes.map((mistake, idx) => (
                                                    <ListGroup.Item key={idx} className="bg-dark text-light border-secondary p-3">
                                                        
                                                        {/* TITLES AND BUTTONS */}
                                                        <div className="d-flex flex-wrap gap-3 justify-content-between align-items-center">
                                                            <div className="d-flex flex-wrap align-items-center gap-3">
                                                                <div className="fs-4 fw-bold text-secondary">#{idx + 1}</div>
                                                                <div>
                                                                    <div className="fw-bold"><LanguageText text={mistake.question}
                                                                        lang={mistake.exercise?.content?.questionLang || 'hu'} parts={mistake.exercise?.content?.questionParts} /></div>
                                                                    <div className="small text-info mt-1">
                                                                        <span className="text-secondary">Forrás:</span> {mistake.assignmentTitle}
                                                                    </div>
                                                                </div>
                                                            </div>
                                                            <div className="d-flex flex-wrap align-items-center gap-3">
                                                                <Badge bg="danger" pill className="fs-6 px-3 py-2">
                                                                    {mistake.mistakeCount} rontott válasz
                                                                </Badge>
                                                                {mistake.exercise && (
                                                                    <Button aria-label={`${expandedMistakeIndex === idx ? 'elrejtés' : 'előnézet'}: ${mistake.assignmentTitle}, ${idx + 1}. hibacsoport`} aria-expanded={expandedMistakeIndex === idx}
                                                                        aria-controls={expandedMistakeIndex === idx ? `teacher-mistake-preview-${idx}` : undefined} variant="link" size="sm" className="text-info p-0 text-decoration-none" onClick={() => toggleMistakePreview(idx)}>
                                                                        {expandedMistakeIndex === idx ? 'elrejtés' : <>előnézet<span aria-hidden="true">👁️</span></>}
                                                                    </Button>
                                                                )}
                                                            </div>
                                                        </div>

                                                        {/* EXPANDABLE PREVIEW */}
                                                      {expandedMistakeIndex === idx && mistake.exercise && (
                                                            <div id={`teacher-mistake-preview-${idx}`} className="mt-3">
                                                                <ExercisePreviewCard 
                                                                    exercise={mistake.exercise} 
                                                                    serverCorrectAnswer={mistake.serverCorrectAnswer}
                                                                />
                                                            </div>
                                                        )}

                                                    </ListGroup.Item>
                                                ))
                                            )}
                                        </ListGroup>
                                    </Card>
                                </Tab>
                            </Tabs>
                        </div>
                    )}
                </Tab>
            </Tabs>

           <Modal 
                show={isAssignmentModalOpen}
                aria-labelledby="assignment-builder-title"
                onHide={() => { if (!isSubmitting) setIsAssignmentModalOpen(false); }}
                size="xl" 
                centered 
            >
                <Modal.Header closeButton closeLabel="Ablak bezárása">
                    <Modal.Title as="h2" id="assignment-builder-title" className="h4 fw-bold app-accent-text">Feladat / Tananyag Összeállítása</Modal.Title>
                </Modal.Header>
                <Form onSubmit={handleAssignmentSubmit}>
                    <Modal.Body>
                        <Row>
                            {/* LEFT: BASICS */}
                            <Col lg={4}>
                                <div className="p-3 border rounded mb-3">
                                    <Form.Label className="text-info fw-bold mb-2">Cél:</Form.Label>
                                    <Form.Check id="assignment-mode-test" type="radio" label="Szigorú Teszt" name="amode" checked={assignmentMode === 'TEST'} onChange={() => setAssignmentMode('TEST')} className="text-danger fw-bold mb-1" />
                                    <Form.Check id="assignment-mode-practice" type="radio" label="Tananyag / Gyakorló" name="amode" checked={assignmentMode === 'PRACTICE'} onChange={() => setAssignmentMode('PRACTICE')} className="text-success fw-bold mb-3" />
                                    
                                    <Form.Group controlId="assignment-title" className="mb-3"><Form.Label className="small fw-bold">Cím</Form.Label><Form.Control type="text" required value={assignmentForm.title} onChange={e => setAssignmentForm({...assignmentForm, title: e.target.value})} /></Form.Group>
                                    <Form.Group controlId="assignment-description" className="mb-3"><Form.Label className="small fw-bold">Leírás</Form.Label><Form.Control as="textarea" rows={2} value={assignmentForm.description} onChange={e => setAssignmentForm({...assignmentForm, description: e.target.value})} /></Form.Group>
                                    <Form.Group controlId="assignment-duration" className="mb-3"><Form.Label className="small fw-bold">Idő (perc)</Form.Label><div className="d-flex gap-2"><Form.Control type="number"
                                            min="1" 
                                            value={assignmentForm.timeLimitMinutes} 
                                            onChange={e => {
                                                const val = e.target.value.replace(/\D/g, '');
                                                setAssignmentForm({...assignmentForm, timeLimitMinutes: val});
                                            }} />
                                        <Button type="button" variant="outline-secondary" aria-label="Idő csökkentése 1 perccel"
                                            disabled={isSubmitting || !assignmentForm.timeLimitMinutes || Number(assignmentForm.timeLimitMinutes) <= 1}
                                            onClick={() => setAssignmentForm(previous => ({ ...previous, timeLimitMinutes: String(Math.max(1, Number(previous.timeLimitMinutes) - 1)) }))}>−</Button>
                                        <Button type="button" variant="outline-secondary" aria-label="Idő növelése 1 perccel" disabled={isSubmitting}
                                            onClick={() => setAssignmentForm(previous => ({ ...previous, timeLimitMinutes: String(Number(previous.timeLimitMinutes || 0) + 1) }))}>+</Button>
                                    </div></Form.Group>
                                    
                                    {assignmentMode === 'TEST' && (
                                        <Form.Group controlId="assignment-attempts" className="mb-3">
                                            <Form.Label className="small text-warning fw-bold">Maximum próbálkozás (Újraírás)</Form.Label>
                                            <Form.Control 
                                              type="number" 
                                                min="1"
                                                placeholder="Üresen hagyva korlátlan" 
                                                value={assignmentForm.maxAttempts || ''} 
                                                onChange={e => {
                                                    const val = e.target.value.replace(/\D/g, '');
                                                    setAssignmentForm({...assignmentForm, maxAttempts: val});
                                                }}
                                            />
                                        </Form.Group>
                                    )}
                                    
                                    <div className="small border-top pt-2 mt-2 mb-2">
                                        <Form.Label className="text-warning fw-bold mb-2">Kérdések kiosztása a diákoknak:</Form.Label>
                                        
                                        <Form.Check 
                                            type="radio" 
                                            id="assignment-generation-fixed" label="Minden kijelölt feladatot megkapnak"
                                            name="genMode" 
                                            checked={assignmentForm.generationMode === 'FIXED'} 
                                            onChange={() => setAssignmentForm({...assignmentForm, generationMode: 'FIXED'})} 
                                            className="mb-2"
                                        />
                                        
                                        <Form.Check 
                                            type="radio" 
                                            id="assignment-generation-random" label="Véletlenszerű sorsolás a kijelöltekből (Anti-Cheat)"
                                            name="genMode" 
                                            checked={assignmentForm.generationMode === 'RANDOM_SUBSET'} 
                                            onChange={() => setAssignmentForm({...assignmentForm, generationMode: 'RANDOM_SUBSET'})} 
                                            className="mb-2 text-info"
                                        />

                                        {assignmentForm.generationMode === 'RANDOM_SUBSET' && (
                                            <Form.Group controlId="assignment-question-count" className="mt-2 ms-3 p-2 bg-secondary bg-opacity-10 rounded border border-info">
                                                <Form.Label className="small text-info fw-bold">Hány kérdést kapjon 1 diák?</Form.Label>
                                                <Form.Control 
                                                    type="number" 
                                                    min="1"
                                                    max={assignmentForm.exerciseIds.length || 1}
                                                    placeholder={`Max: ${assignmentForm.exerciseIds.length} db`}
                                                    value={assignmentForm.questionCount} 
                                                    onChange={e => {
                                                        const val = e.target.value.replace(/\D/g, '');
                                                        setAssignmentForm({...assignmentForm, questionCount: val});
                                                    }}
                                                />
                                                <div className="small mt-1 opacity-75">
                                                    A rendszer {assignmentForm.exerciseIds.length} db kijelölt kérdésből fog véletlenszerűen sorsolni ennyit minden diáknak.
                                                </div>
                                            </Form.Group>
                                        )}
                                    </div>
                                   
                                    {assignmentMode === 'TEST' && (
                                        <div className="small border-top pt-2">
                                            <Form.Check id="assignment-feedback" type="switch" label="Azonnali visszajelzés" className="mb-1"
                                                checked={assignmentForm.hasFeedback} 
                                                onChange={e => setAssignmentForm({...assignmentForm, hasFeedback: e.target.checked})} />

                                             <Form.Check id="assignment-dictionary" type="switch" label="Szótár és Tippek engedélyezése" className="mb-1"
                                                checked={assignmentForm.allowDictionary} 
                                                onChange={e => setAssignmentForm({...assignmentForm, allowDictionary: e.target.checked})} />   
                                           
                                            <Form.Check id="assignment-randomized" type="switch" label="Kevert sorrend" className="mb-1"
                                                checked={assignmentForm.isRandomized} 
                                                onChange={e => setAssignmentForm({...assignmentForm, isRandomized: e.target.checked})} />
                                                
                                            <Form.Check id="assignment-retries" type="switch" label="Második esély a hibás válaszoknál"
                                                checked={assignmentForm.allowRetries} 
                                                onChange={e => setAssignmentForm({...assignmentForm, allowRetries: e.target.checked})} />
                                            
                                            {assignmentForm.timeLimitMinutes > 0 && (
                                                <Form.Check id="assignment-late" type="switch" label="Késedelmes beadás engedélyezése"
                                                    className="mt-2"
                                                    checked={assignmentForm.allowLateSubmission} 
                                                    onChange={e => setAssignmentForm({...assignmentForm, allowLateSubmission: e.target.checked})} />
                                            )}
                                        </div>
                                    )}
                                </div>
                            </Col>

                            {/* MIDDLE: SELECTION */}
                            <Col lg={4} className="border-start">
                                <Form.Label htmlFor="assignment-source-lesson" className="fw-bold text-info">Válogatás Forrásból</Form.Label>
                                <Form.Select id="assignment-source-lesson" className="mb-3" value={selectedLessonId} onChange={handleLessonChange}>
                                    <option value="">-- Válassz leckét --</option>
                                    {lessons.map(l => <option key={l.lessonId} value={l.lessonId}>[{l.orderIndex ? `${l.orderIndex}. lecke` : ''}] {l.title}</option>)}
                                </Form.Select>
                                {availableExercises.length > 0 && (
                                    <>
                                        <Button variant="outline-info" size="sm" className="w-100 mb-2" onClick={handleSelectAllExercises}>Összes kijelölése (ebből a leckéből)</Button>
                                        <div style={{ maxHeight: '350px', overflowY: 'auto' }} className="pe-2 custom-scrollbar">
                                            {availableExercises.map(ex => (
                                                <div key={ex.exerciseId} className="border-bottom py-2">
                                                    <div className="d-flex justify-content-between align-items-center">
                                                        <Form.Check 
                                                        id={`ex-${ex.exerciseId}`} 
                                                        label={
                                                            <span className="small d-flex align-items-center gap-2">
                                                                {ex.difficulty === 'EASY' && <span className="badge bg-success" style={{ fontSize: '0.65rem' }}>EASY</span>}
                                                                {ex.difficulty === 'MEDIUM' && <span className="badge bg-warning text-dark" style={{ fontSize: '0.65rem' }}>MEDIUM</span>}
                                                                {ex.difficulty === 'HARD' && <span className="badge bg-danger" style={{ fontSize: '0.65rem' }}>HARD</span>}
                                                                <span>{ex.content?.question || ex.type}</span>
                                                            </span>
                                                        } 
                                                        checked={assignmentForm.exerciseIds.includes(ex.exerciseId)} 
                                                        onChange={() => toggleExerciseSelection(ex)} 
                                                    />
                                                        <Button variant="link" size="sm" className="text-info p-0" aria-label={`Feladat előnézete: ${ex.content?.question || ex.type}`} aria-expanded={previewExerciseId === ex.exerciseId} onClick={() => togglePreview(ex.exerciseId)}><span aria-hidden="true">👁️</span></Button>
                                                    </div>
                                                    {previewExerciseId === ex.exerciseId && (
                                                        <div className="mt-1 p-2 bg-secondary bg-opacity-10 rounded border border-info small">
                                                            <strong>Típus:</strong> {ex.type}<br/>
                                                            {ex.content?.options && <div><strong>Opciók:</strong> {ex.content.options.join(", ")}</div>}
                                                        </div>
                                                    )}
                                                </div>
                                            ))}
                                        </div>
                                    </>
                                )}
                            </Col>

                            {/* RIGHT: SUMMARY */}
                            <Col lg={4} className="border-start">
                                <h3 className="h6 fw-bold text-success mb-3">Kiválasztott tartalom ({selectedExercisesData.length} db)</h3>
                                <div style={{ maxHeight: '450px', overflowY: 'auto' }} className="pe-2 custom-scrollbar">
                                    {selectedExercisesData.length === 0 ? <p className="small opacity-75">Még nem választottál feladatot.</p> : (
                                        <ListGroup variant="flush">
                                            {selectedExercisesData.map((ex, index) => (
                                                <ListGroup.Item key={ex.id} className="d-flex justify-content-between align-items-center p-2 bg-transparent">
                                                    <div className="small flex-grow-1 pe-2">{index + 1}. {ex.title}</div>
                                                    <Button variant="outline-danger" size="sm" className="border-0" aria-label={`Feladat eltávolítása: ${ex.title}`} onClick={() => removeFromSummary(ex.id)}><span aria-hidden="true">✕</span></Button>
                                                </ListGroup.Item>
                                            ))}
                                        </ListGroup>
                                    )}
                                </div>
                            </Col>
                        </Row>
                        
                        <Row className="mt-4 border-top pt-3 small">
                            <Col md={6}>
                                <Form.Group controlId="assignment-available-from">
                                    <Form.Label className="fw-bold opacity-75">Elérhető:</Form.Label>
                                    <Form.Control 
                                        type="datetime-local" 
                                        value={assignmentForm.availableFrom} 
                                        onChange={e => setAssignmentForm({...assignmentForm, availableFrom: e.target.value})} 
                                    />
                                </Form.Group>
                            </Col>
                            <Col md={6}>
                                <Form.Group controlId="assignment-available-until">
                                    <Form.Label className="fw-bold opacity-75">Határidő:</Form.Label>
                                    <Form.Control 
                                        type="datetime-local" 
                                        value={assignmentForm.availableUntil} 
                                        onChange={e => setAssignmentForm({...assignmentForm, availableUntil: e.target.value})} 
                                    />
                                </Form.Group>
                            </Col>
                        </Row>
                        <AssignmentInitialTiming students={acceptedMembers} value={initialTiming} onChange={setInitialTiming}
                            timeLimitMinutes={assignmentForm.timeLimitMinutes} saving={isSubmitting} />
                        <LearningFeedback error={assignmentError} />
                    </Modal.Body>
                    <Modal.Footer>
                        <Button variant="outline-secondary" disabled={isSubmitting} onClick={() => setIsAssignmentModalOpen(false)}>Mégse</Button>
                        <Button variant="primary" 
                            type="submit" 
                            className="fw-bold px-4" 
                            disabled={assignmentForm.exerciseIds.length === 0 || isSubmitting}
                            >
                            {isSubmitting ? (
                                <><Spinner size="sm" animation="border" className="me-2" /> Mentés...</>
                            ) : (
                                assignmentMode === 'TEST' ? 'Dolgozat Kiosztása' : 'Tananyag Publikálása'
                            )}
                        </Button>
                    </Modal.Footer>
                </Form>
            </Modal>
        </Container>
    );
};

export default TeacherClassroomDetail;
