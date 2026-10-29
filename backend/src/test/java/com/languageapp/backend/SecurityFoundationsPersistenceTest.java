package com.languageapp.backend;

import com.languageapp.backend.config.DataSeeder;
import com.languageapp.backend.entity.*;
import com.languageapp.backend.enums.*;
import com.languageapp.backend.repository.*;
import com.languageapp.backend.scheduler.*;
import com.languageapp.backend.security.*;
import com.languageapp.backend.service.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockCookie;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real HTTP security + PostgreSQL. Schema creation is opt-in and restricted to a disposable database. */
@EnabledIfEnvironmentVariable(named="SECURITY_FOUNDATIONS_TEST_DATABASE_URL", matches="jdbc:postgresql://[^/]+/languageapp_security_fix_check_[a-zA-Z0-9_]+")
@SpringBootTest(properties={"spring.datasource.url=${SECURITY_FOUNDATIONS_TEST_DATABASE_URL}",
        "spring.jpa.hibernate.ddl-auto=create", "spring.jpa.show-sql=false",
        "jwt.secret=U2VjdXJpdHkgZm91bmRhdGlvbnMgdGVzdCBvbmx5IHNpZ25pbmcga2V5IG5vdCBmb3IgZGVwbG95bWVudCE="})
class SecurityFoundationsPersistenceTest {
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager manager;
    @Autowired WebApplicationContext context;
    @Autowired UserRepository users;
    @Autowired ClassroomMemberRepository members;
    @Autowired RefreshTokenRepository sessions;
    @Autowired RefreshTokenService refresh;
    @Autowired JwtService jwt;
    @Autowired PasswordEncoder encoder;
    @Autowired Clock clock;
    @Autowired AssignmentService grading;
    @Autowired com.languageapp.backend.controller.NotificationController notifications;
    @Autowired AssignmentSessionRepository examSessions;
    @Autowired ClassroomAssignmentRepository assignments;
    @Autowired AssignmentAccommodationRepository accommodations;
    @Autowired LessonTopicRepository topics;
    @Autowired LessonRepository lessons;
    @Autowired ExerciseRepository exercises;
    @Autowired tools.jackson.databind.ObjectMapper transportJson;
    @MockitoBean DataSeeder seeder;
    @MockitoBean EmailService mail;
    @MockitoBean SseService sse;
    @MockitoBean ImageStorageService images;
    @MockitoBean AssignmentExpiryScheduler expiry;
    @MockitoBean ChallengeScheduler challenges;
    MockMvc mvc;
    TransactionTemplate tx;
    record Fixture(User teacher, User student, User outsider, UUID classroom, UUID assignment) { }
    @BeforeEach void setup() {
        tx=new TransactionTemplate(manager);
        mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }
    User user(String key, Role role, Course course) {
        var u=new User();u.setName(key);u.setEmail(key+"@example.test");u.setRole(role);u.setVerified(true);
        u.setActiveCourse(course);u.setPasswordHash(encoder.encode("Foundation-old-password"));em.persist(u);return u;
    }
    Fixture fixture() {
        return tx.execute(status -> {
            String key=UUID.randomUUID().toString().replace("-", "").substring(0,8);
            var course=new Course();course.setLanguageCode(key);course.setLanguageName("Test");em.persist(course);
            User teacher=user("owner"+key,Role.TEACHER,course), student=user("student"+key,Role.STUDENT,course), outsider=user("outsider"+key,Role.STUDENT,course);
            var room=new Classroom();room.setName("Private classroom");room.setTeacher(teacher);room.setCourse(course);room.setInviteCode(key);em.persist(room);
            var member=new ClassroomMember();member.setClassroom(room);member.setUser(student);member.setStatus(MembershipStatus.ACCEPTED);em.persist(member);
            var task=new ClassroomAssignment();task.setClassroom(room);task.setTitle("Private task");em.persist(task);
            em.flush();return new Fixture(teacher,student,outsider,room.getClassroomId(),task.getAssignmentId());
        });
    }
    String auth(User u) {
        var session=refresh.createSession(u.getUserId());
        return "Bearer "+jwt.generateSessionToken(org.springframework.security.core.userdetails.User.withUsername(u.getEmail())
                .password("test-only").authorities("ROLE_"+u.getRole().name()).build(),session.id());
    }
    String classroomPath(Fixture f) { return "/api/assignments/classroom/"+f.classroom; }

    @ParameterizedTest @ValueSource(longs={-1,0,1})
    void notificationAssignmentDeadlineAndIndividualOverrideRemainExclusive(long seconds) {
        var f=fixture();Instant now=Instant.parse("2026-10-26T12:00:00Z");
        tx.executeWithoutResult(status->assignments.findById(f.assignment).orElseThrow().setAvailableUntil(now.plusSeconds(seconds)));
        assertEquals(seconds>0?1:0,assignments.findActiveNotificationReferences(f.student.getUserId(),now).size());
        tx.executeWithoutResult(status->{
            assignments.findById(f.assignment).orElseThrow().setAvailableUntil(now.minusSeconds(100));
            var override=new AssignmentAccommodation();override.setAssignment(em.getReference(ClassroomAssignment.class,f.assignment));
            override.setUser(em.getReference(User.class,f.student.getUserId()));override.setAvailableUntilOverride(now.plusSeconds(seconds));em.persist(override);
        });
        assertEquals(seconds>0?1:0,assignments.findActiveNotificationReferences(f.student.getUserId(),now).size());
    }
    @Test void notificationReferencesAreScopedToOwnerAndStudentAndKeepTheExistingWireFormat() throws Exception {
        var f=fixture();UUID pending=gradingSession(f,true),graded=gradingSession(f,true);var foreign=fixture();gradingSession(foreign,true);
        UUID scheduled=tx.execute(status->{
            var request=new ClassroomMember();request.setClassroom(em.getReference(Classroom.class,f.classroom));
            request.setUser(em.getReference(User.class,f.outsider.getUserId()));request.setStatus(MembershipStatus.PENDING);em.persist(request);
            examSessions.findById(graded).orElseThrow().setGraded(true);
            var work=new ClassroomAssignment();work.setClassroom(em.getReference(Classroom.class,f.classroom));work.setTitle("Scheduled");
            work.setAvailableFrom(clock.instant().plusSeconds(86400));work.setAvailableUntil(clock.instant().plusSeconds(172800));
            em.persist(work);em.flush();return work.getAssignmentId();
        });
        mvc.perform(get("/api/notifications/summary").header("Authorization",auth(f.teacher)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.teacherPendingJoinRequestIds.length()").value(1))
                .andExpect(jsonPath("$.teacherPendingJoinRequestIds[0]").value(f.classroom+":"+f.outsider.getUserId()))
                .andExpect(jsonPath("$.teacherUngradedSubmissionIds.length()").value(1))
                .andExpect(jsonPath("$.teacherUngradedSubmissionIds[0]").value(f.classroom+":"+f.assignment+":"+pending))
                .andExpect(jsonPath("$.studentGradedSessionIds").isEmpty());
        mvc.perform(get("/api/notifications/summary").header("Authorization",auth(f.student)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.teacherPendingJoinRequestIds").isEmpty())
                .andExpect(jsonPath("$.teacherUngradedSubmissionIds").isEmpty())
                .andExpect(jsonPath("$.studentGradedSessionIds.length()").value(1))
                .andExpect(jsonPath("$.studentGradedSessionIds[0]").value(f.classroom+":"+f.assignment+":"+graded))
                .andExpect(jsonPath("$.studentActiveAssignmentIds.length()").value(1))
                .andExpect(jsonPath("$.studentActiveAssignmentIds[0]").value(f.classroom+":"+scheduled));
        mvc.perform(get("/api/notifications/summary").header("Authorization",auth(f.outsider)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.studentActiveAssignmentIds").isEmpty());
    }
    @Test void notificationQueryCountAndEntityLoadsDoNotGrowWithOtherUsersLargeSessions() {
        var f=fixture();
        var stats=em.getEntityManagerFactory().unwrap(org.hibernate.SessionFactory.class).getStatistics();
        boolean wasEnabled=stats.isStatisticsEnabled();stats.setStatisticsEnabled(true);
        try {
            var auth=new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(f.student.getEmail(),"unused");
            stats.clear();var before=notifications.getSummary(auth).getBody();
            long queries=stats.getPrepareStatementCount(),loaded=stats.getEntityLoadCount();
            assertTrue(queries<=11,"Summary must use a fixed query budget, got "+queries);
            assertTrue(loaded<=1,"Only the current User may be materialized, got "+loaded);
            var unrelated=fixture();
            tx.executeWithoutResult(status->{
                for(int i=0;i<50;i++) {
                    var row=new AssignmentSession();row.setAssignment(em.getReference(ClassroomAssignment.class,unrelated.assignment));
                    row.setUser(em.getReference(User.class,unrelated.student.getUserId()));row.setStartedAt(clock.instant());
                    row.setFinishedAt(clock.instant());row.setRawAnswers("x".repeat(250000));em.persist(row);
                }
            });
            stats.clear();var after=notifications.getSummary(auth).getBody();
            assertEquals(queries,stats.getPrepareStatementCount());assertEquals(loaded,stats.getEntityLoadCount());
            assertEquals(before.getStudentActiveAssignmentIds(),after.getStudentActiveAssignmentIds());
            assertEquals(before.getStudentGradedSessionIds(),after.getStudentGradedSessionIds());
            assertEquals(before.getTeacherUngradedSubmissionIds(),after.getTeacherUngradedSubmissionIds());
            tx.executeWithoutResult(status->{
                for(int i=0;i<25;i++) {
                    var work=new ClassroomAssignment();work.setClassroom(em.getReference(Classroom.class,f.classroom));
                    work.setTitle("Own notification "+i);em.persist(work);
                    if(i%5==0) {
                        var override=new AssignmentAccommodation();override.setAssignment(work);
                        override.setUser(em.getReference(User.class,f.student.getUserId()));
                        override.setAvailableUntilOverride(clock.instant().plusSeconds(3600));em.persist(override);
                    }
                }
            });
            stats.clear();var withOwnWork=notifications.getSummary(auth).getBody();
            assertEquals(queries,stats.getPrepareStatementCount());assertEquals(loaded,stats.getEntityLoadCount());
            assertEquals(before.getStudentActiveAssignmentIds().size()+25,withOwnWork.getStudentActiveAssignmentIds().size());
            System.out.printf("Notification summary: %d prepared SQL queries and %d loaded entities; unchanged after unrelated 12.5 MB session payload and 25 own assignments.%n",queries,loaded);
        } finally {stats.setStatisticsEnabled(wasEnabled);stats.clear();}
    }

    static java.util.stream.Stream<Class<?>> indexedEntities() {
        return java.util.stream.Stream.of(StudentVocabulary.class,Result.class,AssignmentSession.class,Progress.class,
                UserAchievement.class,Friendship.class,Challenge.class,RefreshToken.class,
                Classroom.class,ClassroomMember.class,ClassroomAssignment.class);
    }
    @ParameterizedTest @org.junit.jupiter.params.provider.MethodSource("indexedEntities")
    void hotPathIndexesAreCreatedWithTheDeclaredColumnOrderAndWithoutChangingUniqueness(Class<?> entity) {
        var table=entity.getAnnotation(jakarta.persistence.Table.class);
        tx.executeWithoutResult(status->{
            for(var index:table.indexes()) {
                var definitions=em.createNativeQuery("select indexdef from pg_indexes where schemaname = current_schema() and tablename = :table and indexname = :name",String.class)
                        .setParameter("table",table.name()).setParameter("name",index.name()).getResultList();
                assertEquals(1,definitions.size(),index.name());
                String definition=definitions.getFirst().toString().toLowerCase(Locale.ROOT).replaceAll("\\s+","");
                assertTrue(definition.endsWith("("+index.columnList().toLowerCase(Locale.ROOT).replaceAll("\\s+","")+")"),index.name()+": "+definition);
                assertFalse(index.unique());assertFalse(definition.contains("uniqueindex"));
            }
        });
    }

    byte[] profilePng() throws Exception {
        var output=new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(3,2,java.awt.image.BufferedImage.TYPE_INT_RGB),"png",output);
        return output.toByteArray();
    }
    @Test void anonymousProfileUploadIsRejectedBeforeStorage() throws Exception {
        mvc.perform(multipart("/api/users/me/profile-picture").file(
                new org.springframework.mock.web.MockMultipartFile("file","avatar.png","image/png",profilePng())))
                .andExpect(status().isUnauthorized());
        org.mockito.Mockito.verifyNoInteractions(images);
    }
    @Test void profileUploadDecodesPixelsAndNeverSendsOriginalPayloadToStorage() throws Exception {
        var f=fixture();String url="https://res.cloudinary.com/test/image/upload/normalized.png";
        org.mockito.Mockito.when(images.uploadProfileImage(org.mockito.ArgumentMatchers.any(byte[].class))).thenAnswer(invocation->{
            assertFalse(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive(),
                    "External image upload must not hold a database transaction");
            tx.executeWithoutResult(status->{
                var latest=users.findById(f.student.getUserId()).orElseThrow();latest.setXp(1234);latest.setPreferredDifficulty(DifficultyLevel.HARD);
            });
            return url;
        });
        mvc.perform(multipart("/api/users/me/profile-picture").file(
                new org.springframework.mock.web.MockMultipartFile("file","fake.svg","application/octet-stream",profilePng()))
                .header("Authorization",auth(f.student)))
                .andExpect(status().isOk()).andExpect(content().string(url));
        var payload=org.mockito.ArgumentCaptor.forClass(byte[].class);
        org.mockito.Mockito.verify(images).uploadProfileImage(payload.capture());
        var image=javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(payload.getValue()));
        assertEquals(250,image.getWidth());assertEquals(250,image.getHeight());
        assertEquals(url,users.findById(f.student.getUserId()).orElseThrow().getProfilePictureUrl());
        var latest=users.findById(f.student.getUserId()).orElseThrow();
        assertEquals(1234,latest.getXp());assertEquals(DifficultyLevel.HARD,latest.getPreferredDifficulty());
    }
    @Test void accountDeactivatedDuringUploadCannotReceiveAStaleProfileUpdate() throws Exception {
        var f=fixture();String header=auth(f.student);
        org.mockito.Mockito.when(images.uploadProfileImage(org.mockito.ArgumentMatchers.any(byte[].class))).thenAnswer(invocation->{
            tx.executeWithoutResult(status->{
                var user=users.findById(f.student.getUserId()).orElseThrow();user.setActive(false);user.setProfilePictureUrl(null);
            });
            return "https://example.test/new.png";
        });
        mvc.perform(multipart("/api/users/me/profile-picture").file(
                new org.springframework.mock.web.MockMultipartFile("file","avatar.png","image/png",profilePng()))
                .header("Authorization",header)).andExpect(status().isNotFound());
        var user=users.findById(f.student.getUserId()).orElseThrow();assertFalse(user.isActive());assertNull(user.getProfilePictureUrl());
    }
    @Test void invalidProfileUploadReturnsStructured400AndLeavesExistingUrlIntact() throws Exception {
        var f=fixture();String old="https://example.test/old.png";
        tx.executeWithoutResult(status->users.findById(f.student.getUserId()).orElseThrow().setProfilePictureUrl(old));
        mvc.perform(multipart("/api/users/me/profile-picture").file(
                new org.springframework.mock.web.MockMultipartFile("file","avatar.png","image/png","<svg onload='alert(1)'/>".getBytes(StandardCharsets.UTF_8)))
                .header("Authorization",auth(f.student)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
        org.mockito.Mockito.verifyNoInteractions(images);
        assertEquals(old,users.findById(f.student.getUserId()).orElseThrow().getProfilePictureUrl());
    }
    @Test void oversizedProfileUploadReturnsStructured413BeforeStorage() throws Exception {
        var f=fixture();
        mvc.perform(multipart("/api/users/me/profile-picture").file(
                new org.springframework.mock.web.MockMultipartFile("file","avatar.png","image/png",new byte[ProfileImageSanitizer.MAX_BYTES+1]))
                .header("Authorization",auth(f.student)))
                .andExpect(status().is(413)).andExpect(jsonPath("$.status").value(413));
        org.mockito.Mockito.verifyNoInteractions(images);
    }

    Map<String,Object> importedExercise(String answer) {
        return Map.of("type","MULTIPLE_CHOICE","difficulty","EASY","content",
                Map.of("question","Kérdés","questionLang","hu","answerLang","en","options",List.of("Apple","Banana")),
                "correctAnswer",Map.of("answer",answer));
    }
    Map<String,Object> importedTopic(String course,String answer) {
        return Map.of("courseCode",course,"topicName","Import "+UUID.randomUUID(),"lessons",
                List.of(Map.of("title","Imported lesson","language","en","exercises",List.of(importedExercise(answer)))));
    }
    String admin(Fixture f) {
        tx.executeWithoutResult(status->users.findById(f.teacher.getUserId()).orElseThrow().setRole(Role.ADMIN));return auth(f.teacher);
    }
    @Test void curriculumImportIsAtomicForSchemaErrorsAndForALaterMissingCourse() throws Exception {
        var f=fixture();String header=admin(f);String course=tx.execute(status->users.findById(f.student.getUserId()).orElseThrow().getActiveCourse().getLanguageCode());
        long topicCount=topics.count(),lessonCount=lessons.count(),exerciseCount=exercises.count();
        mvc.perform(post("/api/admin/curriculum/import").header("Authorization",header).contentType(MediaType.APPLICATION_JSON)
                .content(transportJson.writeValueAsString(List.of(importedTopic(course,"Apple"),importedTopic(course,"Pear")))))
                .andExpect(status().isBadRequest());
        assertEquals(topicCount,topics.count());assertEquals(lessonCount,lessons.count());assertEquals(exerciseCount,exercises.count());
        mvc.perform(post("/api/admin/curriculum/import").header("Authorization",header).contentType(MediaType.APPLICATION_JSON)
                .content(transportJson.writeValueAsString(List.of(importedTopic(course,"Apple"),importedTopic("absent-xy","Apple")))))
                .andExpect(status().isNotFound());
        assertEquals(topicCount,topics.count());assertEquals(lessonCount,lessons.count());assertEquals(exerciseCount,exercises.count());
        mvc.perform(post("/api/admin/curriculum/import").header("Authorization",header).contentType(MediaType.APPLICATION_JSON)
                .content(transportJson.writeValueAsString(List.of(importedTopic(course,"Apple")))))
                .andExpect(status().isOk());
        assertEquals(topicCount+1,topics.count());assertEquals(lessonCount+1,lessons.count());assertEquals(exerciseCount+1,exercises.count());
    }
    @ParameterizedTest @ValueSource(strings={"lesson","exercise"})
    void partialImportRoutesRejectInvalidExerciseSchemasWithoutSaving(String operation) throws Exception {
        var f=fixture();String header=admin(f);
        Lesson lesson=tx.execute(status->{
            var topic=new LessonTopic();topic.setName("Import target");topic.setCourse(users.findById(f.student.getUserId()).orElseThrow().getActiveCourse());em.persist(topic);
            var row=new Lesson();row.setTitle("Import target");row.setTopic(topic);em.persist(row);em.flush();return row;
        });
        long lessonCount=lessons.count(),exerciseCount=exercises.count();
        String path=operation.equals("lesson")?"/api/admin/curriculum/topic/"+lesson.getTopic().getTopicId()+"/lesson/import"
                :"/api/admin/curriculum/lesson/"+lesson.getLessonId()+"/exercise/import";
        Object body=operation.equals("lesson")?List.of(Map.of("title","Invalid","exercises",List.of(importedExercise("Pear"))))
                :List.of(importedExercise("Pear"));
        mvc.perform(post(path).header("Authorization",header).contentType(MediaType.APPLICATION_JSON).content(transportJson.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
        assertEquals(lessonCount,lessons.count());assertEquals(exerciseCount,exercises.count());
    }
    @Test void corruptStoredSolutionReturnsConflictInsteadOfACorrectFeedback() throws Exception {
        var f=fixture();UUID exerciseId=tx.execute(status->{
            var topic=new LessonTopic();topic.setName("Legacy");topic.setCourse(users.findById(f.student.getUserId()).orElseThrow().getActiveCourse());em.persist(topic);
            var lesson=new Lesson();lesson.setTitle("Legacy");lesson.setTopic(topic);em.persist(lesson);
            var exercise=new Exercise();exercise.setLesson(lesson);exercise.setType("TRANSLATION");exercise.setDifficulty("EASY");
            exercise.setContent(Map.of("question","Question"));exercise.setCorrectAnswer(Map.of());em.persist(exercise);em.flush();return exercise.getExerciseId();
        });
        mvc.perform(post("/api/exercises/"+exerciseId+"/check").header("Authorization",admin(f)).contentType(MediaType.APPLICATION_JSON).content("{\"answer\":\"\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.correct").doesNotExist());
    }

    UUID gradingSession(Fixture f, boolean finished) {
        return tx.execute(status -> {
            var session=new AssignmentSession();session.setAssignment(em.getReference(ClassroomAssignment.class,f.assignment));
            session.setUser(em.getReference(User.class,f.student.getUserId()));session.setStartedAt(clock.instant().minusSeconds(600));
            if (finished) session.setFinishedAt(clock.instant().minusSeconds(10));
            session.setFinalScore(75);em.persist(session);em.flush();return session.getSessionId();
        });
    }
    String gradeBody(int score,long revision) { return "{\"teacherScore\":"+score+",\"teacherComment\":\"Feedback\",\"revision\":"+revision+"}"; }
    com.languageapp.backend.dto.request.TeacherGradeRequest gradeRequest(int score,long revision) {
        var request=new com.languageapp.backend.dto.request.TeacherGradeRequest();request.setTeacherScore(score);request.setRevision(revision);return request;
    }

    UUID notificationExercise(Fixture f) {
        return tx.execute(status->{
            var topic=new LessonTopic();topic.setName("Notification exercise");topic.setCourse(users.findById(f.student.getUserId()).orElseThrow().getActiveCourse());em.persist(topic);
            var lesson=new Lesson();lesson.setTitle("Notification");lesson.setTopic(topic);em.persist(lesson);
            var exercise=new Exercise();exercise.setLesson(lesson);exercise.setType("TRANSLATION");exercise.setDifficulty("EASY");
            exercise.setContent(Map.of("question","Alma"));exercise.setCorrectAnswer(Map.of("answer","Apple"));em.persist(exercise);em.flush();return exercise.getExerciseId();
        });
    }
    com.languageapp.backend.dto.request.AssignmentCreateRequest notificationAssignment(UUID exerciseId) {
        var request=new com.languageapp.backend.dto.request.AssignmentCreateRequest();
        request.setTitle("Notification after commit");request.setExerciseIds(List.of(exerciseId));return request;
    }
    @ParameterizedTest @ValueSource(strings={"creation","submission","accommodation"})
    void allAssignmentNotificationsWaitForCommitAndDisappearOnRollback(String operation) {
        var f=fixture();UUID sessionId=gradingSession(f,false),exerciseId=notificationExercise(f);
        long assignmentCount=assignments.count(),accommodationCount=accommodations.count();
        Runnable write=switch(operation) {
            case "creation" -> ()->grading.createAssignment(f.classroom,notificationAssignment(exerciseId),f.teacher.getEmail());
            case "submission" -> ()->{
                var request=new com.languageapp.backend.dto.request.AssignmentSubmitRequest();request.setAnswers(List.of());request.setRevision(0L);
                grading.submitAssignment(sessionId,request,f.student.getEmail());
            };
            default -> ()->{
                var request=new com.languageapp.backend.dto.request.AssignmentAccommodationRequest();request.setExtraMinutes(5);
                grading.updateAccommodation(f.assignment,f.student.getUserId(),request,f.teacher.getEmail());
            };
        };
        org.mockito.Mockito.clearInvocations(sse);
        tx.executeWithoutResult(status->{write.run();org.mockito.Mockito.verifyNoInteractions(sse);status.setRollbackOnly();});
        org.mockito.Mockito.verifyNoInteractions(sse);
        assertEquals(assignmentCount,assignments.count());assertEquals(accommodationCount,accommodations.count());
        assertNull(examSessions.findById(sessionId).orElseThrow().getFinishedAt());
        tx.executeWithoutResult(status->{write.run();org.mockito.Mockito.verifyNoInteractions(sse);});
        String recipient=operation.equals("submission")?f.teacher.getEmail():f.student.getEmail();
        org.mockito.Mockito.verify(sse).sendPing(recipient);
        if(operation.equals("creation")) assertEquals(assignmentCount+1,assignments.count());
        if(operation.equals("accommodation")) assertEquals(accommodationCount+1,accommodations.count());
        if(operation.equals("submission")) assertNotNull(examSessions.findById(sessionId).orElseThrow().getFinishedAt());
    }
    @Test void oneFailedAssignmentNotificationDoesNotUndoCommitOrSkipOtherRecipients() {
        var f=fixture();UUID exerciseId=notificationExercise(f);long before=assignments.count();
        tx.executeWithoutResult(status->{
            var member=new ClassroomMember();member.setClassroom(em.getReference(Classroom.class,f.classroom));
            member.setUser(em.getReference(User.class,f.outsider.getUserId()));member.setStatus(MembershipStatus.ACCEPTED);em.persist(member);
        });
        org.mockito.Mockito.doThrow(new IllegalStateException("synthetic send failure")).when(sse).sendPing(f.student.getEmail());
        assertDoesNotThrow(()->grading.createAssignment(f.classroom,notificationAssignment(exerciseId),f.teacher.getEmail()));
        assertEquals(before+1,assignments.count());
        org.mockito.Mockito.verify(sse).sendPing(f.student.getEmail());
        org.mockito.Mockito.verify(sse).sendPing(f.outsider.getEmail());
    }
    @Test void gradingInputBoundsAreEnforcedBeforeWriting() throws Exception {
        var f=fixture();UUID id=gradingSession(f,true);String header=auth(f.teacher);String path="/api/assignments/sessions/"+id+"/grade";
        for(String body:List.of(gradeBody(-1,0),gradeBody(101,0),"{\"teacherScore\":75}",
                "{\"teacherScore\":100.9,\"revision\":0}","{\"teacherScore\":4294967296,\"revision\":0}",
                "{\"teacherScore\":75,\"revision\":0,\"teacherComment\":\""+"x".repeat(4001)+"\"}"))
            mvc.perform(post(path).header("Authorization",header).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        var saved=examSessions.findById(id).orElseThrow();assertFalse(saved.isGraded());assertEquals(0,saved.getDraftRevision());assertNull(saved.getTeacherScore());
    }
    @Test void onlyTheOwnerCanGradeFinishedSessionsAndStaleVersionsAreRejected() throws Exception {
        var f=fixture();UUID open=gradingSession(f,false),closed=gradingSession(f,true);
        mvc.perform(post("/api/assignments/sessions/"+open+"/grade").header("Authorization",auth(f.teacher)).contentType(MediaType.APPLICATION_JSON).content(gradeBody(70,0)))
                .andExpect(status().isConflict());
        tx.executeWithoutResult(status->users.findById(f.outsider.getUserId()).orElseThrow().setRole(Role.TEACHER));
        mvc.perform(post("/api/assignments/sessions/"+closed+"/grade").header("Authorization",auth(f.outsider)).contentType(MediaType.APPLICATION_JSON).content(gradeBody(70,0)))
                .andExpect(status().isForbidden());
        String owner=auth(f.teacher);String path="/api/assignments/sessions/"+closed+"/grade";
        mvc.perform(post(path).header("Authorization",owner).contentType(MediaType.APPLICATION_JSON).content(gradeBody(70,0)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.gradingRevision").value(1));
        mvc.perform(post(path).header("Authorization",owner).contentType(MediaType.APPLICATION_JSON).content(gradeBody(90,0)))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/assignments/"+f.assignment+"/sessions").header("Authorization",owner))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].gradingRevision").value(1));
        assertEquals(70,examSessions.findById(closed).orElseThrow().getTeacherScore());
    }
    @Test void gradingRollbackDoesNotPublishANotificationAndDeliveryFailureDoesNotUndoCommit() {
        var f=fixture();UUID id=gradingSession(f,true);
        org.mockito.Mockito.clearInvocations(sse);
        assertThrows(IllegalStateException.class,()->tx.executeWithoutResult(status->{
            grading.gradeSession(id,gradeRequest(70,0),f.teacher.getEmail());
            org.mockito.Mockito.verifyNoInteractions(sse);throw new IllegalStateException("synthetic rollback");
        }));
        assertEquals(0,examSessions.findById(id).orElseThrow().getDraftRevision());org.mockito.Mockito.verifyNoInteractions(sse);
        org.mockito.Mockito.doThrow(new IllegalStateException("synthetic send failure")).when(sse).sendPing(f.student.getEmail());
        assertEquals(1,grading.gradeSession(id,gradeRequest(80,0),f.teacher.getEmail()));
        assertEquals(80,examSessions.findById(id).orElseThrow().getTeacherScore());
    }
    @Test void twoTeacherWindowsCannotSilentlyOverwriteTheSameVersion() throws Exception {
        var f=fixture();UUID id=gradingSession(f,true);var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var start=new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.Callable<Boolean> first=()->{start.await();try{grading.gradeSession(id,gradeRequest(70,0),f.teacher.getEmail());return true;}catch(com.languageapp.backend.exception.ConflictException stale){return false;}};
            java.util.concurrent.Callable<Boolean> second=()->{start.await();try{grading.gradeSession(id,gradeRequest(90,0),f.teacher.getEmail());return true;}catch(com.languageapp.backend.exception.ConflictException stale){return false;}};
            var a=pool.submit(first);var b=pool.submit(second);start.countDown();
            assertNotEquals(a.get(15,java.util.concurrent.TimeUnit.SECONDS),b.get(15,java.util.concurrent.TimeUnit.SECONDS));
            var saved=examSessions.findById(id).orElseThrow();assertEquals(1,saved.getDraftRevision());assertTrue(Set.of(70,90).contains(saved.getTeacherScore()));
        } finally {pool.shutdownNow();}
    }

    @ParameterizedTest @ValueSource(strings={"lookup","known-batch","practice/submit"})
    void vocabularyWordBoundsAreValidatedBeforeExternalOrRewardWork(String operation) throws Exception {
        var f=fixture();String word="x".repeat(101);
        String body=switch(operation) {
            case "lookup" -> "{\"word\":\""+word+"\",\"courseId\":\""+UUID.randomUUID()+"\"}";
            case "known-batch" -> "{\"words\":[\""+word+"\"],\"courseId\":\""+UUID.randomUUID()+"\"}";
            default -> "{\"results\":[{\"targetWord\":\""+word+"\",\"isCorrect\":true}]}";
        };
        mvc.perform(post("/api/vocabulary/"+operation).header("Authorization",auth(f.student)).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }
    @Test void vocabularyBatchListIsBoundedAndAnEmptyBatchKeepsItsContract() throws Exception {
        var f=fixture();String header=auth(f.student);
        mvc.perform(post("/api/vocabulary/known-batch").header("Authorization",header).contentType(MediaType.APPLICATION_JSON)
                .content("{\"words\":["+String.join(",",Collections.nCopies(1001,"\"apple\""))+"],\"courseId\":\""+UUID.randomUUID()+"\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/vocabulary/known-batch").header("Authorization",header).contentType(MediaType.APPLICATION_JSON).content("{\"words\":[],\"courseId\":\""+UUID.randomUUID()+"\"}"))
                .andExpect(status().isOk());
    }

    @ParameterizedTest @ValueSource(strings={"/api/exercises/{id}/check","/api/lessons/{id}/attempts/{id}/exercises/{id}/check"})
    void immediateAnswerBoundsAreValidatedAtBothControllers(String template) throws Exception {
        var f=fixture();String path=template.replace("{id}",UUID.randomUUID().toString());
        mvc.perform(post(path).header("Authorization",auth(f.student)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"answer\":\""+"x".repeat(10_001)+"\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
    }
    @ParameterizedTest @ValueSource(strings={"lesson","assignment","draft"})
    void nestedNonTextAnswersAreRejectedOnEverySubmissionPath(String kind) throws Exception {
        var f=fixture();String id=UUID.randomUUID().toString();
        String path=kind.equals("lesson")?"/api/lessons/"+id+"/submit":"/api/assignments/sessions/"+id+"/"+(kind.equals("draft")?"draft":"submit");
        var request=kind.equals("draft")?put(path):post(path);
        mvc.perform(request.header("Authorization",auth(f.student)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"revision\":0,\"answers\":[{\"exerciseId\":\""+id+"\",\"answer\":{\"nested\":[\"not text\"]}}]}"))
                .andExpect(status().isBadRequest());
    }
    @Test void oversizedBodiesAreRejectedByTheRealSecurityChainButAnonymousRequestsStillNeedAuthentication() throws Exception {
        var f=fixture();String path="/api/exercises/"+UUID.randomUUID()+"/check";
        String large="{\"answer\":\""+"x".repeat(65_536)+"\"}";
        mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(large)).andExpect(status().isUnauthorized());
        mvc.perform(post(path).header("Authorization",auth(f.student)).contentType(MediaType.APPLICATION_JSON).content(large))
                .andExpect(status().isPayloadTooLarge()).andExpect(jsonPath("$.status").value(413))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("összmérete túl nagy")));
    }
    @Test void aBoundaryLengthAnswerAndTwoTyposKeepTheirOriginalFeedback() throws Exception {
        var f=fixture();String expected="a".repeat(10_000);
        UUID id=tx.execute(status -> {
            var topic=new LessonTopic();topic.setName("Limits");topic.setCourse(em.getReference(User.class,f.student.getUserId()).getActiveCourse());em.persist(topic);
            var lesson=new Lesson();lesson.setTitle("Limits");lesson.setTopic(topic);em.persist(lesson);
            var exercise=new Exercise();exercise.setLesson(lesson);exercise.setType("TRANSLATION");exercise.setDifficulty("HARD");
            exercise.setContent(Map.of("question","Limits"));exercise.setCorrectAnswer(Map.of("answer",expected));em.persist(exercise);em.flush();return exercise.getExerciseId();
        });
        String path="/api/exercises/"+id+"/check";String header=admin(f);
        mvc.perform(post(path).header("Authorization",header).contentType(MediaType.APPLICATION_JSON).content("{\"answer\":\""+expected+"\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.correct").value(true));
        mvc.perform(post(path).header("Authorization",header).contentType(MediaType.APPLICATION_JSON).content("{\"answer\":\""+expected.substring(0,9998)+"bb\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.correct").value(false)).andExpect(jsonPath("$.almostCorrect").value(true));
    }
    @ParameterizedTest @ValueSource(strings={"lesson","assignment"})
    void oversizedCollectionsFailValidationBeforeAnyDomainMutation(String kind) throws Exception {
        var f=fixture();String id=UUID.randomUUID().toString();int count=kind.equals("lesson")?2001:1001;
        String item="{\"exerciseId\":\""+id+"\",\"answer\":\"\"}";
        String path=kind.equals("lesson")?"/api/lessons/"+id+"/submit":"/api/assignments/sessions/"+id+"/submit";
        mvc.perform(post(path).header("Authorization",auth(f.student)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"revision\":0,\"answers\":["+String.join(",",Collections.nCopies(count,item))+"]}"))
                .andExpect(status().isBadRequest());
    }
    @Test void anonymousStatisticsAreUnauthorized() throws Exception {
        var f=fixture();mvc.perform(get(classroomPath(f)+"/statistics")).andExpect(status().isUnauthorized());
    }
    @Test void ownerCanReadFullStatisticsWithoutAMembership() throws Exception {
        var f=fixture();mvc.perform(get(classroomPath(f)+"/statistics").header("Authorization",auth(f.teacher)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.studentProgress[0].studentEmail").value(f.student.getEmail()));
    }
    @ParameterizedTest @EnumSource(value=Role.class,names={"STUDENT","TEACHER","ADMIN"})
    void anOutsiderRoleDoesNotGrantAccessToAnotherClassroom(Role role) throws Exception {
        var f=fixture();tx.executeWithoutResult(status -> users.findById(f.outsider.getUserId()).orElseThrow().setRole(role));
        for(String suffix:List.of("","/statistics","/my-statistics")) {
            var response=mvc.perform(get(classroomPath(f)+suffix).header("Authorization",auth(f.outsider)))
                    .andExpect(status().isForbidden()).andReturn().getResponse();
            assertFalse(response.getContentAsString().contains(f.student.getEmail()));
        }
    }
    @Test void acceptedStudentCanReadAssignmentsAndOwnAggregatesButNotFullStatistics() throws Exception {
        var f=fixture();String header=auth(f.student);
        mvc.perform(get(classroomPath(f)).header("Authorization",header)).andExpect(status().isOk()).andExpect(jsonPath("$[0].title").value("Private task"));
        String own=mvc.perform(get(classroomPath(f)+"/my-statistics").header("Authorization",header)).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAssignments").value(1)).andExpect(jsonPath("$.completedCount").value(0)).andReturn().getResponse().getContentAsString();
        assertFalse(own.contains(f.teacher.getEmail()));assertFalse(own.contains("studentProgress"));
        mvc.perform(get(classroomPath(f)+"/statistics").header("Authorization",header)).andExpect(status().isForbidden());
    }
    @ParameterizedTest @EnumSource(value=MembershipStatus.class,names={"PENDING","REJECTED"})
    void aPendingOrRejectedMembershipCannotReadTheClassroom(MembershipStatus state) throws Exception {
        var f=fixture();tx.executeWithoutResult(status -> members.findByClassroom_ClassroomIdAndUser_UserId(f.classroom,f.student.getUserId()).orElseThrow().setStatus(state));
        for(String suffix:List.of("","/my-statistics","/statistics"))
            mvc.perform(get(classroomPath(f)+suffix).header("Authorization",auth(f.student))).andExpect(status().isForbidden());
    }
    @Test void aRemovedMembershipCannotReadTheClassroom() throws Exception {
        var f=fixture();tx.executeWithoutResult(status -> members.delete(members.findByClassroom_ClassroomIdAndUser_UserId(f.classroom,f.student.getUserId()).orElseThrow()));
        mvc.perform(get(classroomPath(f)).header("Authorization",auth(f.student))).andExpect(status().isForbidden());
    }
    @Test void anUnknownClassroomReturnsNotFound() throws Exception {
        var f=fixture();mvc.perform(get("/api/assignments/classroom/"+UUID.randomUUID()+"/statistics").header("Authorization",auth(f.teacher))).andExpect(status().isNotFound());
    }
    @Test void theRetiredPublicKeyCannotAuthenticateAnIndependentlySignedAdminToken() throws Exception {
        var f=fixture();tx.executeWithoutResult(status -> users.findById(f.outsider.getUserId()).orElseThrow().setRole(Role.ADMIN));
        // Historical TEST fixture shares the retired public key; the running test service uses an independent key.
        var testSettings=new Properties();
        try(var stream=getClass().getResourceAsStream("/application.properties")) { testSettings.load(stream); }
        String retired=testSettings.getProperty("jwt.secret");
        String forged=io.jsonwebtoken.Jwts.builder().subject(f.outsider.getEmail()).issuedAt(Date.from(clock.instant()))
                .expiration(Date.from(clock.instant().plusSeconds(60)))
                .signWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(io.jsonwebtoken.io.Decoders.BASE64.decode(retired))).compact();
        mvc.perform(get("/api/admin/users").header("Authorization","Bearer "+forged)).andExpect(status().isUnauthorized());
    }
    String seedSession(User user) {
        String raw=UUID.randomUUID()+"-"+UUID.randomUUID();
        tx.executeWithoutResult(status -> {
            try {
                var record=new RefreshToken();record.setUser(em.getReference(User.class,user.getUserId()));
                record.setExpiryDate(clock.instant().plusSeconds(3600));
                record.setTokenHash(Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8))));em.persist(record);
            } catch(java.security.NoSuchAlgorithmException e) {throw new IllegalStateException(e);}
        });return raw;
    }
    String seedReset(User user, long seconds) {
        String raw=UUID.randomUUID().toString();
        tx.executeWithoutResult(status -> {
            var token=new PasswordResetToken();token.setUser(em.getReference(User.class,user.getUserId()));token.setToken(raw);
            token.setExpiryDate(clock.instant().plusSeconds(seconds));em.persist(token);
        });return raw;
    }
    String resetBody(String token,String password) {return "{\"token\":\""+token+"\",\"newPassword\":\""+password+"\"}";}
    @Test void resettingThePasswordRevokesEveryRefreshSessionAndPermitsANewLogin() throws Exception {
        var f=fixture();String first=seedSession(f.student),second=seedSession(f.student),reset=seedReset(f.student,60);
        for(String raw:List.of(first,second)) mvc.perform(post("/api/auth/refresh").header("Origin","http://localhost:5173").cookie(new MockCookie("refreshToken",raw))).andExpect(status().isOk());
        mvc.perform(post("/api/auth/reset-password").header("Origin","http://localhost:5173").contentType(MediaType.APPLICATION_JSON).content(resetBody(reset,"Foundation-new-password"))).andExpect(status().isOk());
        assertTrue(encoder.matches("Foundation-new-password",users.findById(f.student.getUserId()).orElseThrow().getPasswordHash()));
        for(String raw:List.of(first,second)) {
            assertTrue(refresh.findByRawToken(raw).isEmpty());
            mvc.perform(post("/api/auth/refresh").header("Origin","http://localhost:5173").cookie(new MockCookie("refreshToken",raw))).andExpect(status().isBadRequest());
        }
        mvc.perform(post("/api/auth/login").header("Origin","http://localhost:5173").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\""+f.student.getEmail()+"\",\"password\":\"Foundation-new-password\"}"))
                .andExpect(status().isOk()).andExpect(cookie().httpOnly("refreshToken",true)).andExpect(cookie().secure("refreshToken",true));
    }
    @Test void anExpiredResetLeavesTheExistingPasswordAndSessionIntact() throws Exception {
        var f=fixture();String raw=seedSession(f.student),reset=seedReset(f.student,-1);
        mvc.perform(post("/api/auth/reset-password").header("Origin","http://localhost:5173").contentType(MediaType.APPLICATION_JSON).content(resetBody(reset,"Foundation-new-password"))).andExpect(status().isBadRequest());
        assertTrue(encoder.matches("Foundation-old-password",users.findById(f.student.getUserId()).orElseThrow().getPasswordHash()));
        assertTrue(refresh.findByRawToken(raw).isPresent());
    }
    @Test void aConsumedResetCannotRevokeANewerLoginSession() throws Exception {
        var f=fixture();String reset=seedReset(f.student,60);
        mvc.perform(post("/api/auth/reset-password").header("Origin","http://localhost:5173").contentType(MediaType.APPLICATION_JSON).content(resetBody(reset,"Foundation-new-password"))).andExpect(status().isOk());
        String newSession=refresh.createRefreshToken(f.student.getUserId());
        mvc.perform(post("/api/auth/reset-password").header("Origin","http://localhost:5173").contentType(MediaType.APPLICATION_JSON).content(resetBody(reset,"Foundation-other-password"))).andExpect(status().isBadRequest());
        assertTrue(refresh.findByRawToken(newSession).isPresent());
        assertTrue(encoder.matches("Foundation-new-password",users.findById(f.student.getUserId()).orElseThrow().getPasswordHash()));
    }
}
