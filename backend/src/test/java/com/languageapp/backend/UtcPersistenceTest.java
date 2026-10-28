package com.languageapp.backend;

import com.languageapp.backend.scheduler.ChallengeScheduler;
import com.languageapp.backend.scheduler.AssignmentExpiryScheduler;
import com.languageapp.backend.service.SseService;
import com.languageapp.backend.service.ImageStorageService;
import com.languageapp.backend.service.EmailService;
import com.languageapp.backend.config.DataSeeder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.languageapp.backend.entity.*;
import com.languageapp.backend.enums.MembershipStatus;
import com.languageapp.backend.repository.ClassroomAssignmentRepository;
import com.languageapp.backend.repository.StudentVocabularyRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/** Runs only against an explicitly named, separate verification database. */
@EnabledIfEnvironmentVariable(named = "UTC_TEST_DATABASE_URL",
        matches = "jdbc:postgresql://[^/]+/language_learning_utc_check_[a-zA-Z0-9_]+")
@SpringBootTest(properties = {
        "spring.datasource.url=${UTC_TEST_DATABASE_URL}",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.show-sql=false"
})
@Transactional
class UtcPersistenceTest {
    // Legacy-schema validation still uses an isolated DB; never start external effects.
    @MockitoBean DataSeeder seeder;
    @MockitoBean EmailService mail;
    @MockitoBean ImageStorageService images;
    @MockitoBean SseService sse;
    @MockitoBean AssignmentExpiryScheduler expiry;
    @MockitoBean ChallengeScheduler challenges;

    @Autowired private EntityManager entityManager;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ClassroomAssignmentRepository assignments;
    @Autowired private StudentVocabularyRepository vocabulary;

    @ParameterizedTest
    @ValueSource(strings = {"UTC", "Asia/Tokyo", "America/New_York"})
    void timestampsAndBoundaryQueries_ShouldBeIndependentOfDatabaseSessionZone(String zone) {
        jdbc.execute("SET LOCAL TIME ZONE '" + zone + "'");
        Instant now = Instant.parse("2026-10-25T01:30:00.123456Z");
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        User user = new User();
        user.setName("UTC test");
        user.setEmail(suffix + "@test.invalid");
        user.setLastLogin(now);
        user.setLastActivityDate(LocalDate.of(2026, 10, 25));
        entityManager.persist(user);
        Course course = new Course();
        course.setLanguageCode("tz" + suffix);
        course.setLanguageName("UTC test");
        entityManager.persist(course);
        Classroom classroom = new Classroom();
        classroom.setName("UTC test");
        classroom.setTeacher(user);
        classroom.setCourse(course);
        classroom.setInviteCode(suffix);
        entityManager.persist(classroom);
        ClassroomMember member = new ClassroomMember();
        member.setClassroom(classroom);
        member.setUser(user);
        member.setStatus(MembershipStatus.ACCEPTED);
        entityManager.persist(member);
        ClassroomAssignment assignment = new ClassroomAssignment();
        assignment.setClassroom(classroom);
        assignment.setTitle("UTC boundaries");
        assignment.setAvailableFrom(now);
        assignment.setAvailableUntil(now.plusSeconds(1));
        entityManager.persist(assignment);
        StudentVocabulary word = new StudentVocabulary();
        word.setUser(user);
        word.setCourse(course);
        word.setWord("clock");
        word.setTranslation("óra");
        word.setNextPracticeAt(now);
        entityManager.persist(word);
        entityManager.flush();
        UUID userId = user.getUserId();
        UUID courseId = course.getCourseId();
        UUID assignmentId = assignment.getAssignmentId();
        entityManager.clear();

        assertEquals(now, entityManager.find(User.class, userId).getLastLogin());
        assertEquals(LocalDate.of(2026, 10, 25), entityManager.find(User.class, userId).getLastActivityDate());
        assertEquals(now, entityManager.find(ClassroomAssignment.class, assignmentId).getAvailableFrom());
        assertNotNull(entityManager.find(ClassroomAssignment.class, assignmentId).getCreatedAt());
        assertEquals(1, assignments.findActiveAssignmentsForStudent(userId, now).size());
        assertTrue(assignments.findActiveAssignmentsForStudent(userId, now.minusNanos(1000)).isEmpty());
        assertTrue(assignments.findActiveAssignmentsForStudent(userId, now.plusSeconds(1)).isEmpty());
        assertEquals(1, vocabulary.countByUser_UserIdAndNextPracticeAtLessThanEqual(userId, now));
        assertEquals(0, vocabulary.countByUser_UserIdAndNextPracticeAtLessThanEqual(userId, now.minusNanos(1000)));
        assertEquals(1, vocabulary.findAllByUser_UserIdAndCourse_CourseIdAndNextPracticeAtLessThanEqualOrderByNextPracticeAtAsc(
                userId, courseId, now).size());
    }
}
