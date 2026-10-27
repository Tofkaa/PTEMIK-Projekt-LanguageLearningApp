package com.languageapp.backend;

import com.languageapp.backend.config.DataSeeder;
import com.languageapp.backend.entity.*;
import com.languageapp.backend.enums.*;
import com.languageapp.backend.repository.*;
import com.languageapp.backend.scheduler.*;
import com.languageapp.backend.service.*;
import com.languageapp.backend.security.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@EnabledIfEnvironmentVariable(named="REPORTING_TEST_DATABASE_URL",matches="jdbc:postgresql://[^/]+/languageapp_reporting_check_[a-zA-Z0-9_]+")
@SpringBootTest(properties={"spring.datasource.url=${REPORTING_TEST_DATABASE_URL}","spring.jpa.hibernate.ddl-auto=create",
        "spring.jpa.show-sql=false","spring.jpa.properties.hibernate.generate_statistics=true",
        "logging.level.org.hibernate.stat=OFF","logging.level.org.hibernate.engine.internal.StatisticalLoggingSessionEventListener=OFF",
        "jwt.secret=U2VjdXJpdHkgZm91bmRhdGlvbnMgdGVzdCBvbmx5IHNpZ25pbmcga2V5IG5vdCBmb3IgZGVwbG95bWVudCE="})
class ClassroomReportingPersistenceTest {
    @Autowired EntityManager em;
    @Autowired EntityManagerFactory factory;
    @Autowired PlatformTransactionManager manager;
    @Autowired AssignmentService service;
    @Autowired AssignmentSessionRepository sessions;
    @Autowired WebApplicationContext context;
    @Autowired JwtService jwt;
    @Autowired RefreshTokenService refresh;
    @Autowired tools.jackson.databind.ObjectMapper json;
    @MockitoBean DataSeeder seeder;
    @MockitoBean EmailService mail;
    @MockitoBean SseService sse;
    @MockitoBean ImageStorageService images;
    @MockitoBean AssignmentExpiryScheduler expiry;
    @MockitoBean ChallengeScheduler scheduler;
    TransactionTemplate tx;
    Statistics stats;
    static final Instant START=Instant.parse("2026-10-26T12:00:00Z");
    record Fixture(User teacher,User first,User second,User third,User pending,User outsider,
                   Course course,Classroom room,Classroom foreign,ClassroomAssignment firstTask,ClassroomAssignment secondTask,Exercise exercise,Exercise foreignExercise) { }
    @BeforeEach void setup() {tx=new TransactionTemplate(manager);stats=factory.unwrap(SessionFactory.class).getStatistics();}
    User user(Course c,Role role) {
        String key=UUID.randomUUID().toString();User u=new User();u.setEmail(key+"@example.test");u.setName(key);u.setRole(role);
        u.setVerified(true);u.setFriendCode(key.substring(0,8));u.setUserTag("1234");u.setActiveCourse(c);em.persist(u);return u;
    }
    Classroom room(User teacher,Course course) {
        Classroom r=new Classroom();r.setTeacher(teacher);r.setCourse(course);r.setName("Reporting");r.setInviteCode(UUID.randomUUID().toString().substring(0,8));em.persist(r);return r;
    }
    void member(Classroom room,User user,MembershipStatus status) {
        ClassroomMember m=new ClassroomMember();m.setClassroom(room);m.setUser(user);m.setStatus(status);em.persist(m);
    }
    ClassroomAssignment task(Classroom room,String title) {
        ClassroomAssignment a=new ClassroomAssignment();a.setClassroom(room);a.setTitle(title);em.persist(a);return a;
    }
    Fixture fixture() {
        return tx.execute(status -> {
            Course c=new Course();c.setLanguageCode(UUID.randomUUID().toString().substring(0,8));c.setLanguageName("Reporting");em.persist(c);
            User teacher=user(c,Role.TEACHER),first=user(c,Role.STUDENT),second=user(c,Role.STUDENT),third=user(c,Role.STUDENT),pending=user(c,Role.STUDENT),outsider=user(c,Role.TEACHER);
            Classroom room=room(teacher,c),foreign=room(outsider,c);
            member(room,first,MembershipStatus.ACCEPTED);member(room,second,MembershipStatus.ACCEPTED);member(room,third,MembershipStatus.ACCEPTED);member(room,pending,MembershipStatus.PENDING);
            LessonTopic topic=new LessonTopic();topic.setCourse(c);topic.setName("Reporting");em.persist(topic);
            Lesson lesson=new Lesson();lesson.setTopic(topic);lesson.setTitle("Source");em.persist(lesson);
            Exercise e=new Exercise();e.setLesson(lesson);e.setDifficulty("EASY");e.setType("TRANSLATION");e.setCorrectAnswer(Map.of("answer","Current assigned answer"));em.persist(e);
            Exercise other=new Exercise();other.setLesson(lesson);other.setDifficulty("EASY");other.setType("TRANSLATION");other.setCorrectAnswer(Map.of("answer","FOREIGN_SECRET"));em.persist(other);
            ClassroomAssignment a=task(room,"First"),b=task(room,"Second");a.getExercises().add(e);
            task(foreign,"Foreign").getExercises().add(other);
            return new Fixture(teacher,first,second,third,pending,outsider,c,room,foreign,a,b,e,other);
        });
    }
    AssignmentSession attempt(User user,ClassroomAssignment task,Integer score,Integer teacherScore,int seconds,boolean finished,String raw) {
        AssignmentSession s=new AssignmentSession();s.setUser(em.getReference(User.class,user.getUserId()));s.setAssignment(em.getReference(ClassroomAssignment.class,task.getAssignmentId()));
        s.setStartedAt(START.plusSeconds(seconds));if(finished)s.setFinishedAt(START.plusSeconds(seconds+30));
        s.setFinalScore(score);s.setTeacherScore(teacherScore);s.setRawAnswers(raw);em.persist(s);return s;
    }
    String wrong(UUID exercise,String question,String answer) {
        return "{\"question\":\""+question+"\",\"correct\":false,\"exercise\":{\"exerciseId\":\""+exercise+"\"},\"serverCorrectAnswer\":"+(answer==null?"null":"\""+answer+"\"")+"}";
    }
    @Test void aggregatesKeepAllFinishedAttemptsTeacherOverridesDistinctTasksAndZeroRules() {
        Fixture f=fixture();
        tx.executeWithoutResult(status -> {
            attempt(f.first(),f.firstTask(),20,null,0,true,null);attempt(f.first(),f.firstTask(),100,0,60,true,null);
            attempt(f.first(),f.secondTask(),50,80,120,true,null);attempt(f.first(),f.secondTask(),100,null,180,false,null);
            attempt(f.second(),f.firstTask(),0,null,0,true,null);attempt(f.pending(),f.firstTask(),100,null,0,true,null);
            ClassroomAssignment hidden=task(em.getReference(Classroom.class,f.room().getClassroomId()),"Hidden");hidden.setActive(false);attempt(f.first(),hidden,100,null,300,true,null);
            ClassroomAssignment alien=task(em.getReference(Classroom.class,f.foreign().getClassroomId()),"Alien");attempt(f.first(),alien,100,null,300,true,null);
        });
        var result=service.getClassroomStatistics(f.room().getClassroomId(),f.teacher().getEmail());
        assertEquals(2,result.getTotalAssignments());assertEquals(3,result.getStudentProgress().size());assertEquals(33.3,result.getClassAverage());
        var first=result.getStudentProgress().stream().filter(s -> s.getStudentEmail().equals(f.first().getEmail())).findFirst().orElseThrow();
        assertEquals(2,first.getCompletedCount());assertEquals(33.3,first.getAverageScore());assertEquals(START.plusSeconds(150).toString(),first.getLastActivity());
        var zero=result.getStudentProgress().stream().filter(s -> s.getStudentEmail().equals(f.second().getEmail())).findFirst().orElseThrow();
        assertEquals(1,zero.getCompletedCount());assertEquals(0.0,zero.getAverageScore());
        var analytics=service.getClassroomAnalytics(f.room().getClassroomId(),f.teacher().getEmail());
        assertEquals(Set.of(f.firstTask().getAssignmentId().toString(),f.secondTask().getAssignmentId().toString()),
                new HashSet<>(analytics.getAssignmentHeaders().stream().map(a -> a.getAssignmentId()).toList()));
        assertEquals(3,sessions.findBestClassroomAnalyticsSessions(f.room().getClassroomId()).size());
        var mine=service.getStudentClassroomStats(f.room().getClassroomId(),f.first().getEmail());
        assertEquals(Map.of("completedCount",2,"totalAssignments",2,"myAverage",33.3,"classAverage",33.3),mine);
    }
    @Test void analyticsUseOnlyBestFinishedAttemptAndPreserveZeroVersusMissingScore() {
        Fixture f=fixture();UUID id=f.exercise().getExerciseId();
        tx.executeWithoutResult(status -> {
            attempt(f.first(),f.firstTask(),20,null,0,true,"["+wrong(id,"Historical question","Historical answer")+"]");
            attempt(f.first(),f.firstTask(),100,0,60,true,"["+wrong(id,"Lower effective score","Not selected")+"]");
            attempt(f.first(),f.firstTask(),100,null,120,false,"broken ongoing JSON");
            attempt(f.second(),f.firstTask(),0,null,0,true,null);attempt(f.pending(),f.firstTask(),100,null,0,true,"broken pending JSON");
        });
        var result=service.getClassroomAnalytics(f.room().getClassroomId(),f.teacher().getEmail());assertEquals(2,result.getAssignmentHeaders().size());assertEquals(3,result.getHeatmapData().size());
        var first=result.getHeatmapData().stream().filter(s -> s.getStudentEmail().equals(f.first().getEmail())).findFirst().orElseThrow();
        assertEquals(20,first.getScores().get(f.firstTask().getAssignmentId().toString()));assertNull(first.getScores().get(f.secondTask().getAssignmentId().toString()));
        var second=result.getHeatmapData().stream().filter(s -> s.getStudentEmail().equals(f.second().getEmail())).findFirst().orElseThrow();assertEquals(0,second.getScores().get(f.firstTask().getAssignmentId().toString()));
        assertEquals(1,result.getTopMistakes().size());assertEquals("Historical answer",result.getTopMistakes().getFirst().getServerCorrectAnswer());
    }
    @Test void fallbackAnswersRequireBothClassroomAndAssignmentMembership() {
        Fixture f=fixture();UUID[] bOnly=new UUID[1];
        tx.executeWithoutResult(status -> {
            Exercise b=new Exercise();b.setLesson(em.getReference(Exercise.class,f.exercise().getExerciseId()).getLesson());b.setDifficulty("EASY");b.setType("TRANSLATION");b.setCorrectAnswer(Map.of("translation","OTHER_ASSIGNMENT_SECRET"));em.persist(b);bOnly[0]=b.getExerciseId();
            em.find(ClassroomAssignment.class,f.secondTask().getAssignmentId()).getExercises().add(b);
            attempt(f.first(),f.firstTask(),75,null,0,true,"["+wrong(f.exercise().getExerciseId(),"Assigned",null)+","+wrong(bOnly[0],"Other assignment",null)+","+wrong(f.foreignExercise().getExerciseId(),"Foreign classroom",null)+"]");
        });
        var result=service.getClassroomAnalytics(f.room().getClassroomId(),f.teacher().getEmail());assertEquals(3,result.getTopMistakes().size());
        assertEquals("Current assigned answer",result.getTopMistakes().stream().filter(m -> m.getQuestion().equals("Assigned")).findFirst().orElseThrow().getServerCorrectAnswer());
        assertTrue(result.getTopMistakes().stream().filter(m -> !m.getQuestion().equals("Assigned")).allMatch(m -> m.getServerCorrectAnswer()==null));
    }
    @Test void equalBestScoresChooseAStableEarliestAttempt() {
        Fixture f=fixture();UUID[] expected=new UUID[1];
        tx.executeWithoutResult(status -> {
            expected[0]=attempt(f.first(),f.firstTask(),75,null,0,true,null).getSessionId();attempt(f.first(),f.firstTask(),75,null,100,true,null);
        });
        for(int i=0;i<2;i++)assertEquals(expected[0],sessions.findBestClassroomAnalyticsSessions(f.room().getClassroomId()).getFirst().getSessionId());
    }
    @Test void malformedOrMissingScoresDoNotBreakTheRemainingAnalytics() {
        Fixture f=fixture();
        tx.executeWithoutResult(status -> {attempt(f.first(),f.firstTask(),75,null,0,true,"malformed JSON");attempt(f.second(),f.firstTask(),null,null,0,true,"null");});
        var result=service.getClassroomAnalytics(f.room().getClassroomId(),f.teacher().getEmail());assertTrue(result.getTopMistakes().isEmpty());
        assertEquals(75.0,service.getClassroomStatistics(f.room().getClassroomId(),f.teacher().getEmail()).getClassAverage());
    }
    long measureStatistics(Fixture f,int studentCount,int taskCount) {
        stats.clear();var result=service.getClassroomStatistics(f.room().getClassroomId(),f.teacher().getEmail());
        assertEquals(studentCount,result.getStudentProgress().size());assertEquals(taskCount,result.getTotalAssignments());
        assertEquals(0,stats.getEntityStatistics(AssignmentSession.class.getName()).getLoadCount());assertEquals(0,stats.getEntityStatistics(ClassroomAssignment.class.getName()).getLoadCount());
        assertEquals(0,stats.getEntityStatistics(ClassroomMember.class.getName()).getLoadCount());return stats.getPrepareStatementCount();
    }
    long measureAnalytics(Fixture f,int studentCount,int taskCount) {
        stats.clear();var result=service.getClassroomAnalytics(f.room().getClassroomId(),f.teacher().getEmail());
        assertEquals(studentCount,result.getHeatmapData().size());assertEquals(taskCount,result.getAssignmentHeaders().size());
        assertTrue(result.getHeatmapData().stream().allMatch(r -> r.getScores().size()==taskCount));
        for(Class<?> type:List.of(AssignmentSession.class,ClassroomAssignment.class,ClassroomMember.class,Exercise.class))assertEquals(0,stats.getEntityStatistics(type.getName()).getLoadCount());
        return stats.getPrepareStatementCount();
    }
    @Test void reportQueriesStayBoundedFromTenByTenToHundredByHundred() {
        Fixture f=fixture();
        tx.executeWithoutResult(status -> {
            Classroom room=em.find(Classroom.class,f.room().getClassroomId());Course course=em.find(Course.class,f.course().getCourseId());
            for(int i=3;i<10;i++)member(room,user(course,Role.STUDENT),MembershipStatus.ACCEPTED);
            for(int i=2;i<10;i++)task(room,"Task "+i);
            attempt(f.first(),f.firstTask(),75,null,0,true,"["+wrong(f.exercise().getExerciseId(),"Fallback",null)+"]");
            var losing=attempt(f.first(),f.firstTask(),25,null,0,true,"LOSING_PAYLOAD"+"x".repeat(2_000_000));losing.setDraftState("x".repeat(100_000));
        });
        long smallStats=measureStatistics(f,10,10),smallAnalytics=measureAnalytics(f,10,10);
        tx.executeWithoutResult(status -> {
            Classroom room=em.find(Classroom.class,f.room().getClassroomId());Course course=em.find(Course.class,f.course().getCourseId());
            for(int i=10;i<100;i++)member(room,user(course,Role.STUDENT),MembershipStatus.ACCEPTED);
            for(int i=10;i<100;i++)task(room,"Task "+i);
        });
        long largeStats=measureStatistics(f,100,100),largeAnalytics=measureAnalytics(f,100,100);
        assertEquals(smallStats,largeStats);assertEquals(smallAnalytics,largeAnalytics);
        assertTrue(largeStats<=7,"Statistics query budget exceeded: "+largeStats);assertTrue(largeAnalytics<=8,"Analytics query budget exceeded: "+largeAnalytics);
        System.out.println("CLASSROOM_REPORTING_QUERY_COST matrix=10x10 stats="+smallStats+" analytics="+smallAnalytics+"; matrix=100x100 stats="+largeStats+" analytics="+largeAnalytics);
    }
    String token(User user) {
        var session=refresh.createSession(user.getUserId());
        var details=org.springframework.security.core.userdetails.User.withUsername(user.getEmail()).password("unused").roles(user.getRole().name()).build();
        return "Bearer "+jwt.generateSessionToken(details,session.id());
    }
    @Test void httpAuthorizationAndStudentResponseRemainScoped() throws Exception {
        Fixture f=fixture();var mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        String root="/api/assignments/classroom/"+f.room().getClassroomId();
        mvc.perform(get(root+"/statistics").header("Authorization",token(f.first()))).andExpect(status().isForbidden());
        mvc.perform(get(root+"/advanced-statistics").header("Authorization",token(f.outsider()))).andExpect(status().isBadRequest());
        mvc.perform(get(root+"/my-statistics").header("Authorization",token(f.pending()))).andExpect(status().isForbidden());
        String response=mvc.perform(get(root+"/my-statistics").header("Authorization",token(f.first()))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertFalse(response.contains(f.second().getEmail()));assertFalse(response.contains("studentProgress"));
        mvc.perform(get(root+"/statistics").header("Authorization",token(f.teacher()))).andExpect(status().isOk());
    }
    @Test void emptyClassroomReturnsEmptyReports() {
        Fixture f=fixture();UUID id=tx.execute(status -> room(em.find(User.class,f.teacher().getUserId()),em.find(Course.class,f.course().getCourseId())).getClassroomId());
        var result=service.getClassroomStatistics(id,f.teacher().getEmail());assertEquals(0,result.getTotalAssignments());assertTrue(result.getStudentProgress().isEmpty());assertEquals(0.0,result.getClassAverage());
        var analytics=service.getClassroomAnalytics(id,f.teacher().getEmail());assertTrue(analytics.getAssignmentHeaders().isEmpty());assertTrue(analytics.getHeatmapData().isEmpty());assertTrue(analytics.getTopMistakes().isEmpty());
    }
}
