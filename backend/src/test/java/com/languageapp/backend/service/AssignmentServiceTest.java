package com.languageapp.backend.service;

import com.languageapp.backend.dto.request.AssignmentCreateRequest;
import com.languageapp.backend.dto.response.AssignmentStartResponse;
import com.languageapp.backend.entity.*;
import com.languageapp.backend.exception.BadRequestException;
import com.languageapp.backend.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AssignmentServiceTest {

    @Mock private ClassroomAssignmentRepository assignmentRepository;
    @Mock private ClassroomRepository classroomRepository;
    @Mock private ExerciseRepository exerciseRepository;
    @Mock private UserRepository userRepository;
    @Mock private ClassroomMemberRepository classroomMemberRepository;
    @Mock private AssignmentSessionRepository sessionRepository;
    @Mock private EvaluationService evaluationService;
    @Mock private StreakService streakService;
    @Mock private SseService sseService;

    @InjectMocks
    private AssignmentService assignmentService;

    private User teacher;
    private User student;
    private Classroom classroom;
    private ClassroomAssignment assignment;

    @BeforeEach
    void setUp() {
        teacher = new User();
        teacher.setUserId(UUID.randomUUID());
        teacher.setEmail("tanar@test.com");

        student = new User();
        student.setUserId(UUID.randomUUID());
        student.setEmail("diak@test.com");

        classroom = new Classroom();
        classroom.setClassroomId(UUID.randomUUID());
        classroom.setTeacher(teacher);

        assignment = new ClassroomAssignment();
        assignment.setAssignmentId(UUID.randomUUID());
        assignment.setClassroom(classroom);
        assignment.setTitle("Német Szódolgozat");
    }

    // --- 1. TESZT: Új feladat kiadása sikeresen (Tanár jogosultsággal) ---
    @Test
    void createAssignment_AsOwner_ShouldSaveAssignmentAndSendPings() {
        // Arrange
        AssignmentCreateRequest request = new AssignmentCreateRequest();
        request.setTitle("Új Teszt");
        request.setExerciseIds(List.of(UUID.randomUUID(), UUID.randomUUID()));

        when(classroomRepository.findById(classroom.getClassroomId())).thenReturn(Optional.of(classroom));
        when(exerciseRepository.findAllById(request.getExerciseIds())).thenReturn(List.of(new Exercise(), new Exercise()));

        // Act
        assignmentService.createAssignment(classroom.getClassroomId(), request, teacher.getEmail());

        // Assert
        ArgumentCaptor<ClassroomAssignment> assignmentCaptor = ArgumentCaptor.forClass(ClassroomAssignment.class);
        verify(assignmentRepository, times(1)).save(assignmentCaptor.capture());

        ClassroomAssignment savedAssignment = assignmentCaptor.getValue();
        assertEquals("Új Teszt", savedAssignment.getTitle(), "A címnek egyeznie kell");
        assertEquals(2, savedAssignment.getExercises().size(), "A kijelölt feladatoknak mentésre kell kerülniük");
    }

    // --- 2. TESZT: Anti-Cheat Engine (RANDOM_SUBSET) ---
    @Test
    void startAssignment_WithRandomSubset_ShouldReturnLimitedExercises() {
        // Arrange
        // Adunk az assignment-nek 5 feladatot, de a beállítás szerint egy diák csak 2-t kaphat!
        List<Exercise> allExercises = new ArrayList<>();
        for(int i=0; i<5; i++) allExercises.add(new Exercise());

        assignment.setExercises(allExercises);
        assignment.setGenerationMode("RANDOM_SUBSET");
        assignment.setQuestionCount(2); // <-- A LÉNYEG
        assignment.setAvailableFrom(LocalDateTime.now().minusDays(1)); // Már elérhető

        when(userRepository.findByEmail(student.getEmail())).thenReturn(Optional.of(student));
        when(assignmentRepository.findById(assignment.getAssignmentId())).thenReturn(Optional.of(assignment));
        when(classroomMemberRepository.existsByClassroom_ClassroomIdAndUser_UserId(classroom.getClassroomId(), student.getUserId())).thenReturn(true);
        when(sessionRepository.findAllByAssignment_AssignmentIdAndUser_UserId(assignment.getAssignmentId(), student.getUserId())).thenReturn(new ArrayList<>());
        when(sessionRepository.save(any(AssignmentSession.class))).thenAnswer(i -> i.getArguments()[0]);

        // Act
        AssignmentStartResponse response = assignmentService.startAssignment(assignment.getAssignmentId(), student.getEmail());

        // Assert
        assertNotNull(response);
        assertEquals(2, response.getExercises().size(), "A rendszernek pontosan 2 feladatot kellett kisorsolnia az 5-ből az Anti-Cheat szabály miatt!");
    }

    // --- 3. TESZT: Jogosulatlan indítási kísérlet (Nem tagja az osztálynak) ---
    @Test
    void startAssignment_NotAMember_ShouldThrowBadRequestException() {
        // Arrange
        when(userRepository.findByEmail(student.getEmail())).thenReturn(Optional.of(student));
        when(assignmentRepository.findById(assignment.getAssignmentId())).thenReturn(Optional.of(assignment));
        // A diák NINCS benne az osztályban!
        when(classroomMemberRepository.existsByClassroom_ClassroomIdAndUser_UserId(classroom.getClassroomId(), student.getUserId())).thenReturn(false);

        // Act & Assert
        BadRequestException exception = assertThrows(BadRequestException.class, () ->
                assignmentService.startAssignment(assignment.getAssignmentId(), student.getEmail())
        );

        assertTrue(exception.getMessage().contains("Access denied"), "Biztonsági hibát kell dobnia jogosulatlan hozzáférésnél!");
        verify(sessionRepository, never()).save(any()); // Biztosan nem hozott létre sessiont
    }
}