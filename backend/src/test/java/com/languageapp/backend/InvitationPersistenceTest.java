package com.languageapp.backend;

import com.languageapp.backend.config.DataSeeder;
import com.languageapp.backend.entity.*;
import com.languageapp.backend.enums.*;
import com.languageapp.backend.exception.*;
import com.languageapp.backend.repository.*;
import com.languageapp.backend.scheduler.AssignmentExpiryScheduler;
import com.languageapp.backend.security.JwtService;
import com.languageapp.backend.service.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Never creates a schema in the application's database. Opt-in disposable database only. */
@EnabledIfEnvironmentVariable(named="INVITATION_TEST_DATABASE_URL", matches="jdbc:postgresql://[^/]+/languageapp_invitation_check_[a-zA-Z0-9_]+")
@SpringBootTest(properties={"spring.datasource.url=${INVITATION_TEST_DATABASE_URL}", "spring.jpa.hibernate.ddl-auto=create", "spring.jpa.show-sql=false"})
class InvitationPersistenceTest {
    @Autowired InvitationService invitations;
    @Autowired FriendshipService friendships;
    @Autowired ClassroomService classroomService;
    @Autowired UserRepository users;
    @Autowired ClassroomRepository classrooms;
    @Autowired ClassroomMemberRepository memberships;
    @Autowired FriendshipRepository connections;
    @Autowired InvitationLinkRepository links;
    @Autowired JwtService jwt;
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager manager;
    @Autowired WebApplicationContext context;
    @MockitoBean DataSeeder seeder;
    @MockitoBean EmailService email;
    @MockitoBean SseService sse;
    @MockitoBean AssignmentExpiryScheduler expiry;
    TransactionTemplate tx;
    MockMvc mvc;
    record Fixture(User teacher, User student, User other, UUID classroomId) { }
    @BeforeEach void setup() {
        tx = new TransactionTemplate(manager);
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }
    User user(String key, Role role, Course course) {
        User user = new User(); user.setEmail(key+"@example.test"); user.setName(key); user.setUserTag("1234");
        user.setFriendCode(UUID.randomUUID().toString().substring(0, 8).toUpperCase()); user.setRole(role); user.setActiveCourse(course); user.setVerified(true); em.persist(user); return user;
    }
    Fixture fixture() {
        return tx.execute(status -> {
            String key = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
            Course course = new Course(); course.setLanguageCode(key); course.setLanguageName("Test"); em.persist(course);
            User teacher = user("teacher"+key,Role.TEACHER,course), student = user("student"+key,Role.STUDENT,course), other = user("other"+key,Role.STUDENT,course);
            Classroom classroom = new Classroom(); classroom.setName("Invitation test"); classroom.setTeacher(teacher); classroom.setCourse(course);
            classroom.setInviteCode(key.toUpperCase()); em.persist(classroom); em.flush();
            return new Fixture(teacher,student,other,classroom.getClassroomId());
        });
    }
    String auth(User user) {
        return "Bearer "+jwt.generateToken(org.springframework.security.core.userdetails.User.withUsername(user.getEmail())
                .password("test-only").authorities("ROLE_"+user.getRole().name()).build());
    }
    String body(String kind,String token) { return "{\"kind\":\""+kind+"\",\"token\":\""+token+"\"}"; }
    <T> List<T> parallel(Callable<T> a, Callable<T> b) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2); CountDownLatch gate = new CountDownLatch(1);
        try {
            Future<T> x=pool.submit(() -> { gate.await(); return a.call(); });
            Future<T> y=pool.submit(() -> { gate.await(); return b.call(); }); gate.countDown();
            return List.of(x.get(20,TimeUnit.SECONDS),y.get(20,TimeUnit.SECONDS));
        } finally { pool.shutdownNow(); }
    }
    @Test void previewIsReadOnlyAndRepeatedClassroomConfirmationCreatesOnePendingMembership() {
        Fixture f=fixture(); String token=invitations.manage(f.teacher.getEmail(),"classroom",f.classroomId,"current").token();
        assertEquals("READY",invitations.preview(f.student.getEmail(),"classroom",token).state());
        assertFalse(memberships.existsByClassroom_ClassroomIdAndUser_UserId(f.classroomId,f.student.getUserId()));
        assertEquals("PENDING",invitations.confirm(f.student.getEmail(),"classroom",token).state());
        assertEquals("PENDING",invitations.confirm(f.student.getEmail(),"classroom",token).state());
        assertEquals(1,memberships.findAllByClassroom_ClassroomIdAndStatus(f.classroomId,MembershipStatus.PENDING).size());
    }
    @Test void oppositeDirectionFriendConfirmationsCannotCreateTwoRowsOrAutoAccept() throws Exception {
        Fixture f=fixture(); String a=invitations.manage(f.student.getEmail(),"friend",null,"current").token();
        String b=invitations.manage(f.other.getEmail(),"friend",null,"current").token();
        parallel(() -> invitations.confirm(f.other.getEmail(),"friend",a).state(), () -> invitations.confirm(f.student.getEmail(),"friend",b).state());
        var rows=connections.findConnectionsBetween(f.student.getUserId(),f.other.getUserId());
        assertEquals(1,rows.size()); assertEquals(FriendshipStatus.PENDING,rows.getFirst().getStatus());
    }
    @Test void manualCodeAndLinkUseTheSameFriendshipWriteGuard() throws Exception {
        Fixture f=fixture(); String token=invitations.manage(f.other.getEmail(),"friend",null,"current").token();
        parallel(() -> { try { friendships.sendFriendRequest(f.student.getUserId(),f.other.getFriendCode()); return "created"; } catch(BadRequestException duplicate) { return "existing"; } },
                () -> invitations.confirm(f.student.getEmail(),"friend",token).state());
        assertEquals(1,connections.findConnectionsBetween(f.student.getUserId(),f.other.getUserId()).size());
    }
    @Test void manualCodeAndLinkSerializeClassroomMembershipCreation() throws Exception {
        Fixture f=fixture(); String token=invitations.manage(f.teacher.getEmail(),"classroom",f.classroomId,"current").token();
        String code=classrooms.findById(f.classroomId).orElseThrow().getInviteCode();
        parallel(() -> { try { classroomService.joinClassroom(code,f.student.getEmail()); return "created"; } catch(BadRequestException duplicate) { return "existing"; } },
                () -> invitations.confirm(f.student.getEmail(),"classroom",token).state());
        assertEquals(1,memberships.findAllByClassroom_ClassroomIdAndStatus(f.classroomId,MembershipStatus.PENDING).size());
    }
    @Test void rotationAndRevocationDoNotCancelExistingRequestsAndOldLinksStopWorking() {
        Fixture f=fixture(); String old=invitations.manage(f.other.getEmail(),"friend",null,"current").token();
        invitations.confirm(f.student.getEmail(),"friend",old);
        String current=invitations.manage(f.other.getEmail(),"friend",null,"rotate").token(); assertNotEquals(old,current);
        assertThrows(ResourceNotFoundException.class, () -> invitations.preview(f.student.getEmail(),"friend",old));
        invitations.manage(f.other.getEmail(),"friend",null,"revoke");
        assertThrows(ResourceNotFoundException.class, () -> invitations.confirm(f.student.getEmail(),"friend",current));
        assertEquals(1,connections.findConnectionsBetween(f.student.getUserId(),f.other.getUserId()).size());
    }
    @Test void rotationRacingWithConfirmationHasAnOrderedOutcome() throws Exception {
        Fixture f=fixture(); String token=invitations.manage(f.other.getEmail(),"friend",null,"current").token();
        parallel(() -> { try { return invitations.confirm(f.student.getEmail(),"friend",token).state(); } catch(ResourceNotFoundException revoked) { return "revoked"; } },
                () -> invitations.manage(f.other.getEmail(),"friend",null,"rotate").token());
        assertTrue(connections.findConnectionsBetween(f.student.getUserId(),f.other.getUserId()).size()<=1);
        assertThrows(ResourceNotFoundException.class, () -> invitations.preview(f.student.getEmail(),"friend",token));
    }
    @Test void archivedClassroomAndInactiveOwnerAreUnavailableInsteadOfServerErrors() {
        Fixture f=fixture(); String classroom=invitations.manage(f.teacher.getEmail(),"classroom",f.classroomId,"current").token();
        String friend=invitations.manage(f.other.getEmail(),"friend",null,"current").token();
        tx.executeWithoutResult(status -> { classrooms.deleteById(f.classroomId); users.findById(f.other.getUserId()).orElseThrow().setActive(false); });
        assertThrows(ResourceNotFoundException.class, () -> invitations.preview(f.student.getEmail(),"classroom",classroom));
        assertThrows(ResourceNotFoundException.class, () -> invitations.confirm(f.student.getEmail(),"friend",friend));
    }
    @Test void teacherAndUnverifiedAccountsCannotBypassTheCodeBasedJoinPolicy() {
        Fixture f=fixture(); String token=invitations.manage(f.teacher.getEmail(),"classroom",f.classroomId,"current").token();
        tx.executeWithoutResult(status -> users.findById(f.other.getUserId()).orElseThrow().setRole(Role.TEACHER));
        assertThrows(ForbiddenException.class, () -> invitations.confirm(f.other.getEmail(),"classroom",token));
        String code=classrooms.findById(f.classroomId).orElseThrow().getInviteCode();
        assertThrows(ForbiddenException.class, () -> classroomService.joinClassroom(code,f.other.getEmail()));
        tx.executeWithoutResult(status -> users.findById(f.student.getUserId()).orElseThrow().setVerified(false));
        assertThrows(ForbiddenException.class, () -> classroomService.joinClassroom(code,f.student.getEmail()));
    }
    @Test void securityFilterRequiresAuthenticationAndServicesRequireVerification() throws Exception {
        Fixture f=fixture(); String token=invitations.manage(f.other.getEmail(),"friend",null,"current").token();
        mvc.perform(post("/api/invitations/preview").contentType(MediaType.APPLICATION_JSON).content(body("friend",token))).andExpect(status().isUnauthorized());
        tx.executeWithoutResult(status -> users.findById(f.student.getUserId()).orElseThrow().setVerified(false));
        mvc.perform(post("/api/invitations/confirm").header("Authorization",auth(f.student)).contentType(MediaType.APPLICATION_JSON).content(body("friend",token))).andExpect(status().isForbidden());
        assertTrue(connections.findConnectionsBetween(f.student.getUserId(),f.other.getUserId()).isEmpty());
    }
    @Test void httpPreviewIsMinimalNonCacheableAndBadInputHasNoEffects() throws Exception {
        Fixture f=fixture(); String token=invitations.manage(f.other.getEmail(),"friend",null,"current").token();
        var response=mvc.perform(post("/api/invitations/preview").header("Authorization",auth(f.student)).contentType(MediaType.APPLICATION_JSON).content(body("friend",token)))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andExpect(jsonPath("$.state").value("READY")).andReturn().getResponse().getContentAsString();
        assertFalse(response.contains(f.other.getEmail())); assertFalse(response.contains(token));
        mvc.perform(post("/api/invitations/confirm").header("Authorization",auth(f.student)).contentType(MediaType.APPLICATION_JSON).content(body("friend","bad"))).andExpect(status().isBadRequest());
        mvc.perform(get("/api/invitations/confirm").header("Authorization",auth(f.student))).andExpect(status().isMethodNotAllowed());
        assertTrue(connections.findConnectionsBetween(f.student.getUserId(),f.other.getUserId()).isEmpty());
    }
    @Test void httpLinkManagementChecksOwnershipAndHasBoundedRequestRates() throws Exception {
        Fixture f=fixture();
        mvc.perform(post("/api/invitations/classrooms/"+f.classroomId).header("Authorization",auth(f.other))).andExpect(status().isForbidden());
        for(int i=0;i<10;i++) mvc.perform(post("/api/invitations/friend").header("Authorization",auth(f.student))).andExpect(status().isOk());
        mvc.perform(post("/api/invitations/friend").header("Authorization",auth(f.student))).andExpect(status().isTooManyRequests()).andExpect(header().string("Retry-After","60"));
    }
}
