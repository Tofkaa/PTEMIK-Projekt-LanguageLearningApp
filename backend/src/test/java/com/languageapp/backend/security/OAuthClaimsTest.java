package com.languageapp.backend.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.core.user.OAuth2User;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OAuthClaimsTest {
    static OAuth2UserRequest request(String provider) {
        var registration=ClientRegistration.withRegistrationId(provider).clientId("test-client").clientSecret("test-secret")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE).redirectUri("http://localhost/callback")
                .authorizationUri("http://provider.test/authorize").tokenUri("http://provider.test/token")
                .userInfoUri("http://provider.test/userinfo").userNameAttributeName(provider.equals("discord")?"id":"sub").build();
        return new OAuth2UserRequest(registration,new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER,"provider-test-token",null,null));
    }
    static Map<String,Object> claims(String provider) {
        Map<String,Object> values=new HashMap<>(); values.put(provider.equals("discord")?"id":"sub","123456");
        values.put(provider.equals("discord")?"verified":"email_verified",true); values.put("email","Student@Example.test");
        values.put(provider.equals("discord")?"username":"name","Student"); return values;
    }
    static OAuth2User principal(Map<String,Object> claims,String subject) {
        OAuth2User principal=mock(OAuth2User.class); when(principal.getAttributes()).thenReturn(claims); when(principal.getName()).thenReturn(subject); return principal;
    }
    @ParameterizedTest @ValueSource(strings={"google","discord"})
    void bindsTheStableSubjectAndRequiresAnExplicitBooleanVerifiedEmail(String provider) {
        var result=OAuthClaims.from(request(provider),principal(claims(provider),"123456"));
        assertEquals(provider.toUpperCase(Locale.ROOT),result.provider().name()); assertEquals("123456",result.subject());
        assertEquals("student@example.test",result.email()); assertEquals("Student",result.name());
    }
    @ParameterizedTest @ValueSource(strings={"google","discord"})
    void rejectsFalseMissingAndStringVerificationClaims(String provider) {
        for(Object value:Arrays.asList(false,null,"true",1)) {
            var attributes=claims(provider); attributes.put(provider.equals("discord")?"verified":"email_verified",value);
            var failure=assertThrows(OAuth2AuthenticationException.class,()->OAuthClaims.from(request(provider),principal(attributes,"123456")));
            assertEquals("unverified_email",failure.getError().getErrorCode());
        }
    }
    @Test void refusesUnsupportedProvidersAndMismatchingPrincipalNames() {
        assertThrows(OAuth2AuthenticationException.class,()->OAuthClaims.from(request("other"),principal(claims("google"),"123456")));
        assertThrows(OAuth2AuthenticationException.class,()->OAuthClaims.from(request("google"),principal(claims("google"),"different")));
    }
    @ParameterizedTest @ValueSource(strings={"", "bad subject", "line\nbreak"})
    void refusesMalformedStableSubjects(String subject) {
        var attributes=claims("google"); attributes.put("sub",subject);
        assertThrows(OAuth2AuthenticationException.class,()->OAuthClaims.from(request("google"),principal(attributes,subject)));
    }
    @Test void refusesMissingOrMalformedEmailsAndOversizedIdentifiers() {
        for(Object email:Arrays.asList(null,"","not-an-email"," spaced@example.test","x".repeat(260)+"@example.test")) {
            var attributes=claims("google"); attributes.put("email",email);
            assertThrows(OAuth2AuthenticationException.class,()->OAuthClaims.from(request("google"),principal(attributes,"123456")));
        }
        var attributes=claims("google"); attributes.put("sub","x".repeat(256));
        assertThrows(OAuth2AuthenticationException.class,()->OAuthClaims.from(request("google"),principal(attributes,"x".repeat(256))));
    }
    @Test void doesNotUseAnUnsafeOptionalPhotoAsAnImageSource() {
        var attributes=claims("google"); attributes.put("picture","javascript:alert(1)");
        assertNull(OAuthClaims.from(request("google"),principal(attributes,"123456")).picture());
    }
}
