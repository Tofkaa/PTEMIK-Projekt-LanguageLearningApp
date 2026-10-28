package com.languageapp.backend;

import com.languageapp.backend.config.DataSeeder;
import com.languageapp.backend.dto.request.*;
import com.languageapp.backend.entity.*;
import com.languageapp.backend.enums.MembershipStatus;
import com.languageapp.backend.exception.*;
import com.languageapp.backend.repository.*;
import com.languageapp.backend.scheduler.*;
import com.languageapp.backend.service.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Actual row/advisory locks, isolated database; no providers, emails, user accounts or browser operations. */
@EnabledIfEnvironmentVariable(named="ASSIGNMENT_CONCURRENCY_TEST_DATABASE_URL",matches="jdbc:postgresql://[^/]+/languageapp_exam_lock_check_[a-zA-Z0-9_]+")
@SpringBootTest(properties={"spring.datasource.url=${ASSIGNMENT_CONCURRENCY_TEST_DATABASE_URL}","spring.jpa.hibernate.ddl-auto=create","spring.jpa.show-sql=false",
        "jwt.secret=U2VjdXJpdHkgZm91bmRhdGlvbnMgdGVzdCBvbmx5IHNpZ25pbmcga2V5IG5vdCBmb3IgZGVwbG95bWVudCE="})
class AssignmentConcurrencyPersistenceTest {
    static final Instant NOW=Instant.parse("2026-10-28T10:00:00Z");
    @Autowired AssignmentService service;
    @Autowired AssignmentSessionRepository sessions;
    @Autowired ClassroomAssignmentRepository assignments;
    @Autowired EntityManager em;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager manager;
    @Autowired javax.sql.DataSource dataSource;
    @MockitoBean Clock clock;
    @MockitoBean DataSeeder seeder;
    @MockitoBean EmailService mail;
    @MockitoBean ImageStorageService images;
    @MockitoBean StreakService streak;
    @MockitoBean SseService sse;
    @MockitoBean AssignmentExpiryScheduler expiry;
    @MockitoBean ChallengeScheduler challenges;
    TransactionTemplate tx;
    record Student(UUID id,String email,UUID session) { }
    record Fixture(UUID assignment,String teacher,List<Student> students,List<UUID> queue) { }
    @BeforeEach void setup(){tx=new TransactionTemplate(manager);when(clock.instant()).thenReturn(NOW);}

    Fixture fixture(int students,boolean start) {
        Fixture created=tx.execute(s->{
            String key=UUID.randomUUID().toString().substring(0,8);
            User teacher=new User();teacher.setName("Lock teacher");teacher.setEmail("t"+key+"@example.test");em.persist(teacher);
            Course course=new Course();course.setLanguageCode(key);course.setLanguageName("Lock test");em.persist(course);
            LessonTopic topic=new LessonTopic();topic.setCourse(course);topic.setName("Test");em.persist(topic);
            Lesson lesson=new Lesson();lesson.setTopic(topic);lesson.setTitle("Test");em.persist(lesson);
            Exercise ex=new Exercise();ex.setLesson(lesson);ex.setType("TRANSLATION");ex.setDifficulty("EASY");
            ex.setContent(Map.of("question","Alma"));ex.setCorrectAnswer(Map.of("answer","apple"));em.persist(ex);
            Classroom room=new Classroom();room.setName("Lock class");room.setTeacher(teacher);room.setCourse(course);room.setInviteCode(key);em.persist(room);
            List<Student> members=new ArrayList<>();
            for(int i=0;i<students;i++){
                User u=new User();u.setName("Lock student "+i);u.setEmail("s"+i+key+"@example.test");em.persist(u);
                ClassroomMember m=new ClassroomMember();m.setClassroom(room);m.setUser(u);m.setStatus(MembershipStatus.ACCEPTED);em.persist(m);
                members.add(new Student(u.getUserId(),u.getEmail(),null));
            }
            ClassroomAssignment a=new ClassroomAssignment();a.setClassroom(room);a.setTitle("Parallel work");a.setExercises(new ArrayList<>(List.of(ex)));
            a.setTimeLimitMinutes(10);a.setAvailableUntil(NOW.plusSeconds(3600));a.setAllowLateSubmission(false);a.setRandomized(false);a.setMaxAttempts(1);em.persist(a);em.flush();
            return new Fixture(a.getAssignmentId(),teacher.getEmail(),members,List.of(ex.getExerciseId()));
        });
        if(!start)return created;
        return new Fixture(created.assignment,created.teacher,created.students.stream().map(u->{
            var result=service.startAssignment(created.assignment,u.email);return new Student(u.id,u.email,result.getSessionId());
        }).toList(),created.queue);
    }
    AssignmentDraftRequest draft(Fixture f,long revision){var r=new AssignmentDraftRequest();r.setRevision(revision);r.setQueue(f.queue);r.setCurrentAnswer("apple");return r;}
    AssignmentSubmitRequest submit(Fixture f,long revision,String answer){var a=new ExerciseSubmission();a.setExerciseId(f.queue.getFirst());a.setAnswer(answer);var r=new AssignmentSubmitRequest();r.setRevision(revision);r.setAnswers(List.of(a));return r;}
    AssignmentAccommodationRequest extend(){var r=new AssignmentAccommodationRequest();r.setExtraMinutes(5);return r;}
    void await(CountDownLatch latch){try{if(!latch.await(20,TimeUnit.SECONDS))throw new AssertionError("Fixture checkpoint timeout");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new AssertionError(e);}}
    void awaitBlocked(){long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);while(System.nanoTime()<until){
        Integer blocked=jdbc.queryForObject("select count(*) from pg_stat_activity where datname=current_database() and pid<>pg_backend_pid() and cardinality(pg_blocking_pids(pid))>0",Integer.class);
        if(blocked!=null && blocked>0)return;try{Thread.sleep(10);}catch(InterruptedException e){Thread.currentThread().interrupt();throw new AssertionError(e);}
    }fail("Expected a real PostgreSQL lock waiter");}

    @Test void eightIndependentDraftTransactionsReachTheCheckpointBeforeAnyOfThemCommits() throws Exception {
        Fixture f=fixture(8,true);
        // Test lock independence, not cold Docker/TCP connection creation latency.
        // Hold all eight connections at once so each worker has a ready pool slot.
        var warmConnections=new ArrayList<java.sql.Connection>();
        try { for(int i=0;i<8;i++)warmConnections.add(dataSource.getConnection()); }
        finally { for(var connection:warmConnections)connection.close(); }
        var ready=new CountDownLatch(8);var release=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(8);var jobs=new ArrayList<Future<Long>>();
        try{
            for(Student u:f.students)jobs.add(pool.submit(()->tx.execute(s->{long rev=service.saveDraft(u.session,draft(f,0),u.email).revision();ready.countDown();await(release);return rev;})));
            assertTrue(ready.await(10,TimeUnit.SECONDS),"Every independent student must progress while the other transactions stay open");
            release.countDown();for(var job:jobs)assertEquals(1L,job.get(10,TimeUnit.SECONDS));
        }finally{release.countDown();pool.shutdownNow();}
        for(Student u:f.students)assertEquals(1,service.recoverSession(u.session,u.email).revision());
    }

    @Test void parallelFirstStartsForTheSameStudentReturnOneSession() throws Exception {
        Fixture f=fixture(1,false);Student u=f.students.getFirst();var ready=new CountDownLatch(1);var release=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try{
            var first=pool.submit(()->tx.execute(s->{UUID id=service.startAssignment(f.assignment,u.email).getSessionId();ready.countDown();await(release);return id;}));
            assertTrue(ready.await(5,TimeUnit.SECONDS));
            var second=pool.submit(()->service.startAssignment(f.assignment,u.email).getSessionId());awaitBlocked();assertFalse(second.isDone());
            release.countDown();assertEquals(first.get(10,TimeUnit.SECONDS),second.get(10,TimeUnit.SECONDS));
            assertEquals(1,jdbc.queryForObject("select count(*) from assignment_sessions where assignment_id=? and user_id=?",Integer.class,f.assignment,u.id));
        }finally{release.countDown();pool.shutdownNow();}
    }

    @Test void differentStudentsCanStartWhileTheFirstStudentsNewSessionIsUncommitted() throws Exception {
        Fixture f=fixture(2,false);Student a=f.students.get(0),b=f.students.get(1);var ready=new CountDownLatch(1);var release=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try{
            var first=pool.submit(()->tx.execute(s->{UUID id=service.startAssignment(f.assignment,a.email).getSessionId();ready.countDown();await(release);return id;}));
            assertTrue(ready.await(5,TimeUnit.SECONDS));
            UUID second=pool.submit(()->service.startAssignment(f.assignment,b.email).getSessionId()).get(3,TimeUnit.SECONDS);assertNotNull(second);
            release.countDown();assertNotEquals(first.get(10,TimeUnit.SECONDS),second);
        }finally{release.countDown();pool.shutdownNow();}
    }

    @Test void sameSessionDraftWaitsAndConflictsWhileAnotherStudentCanSave() throws Exception {
        Fixture f=fixture(2,true);Student a=f.students.get(0),b=f.students.get(1);var ready=new CountDownLatch(1);var release=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(3);
        try{
            var first=pool.submit(()->tx.execute(s->{service.saveDraft(a.session,draft(f,0),a.email);ready.countDown();await(release);return null;}));
            assertTrue(ready.await(5,TimeUnit.SECONDS));
            var stale=pool.submit(()->service.saveDraft(a.session,draft(f,0),a.email));awaitBlocked();assertFalse(stale.isDone());
            assertEquals(1L,pool.submit(()->service.saveDraft(b.session,draft(f,0),b.email).revision()).get(3,TimeUnit.SECONDS));
            release.countDown();first.get(10,TimeUnit.SECONDS);var error=assertThrows(ExecutionException.class,()->stale.get(10,TimeUnit.SECONDS));assertInstanceOf(ConflictException.class,error.getCause());
        }finally{release.countDown();pool.shutdownNow();}
    }

    @Test void teacherExtensionWaitsForDraftAndPreservesBothTheAnswerAndLongerTiming() throws Exception {
        Fixture f=fixture(1,true);Student u=f.students.getFirst();var ready=new CountDownLatch(1);var release=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try{
            var draft=pool.submit(()->tx.execute(s->{service.saveDraft(u.session,draft(f,0),u.email);ready.countDown();await(release);return null;}));
            assertTrue(ready.await(5,TimeUnit.SECONDS));var teacher=pool.submit(()->service.updateAccommodation(f.assignment,u.id,extend(),f.teacher));
            awaitBlocked();assertFalse(teacher.isDone());release.countDown();draft.get(10,TimeUnit.SECONDS);teacher.get(10,TimeUnit.SECONDS);
        }finally{release.countDown();pool.shutdownNow();}
        var result=service.recoverSession(u.session,u.email);assertEquals(15,result.sessionData().getTimeLimitMinutes());assertEquals("apple",result.draft().getCurrentAnswer());assertEquals(1,result.revision());
    }

    @Test void studentWaitsForAnUncommittedExtensionAndReadsItsNewSnapshot() throws Exception {
        Fixture f=fixture(1,true);Student u=f.students.getFirst();var ready=new CountDownLatch(1);var release=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try{
            var teacher=pool.submit(()->tx.execute(s->{service.updateAccommodation(f.assignment,u.id,extend(),f.teacher);ready.countDown();await(release);return null;}));
            assertTrue(ready.await(5,TimeUnit.SECONDS));var student=pool.submit(()->service.saveDraft(u.session,draft(f,0),u.email));awaitBlocked();assertFalse(student.isDone());
            release.countDown();teacher.get(10,TimeUnit.SECONDS);student.get(10,TimeUnit.SECONDS);
        }finally{release.countDown();pool.shutdownNow();}
        assertEquals(15,service.getSessionTiming(u.session,u.email).timeLimitMinutes());
    }

    @Test void startingWhileAnExistingAttemptFinishesRespectsTheCommittedAttemptLimit() throws Exception {
        Fixture f=fixture(1,true);Student u=f.students.getFirst();var ready=new CountDownLatch(1);var release=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try{
            var finished=pool.submit(()->tx.execute(s->{service.submitAssignment(u.session,submit(f,0,"apple"),u.email);ready.countDown();await(release);return null;}));
            assertTrue(ready.await(5,TimeUnit.SECONDS));var start=pool.submit(()->service.startAssignment(f.assignment,u.email));awaitBlocked();assertFalse(start.isDone());
            release.countDown();finished.get(10,TimeUnit.SECONDS);var error=assertThrows(ExecutionException.class,()->start.get(10,TimeUnit.SECONDS));assertInstanceOf(BadRequestException.class,error.getCause());
            assertEquals(1,jdbc.queryForObject("select count(*) from assignment_sessions where assignment_id=?",Integer.class,f.assignment));
        }finally{release.countDown();pool.shutdownNow();}
    }

    @Test void lateSubmitAndExpiryUseTheSavedAnswersAndFinalizeOnlyOnce() throws Exception {
        Fixture f=fixture(1,true);Student u=f.students.getFirst();service.saveDraft(u.session,draft(f,0),u.email);when(clock.instant()).thenReturn(NOW.plusSeconds(601));
        var pool=Executors.newFixedThreadPool(2);var go=new CountDownLatch(1);
        try{
            var submit=pool.submit(()->{await(go);service.submitAssignment(u.session,submit(f,1,"wrong"),u.email);});
            var expiry=pool.submit(()->{await(go);service.closeExpiredSession(u.session);});go.countDown();submit.get(10,TimeUnit.SECONDS);expiry.get(10,TimeUnit.SECONDS);
        }finally{go.countDown();pool.shutdownNow();}
        var result=sessions.findById(u.session).orElseThrow();assertEquals(NOW.plusSeconds(600),result.getFinishedAt());assertEquals(100,result.getFinalScore());verify(streak,times(1)).updateActivity(any(User.class));
    }

    @Test void independentFinalSubmissionsDoNotExcludeEachOther() throws Exception {
        Fixture f=fixture(2,true);var ready=new CountDownLatch(2);var release=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);var jobs=new ArrayList<Future<?>>();
        try{
            for(Student u:f.students)jobs.add(pool.submit(()->tx.execute(s->{service.submitAssignment(u.session,submit(f,0,"apple"),u.email);ready.countDown();await(release);return null;})));
            assertTrue(ready.await(5,TimeUnit.SECONDS));release.countDown();for(var job:jobs)job.get(10,TimeUnit.SECONDS);
        }finally{release.countDown();pool.shutdownNow();}
        for(Student u:f.students)assertEquals(100,sessions.findById(u.session).orElseThrow().getFinalScore());
    }

    @Test void rollbackOfANewStartReleasesThePerStudentGuardAndLeavesNoAttempt() {
        Fixture f=fixture(1,false);Student u=f.students.getFirst();
        UUID rolledBack=tx.execute(s->{UUID id=service.startAssignment(f.assignment,u.email).getSessionId();s.setRollbackOnly();return id;});
        assertFalse(sessions.existsById(rolledBack));UUID fresh=service.startAssignment(f.assignment,u.email).getSessionId();assertNotEquals(rolledBack,fresh);
        assertEquals(1,jdbc.queryForObject("select count(*) from assignment_sessions where assignment_id=?",Integer.class,f.assignment));
    }

    @Test void deletionWaitsForAnActiveStudentBeforeRemovingItsSession() throws Exception {
        Fixture f=fixture(1,true);Student u=f.students.getFirst();var ready=new CountDownLatch(1);var release=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try{
            var student=pool.submit(()->tx.execute(s->{service.saveDraft(u.session,draft(f,0),u.email);ready.countDown();await(release);return null;}));
            assertTrue(ready.await(5,TimeUnit.SECONDS));var deletion=pool.submit(()->service.deleteAssignment(f.assignment,f.teacher));awaitBlocked();assertFalse(deletion.isDone());
            release.countDown();student.get(10,TimeUnit.SECONDS);deletion.get(10,TimeUnit.SECONDS);
        }finally{release.countDown();pool.shutdownNow();}
        assertFalse(assignments.existsById(f.assignment));assertFalse(sessions.existsById(u.session));
    }
}
