package com.languageapp.backend.security;

import com.languageapp.backend.enums.AuthProvider;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OAuthHandlerTrustTest {
    final FederatedIdentityService service=mock(FederatedIdentityService.class);
    OAuth2AuthenticationFailureHandler failure() {
        var failure=new OAuth2AuthenticationFailureHandler();ReflectionTestUtils.setField(failure,"frontendUrl","https://localhost");return failure;
    }
    OAuth2AuthenticationSuccessHandler success() {
        var handler=new OAuth2AuthenticationSuccessHandler(service,failure());ReflectionTestUtils.setField(handler,"frontendUrl","https://localhost");return handler;
    }
    DefaultOAuth2User raw() {
        return new DefaultOAuth2User(List.of(new SimpleGrantedAuthority("ROLE_USER")),Map.of("email","victim@example.test","sub","subject"),"sub");
    }
    @Test void emailOnlyPrincipalsCannotMintCredentials() throws Exception {
        var principal=raw();var response=new MockHttpServletResponse();
        success().onAuthenticationSuccess(new MockHttpServletRequest(),response,new UsernamePasswordAuthenticationToken(principal,"unused",principal.getAuthorities()));
        assertEquals("https://localhost/login?oauth_error=identity_unavailable",response.getRedirectedUrl());
        assertNull(response.getHeader("Set-Cookie"));verifyNoInteractions(service);
    }
    @Test void aProviderMismatchCannotUseAnotherProvidersBinding() throws Exception {
        var principal=new FederatedOAuth2User(new FederatedIdentityService.Binding(UUID.randomUUID(),AuthProvider.GOOGLE,"subject"),raw());
        var response=new MockHttpServletResponse();
        success().onAuthenticationSuccess(new MockHttpServletRequest(),response,new OAuth2AuthenticationToken(principal,principal.getAuthorities(),"discord"));
        assertNull(response.getHeader("Set-Cookie"));verifyNoInteractions(service);
    }
    @Test void revocationBetweenUserinfoAndSuccessCannotIssueCredentials() throws Exception {
        var principal=new FederatedOAuth2User(new FederatedIdentityService.Binding(UUID.randomUUID(),AuthProvider.GOOGLE,"subject"),raw());
        when(service.issueSession(principal)).thenThrow(OAuthClaims.failure("identity_unavailable"));
        var response=new MockHttpServletResponse();
        success().onAuthenticationSuccess(new MockHttpServletRequest(),response,new OAuth2AuthenticationToken(principal,principal.getAuthorities(),"google"));
        assertNull(response.getHeader("Set-Cookie"));assertFalse(response.getRedirectedUrl().contains("token="));
    }
    @Test void providerErrorDescriptionsAndArbitraryCodesNeverReachTheRedirect() throws Exception {
        var response=new MockHttpServletResponse();
        failure().onAuthenticationFailure(new MockHttpServletRequest(),response,
                new OAuth2AuthenticationException(new OAuth2Error("https://untrusted.test/?token=secret"),"sensitive description"));
        assertEquals("https://localhost/login?oauth_error=identity_unavailable",response.getRedirectedUrl());
        assertEquals("no-store",response.getHeader("Cache-Control"));
    }

    @Test void sessionStorageFailureReturnsAFixedRetryableFailureWithoutACookie() throws Exception {
        var principal=new FederatedOAuth2User(new FederatedIdentityService.Binding(UUID.randomUUID(),AuthProvider.GOOGLE,"subject"),raw());
        when(service.issueSession(principal)).thenThrow(new org.springframework.dao.DataAccessResourceFailureException("test-only database unavailable"));
        var response=new MockHttpServletResponse();
        success().onAuthenticationSuccess(new MockHttpServletRequest(),response,new OAuth2AuthenticationToken(principal,principal.getAuthorities(),"google"));
        assertEquals("https://localhost/login?oauth_error=identity_unavailable",response.getRedirectedUrl());
        assertNull(response.getHeader("Set-Cookie"));assertNull(org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication());
    }
}
