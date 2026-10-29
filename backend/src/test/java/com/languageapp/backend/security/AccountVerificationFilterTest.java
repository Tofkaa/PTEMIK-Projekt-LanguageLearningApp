package com.languageapp.backend.security;

import com.languageapp.backend.enums.Role;
import com.languageapp.backend.repository.UserRepository;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import tools.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AccountVerificationFilterTest {
    @AfterEach void clear(){SecurityContextHolder.clearContext();}
    @Test void matrixProtectsEveryClassroomSocialAndStaffRouteWithoutBlockingRecoveryAndPractice(){
        for(String p:List.of("/api/classrooms","/api/classrooms/x/members","/api/assignments/sessions/x/submit","/api/friendships/request","/api/challenges/create","/api/invitations/x/preview","/api/admin/users","/api/exercises/x/check","/api/lessons/all-for-challenge"))
            assertTrue(AccountVerificationFilter.requiresVerification(p,null),p);
        for(String p:List.of("/api/auth/refresh","/api/auth/verify","/api/users/me","/api/users/me/verification-email","/api/oauth-links/request","/api/vocabulary/lookup","/api/lessons/x/attempts","/api/notifications/summary","/api/classrooms-lookalike"))
            assertFalse(AccountVerificationFilter.requiresVerification(p,null),p);
        assertTrue(AccountVerificationFilter.requiresVerification("/api/lessons/x/submit","challenge"));
    }
    @Test void unverifiedAuthenticatedUserGetsNoBusinessCallButAConfirmedUserPasses() throws Exception {
        var users=mock(UserRepository.class);var chain=mock(jakarta.servlet.FilterChain.class);
        var filter=new AccountVerificationFilter(users,new ObjectMapper(),Clock.systemUTC());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("u@example.test","ignored",List.of()));
        var request=new MockHttpServletRequest("POST","/api/classrooms");var response=new MockHttpServletResponse();
        when(users.findAccessState("u@example.test")).thenReturn(Optional.of(new UserRepository.AccessState(UUID.randomUUID(),true,false,Role.TEACHER)));
        filter.doFilter(request,response,chain);assertEquals(403,response.getStatus());assertEquals("no-store",response.getHeader("Cache-Control"));verifyNoInteractions(chain);
        assertTrue(response.getContentAsString().contains("e-mail"));
        when(users.findAccessState("u@example.test")).thenReturn(Optional.of(new UserRepository.AccessState(UUID.randomUUID(),true,true,Role.TEACHER)));
        var accepted=new MockHttpServletResponse();filter.doFilter(new MockHttpServletRequest("POST","/api/classrooms"),accepted,chain);verify(chain).doFilter(any(),eq(accepted));
    }
    @Test void contextPathDoesNotBypassVerificationAndOrdinaryProfileDoesNotQueryTheGate() throws Exception {
        var users=mock(UserRepository.class);var chain=mock(jakarta.servlet.FilterChain.class);
        var filter=new AccountVerificationFilter(users,new ObjectMapper(),Clock.systemUTC());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("u@example.test","ignored",List.of()));
        var profile=new MockHttpServletRequest("GET","/api/users/me");filter.doFilter(profile,new MockHttpServletResponse(),chain);verifyNoInteractions(users);
        var request=new MockHttpServletRequest("GET","/app/api/assignments/active");request.setContextPath("/app");var denied=new MockHttpServletResponse();
        filter.doFilter(request,denied,chain);assertEquals(403,denied.getStatus());
    }
}
