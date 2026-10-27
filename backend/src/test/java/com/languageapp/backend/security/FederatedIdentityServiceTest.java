package com.languageapp.backend.security;

import com.languageapp.backend.entity.*;
import com.languageapp.backend.enums.*;
import com.languageapp.backend.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import java.time.Clock;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FederatedIdentityServiceTest {
    @Mock UserRepository users;
    @Mock OAuthIdentityRepository identities;
    @Mock CourseRepository courses;
    @Mock RefreshTokenService sessions;
    @Mock JwtService jwt;
    @Mock Clock clock;
    @InjectMocks FederatedIdentityService service;
    OAuthClaims claims() { return new OAuthClaims(AuthProvider.GOOGLE,"stable-subject","provider@example.test","Provider name",null); }
    User user() {
        User user=new User();user.setUserId(UUID.randomUUID());user.setEmail("stored@example.test");user.setVerified(true);
        user.setAuthProvider(AuthProvider.GOOGLE);user.setProviderId("stable-subject");user.setXp(75);return user;
    }
    @Test void aKnownSubjectKeepsItsAccountEvenIfTheProviderEmailChanges() {
        User user=user(); when(identities.findUserId(AuthProvider.GOOGLE,"stable-subject")).thenReturn(Optional.of(user.getUserId()));
        when(users.findForSessionUpdate(user.getUserId())).thenReturn(Optional.of(user));
        assertEquals(user.getUserId(),service.bind(claims()).userId());
        assertEquals("stored@example.test",user.getEmail()); assertEquals(75,user.getXp());
        verify(users,never()).existsByEmailIgnoreCase(anyString()); verify(identities,never()).saveAndFlush(any());
    }
    @Test void existingLocalEmailDoesNotCreateAnIdentityOrModifyItsAccount() {
        when(users.existsByEmailIgnoreCase("provider@example.test")).thenReturn(true);
        var failure=assertThrows(OAuth2AuthenticationException.class,()->service.bind(claims()));
        assertEquals("email_in_use",failure.getError().getErrorCode()); verify(users,never()).saveAndFlush(any());
        verify(identities,never()).saveAndFlush(any()); verifyNoInteractions(sessions,jwt);
    }
    @Test void exactLegacyProviderAndSubjectAreRequiredBeforeCreatingABinding() {
        User user=user(); when(users.findLegacyOAuthIds(eq(AuthProvider.GOOGLE),eq("stable-subject"),any())).thenReturn(List.of(user.getUserId()));
        when(users.findForSessionUpdate(user.getUserId())).thenReturn(Optional.of(user));
        assertEquals(user.getUserId(),service.bind(claims()).userId());
        ArgumentCaptor<OAuthIdentity> saved=ArgumentCaptor.forClass(OAuthIdentity.class);verify(identities).saveAndFlush(saved.capture());
        assertSame(user,saved.getValue().getUser());assertEquals("stable-subject",saved.getValue().getSubject());
        verify(users,never()).existsByEmailIgnoreCase(anyString());
    }
    @Test void mismatchedOrAmbiguousLegacyEvidenceFailsClosed() {
        User user=user();user.setAuthProvider(AuthProvider.LOCAL);
        when(users.findLegacyOAuthIds(eq(AuthProvider.GOOGLE),eq("stable-subject"),any())).thenReturn(List.of(user.getUserId()));
        when(users.findForSessionUpdate(user.getUserId())).thenReturn(Optional.of(user));
        assertThrows(OAuth2AuthenticationException.class,()->service.bind(claims()));
        when(users.findLegacyOAuthIds(eq(AuthProvider.GOOGLE),eq("stable-subject"),any())).thenReturn(List.of(UUID.randomUUID(),UUID.randomUUID()));
        assertThrows(OAuth2AuthenticationException.class,()->service.bind(claims()));
        verify(identities,never()).saveAndFlush(any());
    }
    @Test void enrollmentCreatesOnlyAVerifiedStudentAndItsProviderBinding() {
        when(courses.findByLanguageCode("en")).thenReturn(Optional.of(new Course()));
        when(users.saveAndFlush(any())).thenAnswer(call->{User user=call.getArgument(0);user.setUserId(UUID.randomUUID());return user;});
        var binding=service.bind(claims()); assertNotNull(binding.userId());
        ArgumentCaptor<User> created=ArgumentCaptor.forClass(User.class);verify(users).saveAndFlush(created.capture());
        assertEquals(Role.STUDENT,created.getValue().getRole());assertTrue(created.getValue().isVerified());
        assertEquals(AuthProvider.GOOGLE,created.getValue().getAuthProvider());verify(identities).saveAndFlush(any());
    }
    @Test void inactiveOrUnverifiedAccountsCannotObtainABinding() {
        User user=user();user.setActive(false);
        when(identities.findUserId(AuthProvider.GOOGLE,"stable-subject")).thenReturn(Optional.of(user.getUserId()));
        when(users.findForSessionUpdate(user.getUserId())).thenReturn(Optional.of(user));
        assertThrows(OAuth2AuthenticationException.class,()->service.bind(claims()));
        user.setActive(true);user.setVerified(false);assertThrows(OAuth2AuthenticationException.class,()->service.bind(claims()));
        verifyNoInteractions(sessions,jwt);
    }

    @Test void aRemovedIdentityCannotIssueANewSessionFromAnEarlierPrincipal() {
        User user=user();when(users.findForSessionUpdate(user.getUserId())).thenReturn(Optional.of(user));
        var principal=new FederatedOAuth2User(new FederatedIdentityService.Binding(user.getUserId(),AuthProvider.GOOGLE,"stable-subject"),
                new org.springframework.security.oauth2.core.user.DefaultOAuth2User(
                        List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER")),Map.of("sub","stable-subject"),"sub"));
        assertThrows(OAuth2AuthenticationException.class,()->service.issueSession(principal));
        verifyNoInteractions(sessions,jwt);
    }
    @Test void aBindingMovedToAnotherAccountCannotIssueCredentialsForTheEarlierOwner() {
        User user=user();when(users.findForSessionUpdate(user.getUserId())).thenReturn(Optional.of(user));
        when(identities.findUserId(AuthProvider.GOOGLE,"stable-subject")).thenReturn(Optional.of(UUID.randomUUID()));
        var principal=new FederatedOAuth2User(new FederatedIdentityService.Binding(user.getUserId(),AuthProvider.GOOGLE,"stable-subject"),
                new org.springframework.security.oauth2.core.user.DefaultOAuth2User(
                        List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER")),Map.of("sub","stable-subject"),"sub"));
        assertThrows(OAuth2AuthenticationException.class,()->service.issueSession(principal));
        verifyNoInteractions(sessions,jwt);
    }
}
