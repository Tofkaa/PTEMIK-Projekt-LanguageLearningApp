package com.languageapp.backend.service;

import com.languageapp.backend.dto.request.ClassroomCreateRequest;
import com.languageapp.backend.dto.response.ClassroomResponse;
import com.languageapp.backend.entity.Classroom;
import com.languageapp.backend.entity.ClassroomMember;
import com.languageapp.backend.entity.Course;
import com.languageapp.backend.entity.User;
import com.languageapp.backend.enums.MembershipStatus;
import com.languageapp.backend.enums.Role;
import com.languageapp.backend.exception.BadRequestException;
import com.languageapp.backend.repository.ClassroomMemberRepository;
import com.languageapp.backend.repository.ClassroomRepository;
import com.languageapp.backend.repository.CourseRepository;
import com.languageapp.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClassroomServiceTest {

    @Mock private ClassroomRepository classroomRepository;
    @Mock private ClassroomMemberRepository classroomMemberRepository;
    @Mock private UserRepository userRepository;
    @Mock private CourseRepository courseRepository;

    @InjectMocks
    private ClassroomService classroomService;

    private User teacher;
    private User student;
    private Course course;
    private Classroom classroom;

    @BeforeEach
    void setUp() {
        teacher = new User();
        teacher.setUserId(UUID.randomUUID());
        teacher.setEmail("tanar@test.com");
        teacher.setRole(Role.TEACHER); // Fontos a létrehozás teszteléséhez!

        student = new User();
        student.setUserId(UUID.randomUUID());
        student.setEmail("diak@test.com");
        student.setRole(Role.STUDENT);

        course = new Course();
        course.setCourseId(UUID.randomUUID());
        course.setLanguageCode("en");

        classroom = new Classroom();
        classroom.setClassroomId(UUID.randomUUID());
        classroom.setName("Angol Kezdő");
        classroom.setTeacher(teacher);
        classroom.setCourse(course);
        classroom.setInviteCode("TESTCODE");
    }

    // --- 1. TESZT: Osztályterem létrehozása sikeresen (Tanárként) ---
    @Test
    void createClassroom_AsTeacher_ShouldCreateClassroomAndGenerateInviteCode() {
        // Arrange
        ClassroomCreateRequest request = new ClassroomCreateRequest();
        request.setName("Új Osztály");
        request.setDescription("Leírás");
        request.setCourseCode("en");

        when(userRepository.findByEmail(teacher.getEmail())).thenReturn(Optional.of(teacher));
        when(courseRepository.findByLanguageCode("en")).thenReturn(Optional.of(course));

        // Trükk: azt mondjuk, hogy a generált kód még nem létezik az adatbázisban
        when(classroomRepository.existsByInviteCode(anyString())).thenReturn(false);
        when(classroomRepository.save(any(Classroom.class))).thenAnswer(i -> i.getArguments()[0]);

        // Act
        ClassroomResponse response = classroomService.createClassroom(request, teacher.getEmail());

        // Assert
        assertNotNull(response);
        assertEquals("Új Osztály", response.getName());
        assertNotNull(response.getInviteCode(), "A rendszernek generálnia kell egy meghívó kódot!");
        assertEquals("en", response.getCourseCode());

        ArgumentCaptor<Classroom> classroomCaptor = ArgumentCaptor.forClass(Classroom.class);
        verify(classroomRepository, times(1)).save(classroomCaptor.capture());
    }

    // --- 2. TESZT: Osztályterem létrehozása jogosulatlanul (Diákként) ---
    @Test
    void createClassroom_AsStudent_ShouldThrowBadRequestException() {
        // Arrange
        student.setRole(Role.STUDENT); // Diák próbál osztályt csinálni
        when(userRepository.findByEmail(student.getEmail())).thenReturn(Optional.of(student));

        ClassroomCreateRequest request = new ClassroomCreateRequest();

        // Act & Assert
        BadRequestException exception = assertThrows(BadRequestException.class, () ->
                classroomService.createClassroom(request, student.getEmail())
        );

        assertTrue(exception.getMessage().contains("Only teachers can create classrooms"), "A rendszernek blokkolnia kell a diákokat!");
        verify(classroomRepository, never()).save(any());
    }

    // --- 3. TESZT: Csatlakozás meghívó kóddal ---
    @Test
    void joinClassroom_ValidInviteCode_ShouldCreatePendingMembership() {
        // Arrange
        when(userRepository.findByEmail(student.getEmail())).thenReturn(Optional.of(student));
        when(classroomRepository.findByInviteCode("TESTCODE")).thenReturn(Optional.of(classroom));
        // A diák még nem tagja
        when(classroomMemberRepository.existsByClassroom_ClassroomIdAndUser_UserId(classroom.getClassroomId(), student.getUserId())).thenReturn(false);

        // Act
        classroomService.joinClassroom("TESTCODE", student.getEmail());

        // Assert
        ArgumentCaptor<ClassroomMember> memberCaptor = ArgumentCaptor.forClass(ClassroomMember.class);
        verify(classroomMemberRepository, times(1)).save(memberCaptor.capture());

        ClassroomMember savedMember = memberCaptor.getValue();
        assertEquals(student, savedMember.getUser());
        assertEquals(classroom, savedMember.getClassroom());
        assertEquals(MembershipStatus.PENDING, savedMember.getStatus(), "A csatlakozásnak PENDING státuszban kell várakoznia a tanárra!");
    }

    // --- 4. TESZT: Tanári moderáció (Jelentkezés elfogadása) ---
    @Test
    void moderateJoinRequest_Accept_ShouldChangeStatusToAccepted() {
        // Arrange
        ClassroomMember pendingMember = new ClassroomMember();
        pendingMember.setClassroomMemberId(UUID.randomUUID());
        pendingMember.setUser(student);
        pendingMember.setClassroom(classroom);
        pendingMember.setStatus(MembershipStatus.PENDING);

        when(classroomRepository.findById(classroom.getClassroomId())).thenReturn(Optional.of(classroom));
        when(classroomMemberRepository.findByClassroom_ClassroomIdAndUser_UserId(classroom.getClassroomId(), student.getUserId())).thenReturn(Optional.of(pendingMember));

        // Act (isApproved = true)
        classroomService.moderateJoinRequest(classroom.getClassroomId(), student.getUserId(), teacher.getEmail(), true);

        // Assert
        assertEquals(MembershipStatus.ACCEPTED, pendingMember.getStatus(), "A státusznak ACCEPTED-re kellett változnia");
        verify(classroomMemberRepository, times(1)).save(pendingMember);
        verify(classroomMemberRepository, never()).delete(any()); // Nem töröltük ki
    }
}