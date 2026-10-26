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
        for(String raw:List.of(first,second)) mvc.perform(post("/api/auth/refresh").cookie(new MockCookie("refreshToken",raw))).andExpect(status().isOk());
        mvc.perform(post("/api/auth/reset-password").contentType(MediaType.APPLICATION_JSON).content(resetBody(reset,"Foundation-new-password"))).andExpect(status().isOk());
        assertTrue(encoder.matches("Foundation-new-password",users.findById(f.student.getUserId()).orElseThrow().getPasswordHash()));
        for(String raw:List.of(first,second)) {
            assertTrue(refresh.findByRawToken(raw).isEmpty());
            mvc.perform(post("/api/auth/refresh").cookie(new MockCookie("refreshToken",raw))).andExpect(status().isBadRequest());
        }
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\""+f.student.getEmail()+"\",\"password\":\"Foundation-new-password\"}"))
                .andExpect(status().isOk()).andExpect(cookie().httpOnly("refreshToken",true)).andExpect(cookie().secure("refreshToken",true));
    }
    @Test void anExpiredResetLeavesTheExistingPasswordAndSessionIntact() throws Exception {
        var f=fixture();String raw=seedSession(f.student),reset=seedReset(f.student,-1);
        mvc.perform(post("/api/auth/reset-password").contentType(MediaType.APPLICATION_JSON).content(resetBody(reset,"Foundation-new-password"))).andExpect(status().isBadRequest());
        assertTrue(encoder.matches("Foundation-old-password",users.findById(f.student.getUserId()).orElseThrow().getPasswordHash()));
        assertTrue(refresh.findByRawToken(raw).isPresent());
    }
    @Test void aConsumedResetCannotRevokeANewerLoginSession() throws Exception {
        var f=fixture();String reset=seedReset(f.student,60);
        mvc.perform(post("/api/auth/reset-password").contentType(MediaType.APPLICATION_JSON).content(resetBody(reset,"Foundation-new-password"))).andExpect(status().isOk());
        String newSession=refresh.createRefreshToken(f.student.getUserId());
        mvc.perform(post("/api/auth/reset-password").contentType(MediaType.APPLICATION_JSON).content(resetBody(reset,"Foundation-other-password"))).andExpect(status().isBadRequest());
        assertTrue(refresh.findByRawToken(newSession).isPresent());
        assertTrue(encoder.matches("Foundation-new-password",users.findById(f.student.getUserId()).orElseThrow().getPasswordHash()));
    }
}
