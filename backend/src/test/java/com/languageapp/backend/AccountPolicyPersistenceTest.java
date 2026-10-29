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
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@EnabledIfEnvironmentVariable(named="ACCOUNT_POLICY_TEST_DATABASE_URL",matches="jdbc:postgresql://[^/]+/languageapp_account_policy_check_[a-zA-Z0-9_]+")
@SpringBootTest(properties={"spring.datasource.url=${ACCOUNT_POLICY_TEST_DATABASE_URL}","spring.jpa.hibernate.ddl-auto=create","spring.jpa.show-sql=false",
        "jwt.secret=U2VjdXJpdHkgZm91bmRhdGlvbnMgdGVzdCBvbmx5IHNpZ25pbmcga2V5IG5vdCBmb3IgZGVwbG95bWVudCE="})
class AccountPolicyPersistenceTest {
    @Autowired AdminService admins;
    @Autowired UserService profiles;
    @Autowired AccountDeletionService deletion;
    @Autowired AuthenticationService authentication;
    @Autowired CustomUserDetailsService details;
    @Autowired RefreshTokenService refresh;
    @Autowired JwtService jwt;
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager manager;
    @Autowired WebApplicationContext context;
    @Autowired PasswordEncoder encoder;
    @MockitoBean DataSeeder seeder;
    @MockitoBean EmailService mail;
    @MockitoBean ImageStorageService images;
    @MockitoBean SseService sse;
    @MockitoBean AssignmentExpiryScheduler expiry;
    @MockitoBean ChallengeScheduler challenges;
    TransactionTemplate tx;MockMvc mvc;
    @BeforeEach void setup(){tx=new TransactionTemplate(manager);mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();}
    User user(Role role,boolean verified){return tx.execute(status->{
        String id=UUID.randomUUID().toString().substring(0,8);var c=new Course();c.setLanguageCode(id);c.setLanguageName("Test");em.persist(c);
        var u=new User();u.setName(id);u.setEmail(id+"@example.test");u.setRole(role);u.setVerified(verified);u.setActiveCourse(c);u.setPasswordHash(encoder.encode("Policy-test-password"));em.persist(u);em.flush();return u;
    });}
    String auth(User u){var s=refresh.createSession(u.getUserId());return "Bearer "+jwt.generateSessionToken(org.springframework.security.core.userdetails.User.withUsername(u.getEmail()).password("test-only").authorities("ROLE_"+u.getRole().name()).build(),s.id());}
    @ParameterizedTest @ValueSource(strings={"/api/classrooms/teacher","/api/classrooms/x/members","/api/assignments/active","/api/friendships","/api/challenges/active","/api/admin/users","/api/exercises/00000000-0000-0000-0000-000000000000/check"})
    void unverifiedPrivilegedCallsAreDeniedBeforeTheController(String path) throws Exception {
        var u=user(Role.ADMIN,false);
        var request=path.endsWith("check")?post(path).contentType("application/json").content("{\"answer\":\"apple\"}"):get(path);
        mvc.perform(request.header("Authorization",auth(u))).andExpect(status().isForbidden()).andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("e-mail")));
    }
    @Test void unverifiedTeacherCannotCreateClassroomButProfileRecoveryAndPracticeRemainAvailable() throws Exception {
        var u=user(Role.TEACHER,false);String token=auth(u);
        mvc.perform(post("/api/classrooms").header("Authorization",token).contentType("application/json").content("{\"name\":\"Blocked\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/users/me").header("Authorization",token)).andExpect(status().isOk());
        mvc.perform(get("/api/lessons").header("Authorization",token)).andExpect(status().isOk());
        mvc.perform(post("/api/users/me/verification-email").header("Authorization",token)).andExpect(status().isNoContent());
    }
    @Test void verificationTakesEffectWithoutReplacingTheExistingJwt() throws Exception {
        var u=user(Role.ADMIN,false);String token=auth(u);
        mvc.perform(get("/api/admin/users").header("Authorization",token)).andExpect(status().isForbidden());
        tx.executeWithoutResult(s->em.find(User.class,u.getUserId()).setVerified(true));
        mvc.perform(get("/api/admin/users").header("Authorization",token)).andExpect(status().isOk());
    }
    @Test void unverifiedChallengeQueryCannotBypassTheSocialGate() throws Exception {
        var u=user(Role.STUDENT,false);
        mvc.perform(post("/api/lessons/"+UUID.randomUUID()+"/attempts").param("challengeId",UUID.randomUUID().toString()).header("Authorization",auth(u)))
                .andExpect(status().isForbidden());
    }
    @ParameterizedTest @ValueSource(strings={"ADMIN","DELETED"})
    void registrationRejectsEveryNonPublicRoleWithoutCreatingAnAccount(String role){
        var r=new RegisterRequest();r.setName("Rejected");r.setEmail(UUID.randomUUID()+"@example.test");r.setPassword("Policy-test-password");r.setRole(Role.valueOf(role));
        assertThrows(BadRequestException.class,()->authentication.register(r));
        long accounts=tx.execute(s->em.createQuery("select count(u) from User u where u.email=:email",Long.class).setParameter("email",r.getEmail()).getSingleResult());
        assertEquals(0L,accounts);
    }
    @ParameterizedTest @ValueSource(strings={"STUDENT","TEACHER","DEFAULT"})
    void publicStudentTeacherAndDefaultRegistrationRemainAvailable(String role){
        tx.executeWithoutResult(s->{
            if(em.createQuery("select count(c) from Course c where c.languageCode='en'",Long.class).getSingleResult()==0){
                var c=new Course();c.setLanguageCode("en");c.setLanguageName("English");em.persist(c);
            }
        });
        var r=new RegisterRequest();r.setName("Registered"+UUID.randomUUID().toString().substring(0,8));r.setEmail(UUID.randomUUID()+"@example.test");
        r.setPassword("Policy-test-password");r.setRole(role.equals("DEFAULT")?null:Role.valueOf(role));
        var result=authentication.register(r);assertNotNull(result.refreshToken());
        var stored=tx.execute(s->em.createQuery("select u from User u where u.email=:email",User.class).setParameter("email",r.getEmail()).getSingleResult());
        assertEquals(role.equals("TEACHER")?Role.TEACHER:Role.STUDENT,stored.getRole());assertFalse(stored.isVerified());
    }
    @Test void deletedRoleCannotCreateOrRefreshAUsableSessionEvenIfLegacyActiveFlagIsTrue() throws Exception {
        var u=user(Role.STUDENT,true);var session=refresh.createSession(u.getUserId());String token=auth(u);
        tx.executeWithoutResult(s->em.find(User.class,u.getUserId()).setRole(Role.DELETED));
        assertFalse(details.loadUserByUsername(u.getEmail()).isEnabled());
        assertFalse(refresh.isSessionActive(session.id(),u.getEmail()));
        assertThrows(BadRequestException.class,()->refresh.createSession(u.getUserId()));
        assertThrows(BadRequestException.class,()->authentication.refreshToken(session.rawToken()));
        mvc.perform(get("/api/users/me").header("Authorization",token)).andExpect(status().isUnauthorized());
    }
    @Test void adminSelfDemotionSuspensionAndBothDeletionRoutesAreRejected(){
        var u=user(Role.ADMIN,true);
        assertThrows(ForbiddenException.class,()->admins.updateUserRole(u.getUserId(),Role.STUDENT,u.getEmail()));
        assertThrows(ForbiddenException.class,()->admins.toggleUserStatus(u.getUserId(),false,u.getEmail()));
        assertThrows(ForbiddenException.class,()->profiles.deleteAndAnonymizeMyAccount(u.getEmail(),"Policy-test-password",true));
        tx.executeWithoutResult(s->em.find(User.class,u.getUserId()).setAuthProvider(AuthProvider.GOOGLE));
        assertThrows(ForbiddenException.class,()->deletion.request(u.getEmail(),null));
        assertEquals(Role.ADMIN,tx.execute(s->em.find(User.class,u.getUserId()).getRole()));
        verifyNoInteractions(mail);
    }
    @Test void adminCannotGrantDeletedRoleOrReactivateAnAnonymizedAccount(){
        var a=user(Role.ADMIN,true);var target=user(Role.DELETED,true);
        assertThrows(BadRequestException.class,()->admins.updateUserRole(target.getUserId(),Role.DELETED,a.getEmail()));
        assertThrows(ConflictException.class,()->admins.updateUserRole(target.getUserId(),Role.STUDENT,a.getEmail()));
        assertThrows(ConflictException.class,()->admins.toggleUserStatus(target.getUserId(),true,a.getEmail()));
    }
    @Test void ordinaryRoleChangesAndBannedAccountRestorationStillWork(){
        var a=user(Role.ADMIN,true);var target=user(Role.STUDENT,true);
        admins.updateUserRole(target.getUserId(),Role.TEACHER,a.getEmail());admins.toggleUserStatus(target.getUserId(),false,a.getEmail());admins.toggleUserStatus(target.getUserId(),true,a.getEmail());
        var stored=tx.execute(s->em.find(User.class,target.getUserId()));assertEquals(Role.TEACHER,stored.getRole());assertTrue(stored.isActive());
    }
    @Test void aDemotedAdminCannotUseAnAlreadyIssuedJwtToMutatePrivileges() throws Exception {
        var a=user(Role.ADMIN,true);var b=user(Role.ADMIN,true);String old=auth(b);
        admins.updateUserRole(b.getUserId(),Role.STUDENT,a.getEmail());
        mvc.perform(put("/api/admin/users/"+a.getUserId()+"/role").param("newRole","STUDENT").header("Authorization",old)).andExpect(status().isForbidden());
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void concurrentAdminsCannotDemoteOrSuspendEachOtherAndLeaveNoUsableAdmin(boolean suspend) throws Exception {
        var a=user(Role.ADMIN,true);var b=user(Role.ADMIN,true);var pool=Executors.newFixedThreadPool(2);var ready=new CountDownLatch(2);var go=new CountDownLatch(1);
        java.util.function.BiFunction<User,User,Boolean> change=(actor,target)->{
            ready.countDown();try{assertTrue(go.await(10,TimeUnit.SECONDS));if(suspend)admins.toggleUserStatus(target.getUserId(),false,actor.getEmail());else admins.updateUserRole(target.getUserId(),Role.STUDENT,actor.getEmail());return true;}
            catch(ForbiddenException denied){return false;}catch(InterruptedException interrupted){throw new IllegalStateException(interrupted);}
        };
        try{var one=pool.submit(()->change.apply(a,b));var two=pool.submit(()->change.apply(b,a));assertTrue(ready.await(10,TimeUnit.SECONDS));go.countDown();assertNotEquals(one.get(25,TimeUnit.SECONDS),two.get(25,TimeUnit.SECONDS));}
        finally{go.countDown();pool.shutdownNow();}
        long remaining=tx.execute(s->em.createQuery("select count(u) from User u where u.userId in :ids and u.role=:role and u.isActive=true",Long.class).setParameter("ids",List.of(a.getUserId(),b.getUserId())).setParameter("role",Role.ADMIN).getSingleResult());
        assertEquals(1,remaining);
    }
}
