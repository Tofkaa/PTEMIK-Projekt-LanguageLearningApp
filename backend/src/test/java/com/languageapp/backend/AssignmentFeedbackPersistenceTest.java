package com.languageapp.backend;

import com.languageapp.backend.config.DataSeeder;
import com.languageapp.backend.dto.request.*;
import com.languageapp.backend.entity.*;
import com.languageapp.backend.enums.*;
import com.languageapp.backend.exception.*;
import com.languageapp.backend.scheduler.*;
import com.languageapp.backend.security.*;
import com.languageapp.backend.service.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Only a new opt-in disposable PostgreSQL database may receive this schema. */
@EnabledIfEnvironmentVariable(named="ASSIGNMENT_FEEDBACK_TEST_DATABASE_URL",matches="jdbc:postgresql://[^/]+/languageapp_feedback_check_[a-zA-Z0-9_]+")
@SpringBootTest(properties={"spring.datasource.url=${ASSIGNMENT_FEEDBACK_TEST_DATABASE_URL}","spring.jpa.hibernate.ddl-auto=create","spring.jpa.show-sql=false",
        "jwt.secret=U2VjdXJpdHkgZm91bmRhdGlvbnMgdGVzdCBvbmx5IHNpZ25pbmcga2V5IG5vdCBmb3IgZGVwbG95bWVudCE="})
class AssignmentFeedbackPersistenceTest {
    @Autowired AssignmentService service;
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager manager;
    @Autowired WebApplicationContext context;
    @Autowired JwtService jwt;
    @Autowired RefreshTokenService refresh;
    @MockitoBean DataSeeder seeder;
    @MockitoBean EmailService mail;
    @MockitoBean ImageStorageService images;
    @MockitoBean StreakService streak;
    @MockitoBean SseService sse;
    @MockitoBean AssignmentExpiryScheduler expiry;
    @MockitoBean ChallengeScheduler challenges;
    TransactionTemplate tx;
    MockMvc mvc;
    record Fixture(UUID assignment,UUID room,UUID session,UUID exercise,UUID unallocated,User student,User teacher,User outsider) {}
    @BeforeEach void setup(){tx=new TransactionTemplate(manager);mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();}
    User user(String key,Role role,Course course){var u=new User();u.setName(key);u.setEmail(key+"@example.test");u.setRole(role);u.setVerified(true);u.setActiveCourse(course);em.persist(u);return u;}
    Fixture fixture(boolean test,boolean feedback){
        var f=tx.execute(status->{
            String id=UUID.randomUUID().toString().substring(0,8);
            var c=new Course();c.setLanguageCode(id);c.setLanguageName("Test");em.persist(c);
            var t=user("t"+id,Role.TEACHER,c);var u=user("u"+id,Role.STUDENT,c);var o=user("o"+id,Role.STUDENT,c);
            var topic=new LessonTopic();topic.setName("Test");topic.setCourse(c);em.persist(topic);
            var l=new Lesson();l.setTitle("Test");l.setTopic(topic);em.persist(l);
            var e=new Exercise();e.setLesson(l);e.setType("TRANSLATION");e.setDifficulty("EASY");e.setContent(Map.of("question","Alma"));e.setCorrectAnswer(Map.of("answer","apple"));em.persist(e);
            var extra=new Exercise();extra.setLesson(l);extra.setType("TRANSLATION");extra.setDifficulty("EASY");extra.setContent(Map.of("question","Körte"));extra.setCorrectAnswer(Map.of("answer","pear"));em.persist(extra);
            var room=new Classroom();room.setName("Test");room.setTeacher(t);room.setCourse(c);room.setInviteCode(id);em.persist(room);
            var member=new ClassroomMember();member.setUser(u);member.setClassroom(room);member.setStatus(MembershipStatus.ACCEPTED);em.persist(member);
            var a=new ClassroomAssignment();a.setTitle("Feedback");a.setClassroom(room);a.setTest(test);a.setHasFeedback(feedback);a.setRandomized(false);a.setAllowLateSubmission(false);a.setTimeLimitMinutes(10);a.setExercises(List.of(e,extra));a.setGenerationMode("RANDOM_SUBSET");a.setQuestionCount(1);em.persist(a);em.flush();
            return new Fixture(a.getAssignmentId(),room.getClassroomId(),null,e.getExerciseId(),extra.getExerciseId(),u,t,o);
        });
        var started=service.startAssignment(f.assignment(),f.student().getEmail());var chosen=started.getExercises().getFirst().getExerciseId();
        return new Fixture(f.assignment(),f.room(),started.getSessionId(),chosen,chosen.equals(f.exercise())?f.unallocated():f.exercise(),f.student(),f.teacher(),f.outsider());
    }
    String auth(User u){var session=refresh.createSession(u.getUserId());return "Bearer "+jwt.generateSessionToken(org.springframework.security.core.userdetails.User.withUsername(u.getEmail()).password("test-only").authorities("ROLE_"+u.getRole().name()).build(),session.id());}
    String check(Fixture f,UUID exercise){return "/api/assignments/sessions/"+f.session()+"/exercises/"+exercise+"/check";}
    String expected(Fixture f){return tx.execute(s->String.valueOf(em.find(Exercise.class,f.exercise()).getCorrectAnswer().get("answer")));}
    void finish(Fixture f){var answer=new ExerciseSubmission();answer.setExerciseId(f.exercise());answer.setAnswer(expected(f));var r=new AssignmentSubmitRequest();r.setRevision(0L);r.setAnswers(List.of(answer));service.submitAssignment(f.session(),r,f.student().getEmail());}
    @ParameterizedTest @CsvSource({"true,false,false,false","true,false,true,true","true,true,false,true","false,false,false,true","false,true,false,true"})
    void studentResultVisibilityFollowsPublicationMatrix(boolean test,boolean feedback,boolean graded,boolean visible) throws Exception {
        var f=fixture(test,feedback);finish(f);
        tx.execute(s->{var row=em.find(AssignmentSession.class,f.session());row.setTeacherScore(88);row.setTeacherComment("Private review");row.setGraded(graded);return null;});
        var response=service.getMySessionsForAssignment(f.assignment(),f.student().getEmail()).getFirst();
        assertEquals(visible,response.isResultsAvailable());
        assertEquals(visible?100:null,response.getFinalScore());assertEquals(visible?88:null,response.getTeacherScore());
        assertEquals(visible?"Private review":null,response.getTeacherComment());assertEquals(visible?1:0,response.getAnswers().size());
        var transport=mvc.perform(get("/api/assignments/"+f.assignment()+"/my-sessions").header("Authorization",auth(f.student())))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
                .andExpect(jsonPath("$[0].resultsAvailable").value(visible)).andReturn().getResponse().getContentAsString();
        if(!visible){assertFalse(transport.contains("serverCorrectAnswer"));assertFalse(transport.contains("Private review"));}
    }
    @Test void teacherPublicationMakesThePreviouslyHiddenResultAvailable(){
        var f=fixture(true,false);finish(f);assertNull(service.getMySessionsForAssignment(f.assignment(),f.student().getEmail()).getFirst().getFinalScore());
        var grade=new TeacherGradeRequest();grade.setRevision(0L);grade.setTeacherScore(75);grade.setTeacherComment("Published");service.gradeSession(f.session(),grade,f.teacher().getEmail());
        var published=service.getMySessionsForAssignment(f.assignment(),f.student().getEmail()).getFirst();assertTrue(published.isResultsAvailable());assertEquals(75,published.getTeacherScore());assertFalse(published.getAnswers().isEmpty());
    }
    @Test void ownerReadsUnpublishedAnswersButAnotherTeacherAndStudentCannot(){
        var f=fixture(true,false);finish(f);assertTrue(service.getSessionsForAssignment(f.assignment(),f.teacher().getEmail()).getFirst().isResultsAvailable());
        assertThrows(BadRequestException.class,()->service.getSessionsForAssignment(f.assignment(),f.outsider().getEmail()));
        assertThrows(BadRequestException.class,()->service.getMySessionsForAssignment(f.assignment(),f.outsider().getEmail()));
    }
    @Test void feedbackCheckRequiresOwnLiveAllocatedExercise() throws Exception {
        var f=fixture(true,true);String authorization=auth(f.student());
        mvc.perform(post(check(f,f.exercise())).header("Authorization",authorization).contentType("application/json").content("{\"answer\":\""+expected(f)+"\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.correct").value(true)).andExpect(header().string("Cache-Control","no-store"));
        assertThrows(ForbiddenException.class,()->service.checkSessionExercise(f.session(),f.unallocated(),f.student().getEmail(),"apple"));
        assertThrows(BadRequestException.class,()->service.checkSessionExercise(f.session(),f.exercise(),f.outsider().getEmail(),"apple"));
        finish(f);assertThrows(ConflictException.class,()->service.checkSessionExercise(f.session(),f.exercise(),f.student().getEmail(),"apple"));
    }
    @Test void noFeedbackCannotBeBypassedWithALegacyRetryFlagOrWithTheGlobalOracle() throws Exception {
        var f=fixture(true,false);tx.execute(s->{em.find(ClassroomAssignment.class,f.assignment()).setAllowRetries(true);return null;});
        assertFalse(service.recoverSession(f.session(),f.student().getEmail()).sessionData().isAllowRetries());
        assertFalse(service.getAssignmentDetails(f.assignment(),f.student().getEmail()).isAllowRetries());
        mvc.perform(post(check(f,f.exercise())).header("Authorization",auth(f.student())).contentType("application/json").content("{\"answer\":\"apple\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.correct").doesNotExist());
        mvc.perform(post("/api/exercises/"+f.exercise()+"/check").header("Authorization",auth(f.student())).contentType("application/json").content("{\"answer\":\"apple\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.correct").doesNotExist());
    }
    @Test void expiredNoLateAttemptCannotBeUsedAsAnAnswerOracle(){
        var f=fixture(true,true);tx.execute(s->{em.find(AssignmentSession.class,f.session()).setStartedAt(Instant.now().minusSeconds(3600));return null;});
        assertThrows(ConflictException.class,()->service.checkSessionExercise(f.session(),f.exercise(),f.student().getEmail(),"apple"));
    }
    @Test void hiddenScoreDoesNotLeakThroughStudentOrClassAverages(){
        var f=fixture(true,false);finish(f);var stats=service.getStudentClassroomStats(f.room(),f.student().getEmail());
        assertEquals(0.0,stats.get("myAverage"));assertEquals(0.0,stats.get("classAverage"));assertEquals(1,stats.get("completedCount"));
        assertEquals(100.0,service.getClassroomStatistics(f.room(),f.teacher().getEmail()).getClassAverage());
        var grade=new TeacherGradeRequest();grade.setRevision(0L);grade.setTeacherScore(75);service.gradeSession(f.session(),grade,f.teacher().getEmail());
        assertEquals(75.0,service.getStudentClassroomStats(f.room(),f.student().getEmail()).get("myAverage"));
    }
    @Test void creationRejectsSecondChanceWithoutFeedback(){
        var f=fixture(true,false);var r=new AssignmentCreateRequest();r.setTitle("Invalid");r.setTest(true);r.setAllowRetries(true);r.setExerciseIds(List.of(f.exercise()));
        assertThrows(BadRequestException.class,()->service.createAssignment(f.room(),r,f.teacher().getEmail()));
        r.setHasFeedback(true);assertDoesNotThrow(()->service.createAssignment(f.room(),r,f.teacher().getEmail()));
    }
    @Test void newSessionBoundCheckRetainsPayloadBounds() throws Exception {
        var f=fixture(true,true);mvc.perform(post(check(f,f.exercise())).header("Authorization",auth(f.student())).contentType("application/json").content("{\"answer\":\""+"x".repeat(65536)+"\"}"))
                .andExpect(status().isPayloadTooLarge());
    }
}
