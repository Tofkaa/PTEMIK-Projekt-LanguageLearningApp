package com.languageapp.backend;

import com.languageapp.backend.scheduler.ChallengeScheduler;
import com.languageapp.backend.scheduler.AssignmentExpiryScheduler;
import com.languageapp.backend.service.SseService;
import com.languageapp.backend.service.ImageStorageService;
import com.languageapp.backend.service.EmailService;
import com.languageapp.backend.config.DataSeeder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.languageapp.backend.dto.request.AssignmentAccommodationRequest;
import com.languageapp.backend.entity.*;
import com.languageapp.backend.enums.MembershipStatus;
import com.languageapp.backend.repository.ClassroomAssignmentRepository;
import com.languageapp.backend.service.AssignmentService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "UTC_TEST_DATABASE_URL", matches = "jdbc:postgresql://[^/]+/language_learning_utc_check_[a-zA-Z0-9_]+")
@SpringBootTest(properties = {"spring.datasource.url=${UTC_TEST_DATABASE_URL}", "spring.jpa.hibernate.ddl-auto=validate", "spring.jpa.show-sql=false"})
@Transactional
class AssignmentTimingPersistenceTest {
    // Legacy-schema validation still uses an isolated DB; never start external effects.
    @MockitoBean DataSeeder seeder;
    @MockitoBean EmailService mail;
    @MockitoBean ImageStorageService images;
    @MockitoBean SseService sse;
    @MockitoBean AssignmentExpiryScheduler expiry;
    @MockitoBean ChallengeScheduler challenges;

    @Autowired EntityManager em;
    @Autowired AssignmentService service;
    @Autowired ClassroomAssignmentRepository assignments;

    @Test void overrideQueryAndSessionSnapshotsPersistIndependently() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        // PostgreSQL timestamp precision is microseconds; do not compare a rounded DB value
        // with the original nanosecond request when reusing the same deadline.
        Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        User teacher = new User(); teacher.setName("Timing teacher"); teacher.setEmail("t" + suffix + "@test.invalid"); em.persist(teacher);
        User student = new User(); student.setName("Timing student"); student.setEmail("s" + suffix + "@test.invalid"); em.persist(student);
        Course course = new Course(); course.setLanguageCode("tm" + suffix); course.setLanguageName("Timing"); em.persist(course);
        Classroom room = new Classroom(); room.setName("Timing check"); room.setTeacher(teacher); room.setCourse(course); room.setInviteCode(suffix); em.persist(room);
        ClassroomMember member = new ClassroomMember(); member.setClassroom(room); member.setUser(student); member.setStatus(MembershipStatus.ACCEPTED); em.persist(member);
        ClassroomAssignment a = new ClassroomAssignment(); a.setClassroom(room); a.setTitle("Timing check"); a.setTimeLimitMinutes(10);
        a.setAvailableFrom(now.minusSeconds(3600)); a.setAvailableUntil(now.minusSeconds(1)); em.persist(a); em.flush();
        UUID assignmentId = a.getAssignmentId();
        assertTrue(assignments.findActiveAssignmentsForStudent(student.getUserId(), now).isEmpty());

        var request = new AssignmentAccommodationRequest(); request.setExtraMinutes(5); request.setAvailableUntilOverride(now.plusSeconds(7200));
        service.updateAccommodation(assignmentId, student.getUserId(), request, teacher.getEmail());
        em.flush(); em.clear();
        assertEquals(1, assignments.findActiveAssignmentsForStudent(student.getUserId(), now).size());
        var started = service.startAssignment(assignmentId, student.getEmail());
        assertEquals(15, started.getTimeLimitMinutes());
        request.setExtraMinutes(20);
        service.updateAccommodation(assignmentId, student.getUserId(), request, teacher.getEmail());
        em.flush(); em.clear();
        assertEquals(30, service.getSessionTiming(started.getSessionId(), student.getEmail()).timeLimitMinutes());

        var finished = em.find(AssignmentSession.class, started.getSessionId()); finished.setFinishedAt(Instant.now()); em.flush();
        request.setExtraMinutes(40);
        service.updateAccommodation(assignmentId, student.getUserId(), request, teacher.getEmail());
        em.flush(); em.clear();
        assertEquals(30, service.getSessionTiming(started.getSessionId(), student.getEmail()).timeLimitMinutes());
        assertEquals(50, service.getAssignmentDetails(assignmentId, student.getEmail()).getTimeLimitMinutes());
    }
}
