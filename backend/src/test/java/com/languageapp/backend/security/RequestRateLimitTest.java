package com.languageapp.backend.security;

import com.languageapp.backend.exception.TooManyRequestsException;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.*;
import org.springframework.security.core.context.SecurityContextHolder;
import tools.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RequestRateLimitTest {
    Clock clock=Clock.fixed(Instant.parse("2026-10-28T12:00:00Z"),ZoneOffset.UTC);
    @AfterEach void clear(){SecurityContextHolder.clearContext();}
    @Test void opaqueKeysAreScopedAndNeverStoreRawIdentity() {
        var a=RequestBudgetStore.budget("login","account","someone@example.test",20,300);
        assertTrue(a.key().matches("[0-9a-f]{64}"));
        assertNotEquals(a.key(),RequestBudgetStore.budget("login","ip","someone@example.test",20,300).key());
        assertNotEquals(a.key(),RequestBudgetStore.budget("reset","account","someone@example.test",20,300).key());
        assertThrows(IllegalArgumentException.class,()->new RequestBudgetStore.Budget("key",0,60));
    }
    @Test void authAndExpensiveRoutesAreProtectedWithoutThrottlingExamRecovery() {
        for(String path:List.of("/api/auth/login","/api/auth/register","/api/auth/forgot-password",
                "/api/auth/reset-password","/api/auth/oauth/exchange","/api/users/me/verification-email",
                "/api/users/me/email-change/request","/api/users/me/email-change/verify","/api/oauth-links/request",
                "/api/oauth-links/verify","/api/users/me/deletion/request","/api/users/me/deletion/confirm",
                "/api/friendships/request","/api/classrooms/join","/api/vocabulary/lookup","/api/vocabulary/known-batch",
                "/api/vocabulary/practice/check","/api/lessons/lesson/attempts/attempt/exercises/word/check",
                "/api/exercises/word/check","/api/invitations/token/confirm"))
            assertNotNull(RequestOperation.route("POST",path),path);
        for(String path:List.of("/api/assignments/sessions/session/draft","/api/assignments/sessions/session/submit")) {
            assertNull(RequestOperation.route("POST",path));assertNull(RequestOperation.route("PUT",path));
        }
        assertNull(RequestOperation.route("OPTIONS","/api/auth/login"));
        assertNull(RequestOperation.route("GET","/api/users/me"));
        assertNotNull(RequestOperation.route("GET","/api/users/me/export"));
    }
    @Test void untrustedForwardingHeadersCannotRotateClientIp() {
        var request=new MockHttpServletRequest();request.setRemoteAddr("192.0.2.1");
        request.addHeader("X-Forwarded-For","198.51.100.2");request.addHeader("X-App-Client-IP","198.51.100.3");
        assertEquals("192.0.2.1",new ClientAddressResolver("",clock).address(request));
        var resolver=new ClientAddressResolver("frontend",clock){@Override protected Set<String> resolveTrusted(){return Set.of("172.18.0.4");}};
        assertEquals("192.0.2.1",resolver.address(request));
        request.setRemoteAddr("172.18.0.4");
        assertEquals("198.51.100.3",resolver.address(request));
        request.removeHeader("X-App-Client-IP");request.addHeader("X-App-Client-IP","1.2.3.4, 5.6.7.8");
        assertEquals("172.18.0.4",resolver.address(request));
    }
    @Test void proxyDnsFailureDoesNotTrustSuppliedClientIdentity() {
        var request=new MockHttpServletRequest();request.setRemoteAddr("172.18.0.4");request.addHeader("X-App-Client-IP","1.2.3.4");
        var resolver=new ClientAddressResolver("frontend",clock){@Override protected Set<String> resolveTrusted() throws java.net.UnknownHostException{throw new java.net.UnknownHostException();}};
        assertEquals("172.18.0.4",resolver.address(request));
    }
    @Test void filteredRejectionHasRetryAfterNoCookieAndNeverReachesBusinessHandler() throws Exception {
        var limiter=mock(ApiRequestLimiter.class);
        doThrow(new TooManyRequestsException(15)).when(limiter).request(RequestOperation.LOGIN,"127.0.0.1",null,false);
        var filter=new ApiRateLimitFilter(limiter,new ClientAddressResolver("",clock),new ObjectMapper(),clock);
        var request=new MockHttpServletRequest("POST","/api/auth/login");var response=new MockHttpServletResponse();
        var chain=mock(jakarta.servlet.FilterChain.class);filter.doFilter(request,response,chain);
        assertEquals(429,response.getStatus());assertEquals("15",response.getHeader("Retry-After"));
        assertEquals("no-store",response.getHeader("Cache-Control"));assertNull(response.getHeader("Set-Cookie"));
        assertTrue(response.getContentAsString().contains("15 másodperc"));verifyNoInteractions(chain);
    }
    @Test void aBearerTokenCannotBypassThePublicBudgetOrDoubleChargeTheLoginIdentity() {
        var store=mock(RequestBudgetStore.class);var limiter=new ApiRequestLimiter(store);
        limiter.request(RequestOperation.LOGIN,"192.0.2.1","logged-in@example.test",false);
        verify(store).check(List.of(
            RequestBudgetStore.budget("requests","global","all",120000,60),
            RequestBudgetStore.budget("public","global","all",6000,60),
            RequestBudgetStore.budget("LOGIN","ip","192.0.2.1",2400,60)));
    }
    @Test void accountIdentityVariantsConsumeTheSameQuota() {
        var store=mock(RequestBudgetStore.class);var limiter=new ApiRequestLimiter(store);
        limiter.anonymousAccount(RequestOperation.LOGIN,"  STUDENT@example.test ");
        limiter.anonymousAccount(RequestOperation.LOGIN,"student@example.test");
        var expected=List.of(RequestBudgetStore.budget("LOGIN","account","student@example.test",20,300));
        verify(store,times(2)).check(expected);
    }
}
