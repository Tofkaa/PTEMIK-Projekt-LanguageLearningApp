package com.languageapp.backend.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;

class BrowserOriginPolicyTest {
    final BrowserOriginPolicy policy = new BrowserOriginPolicy("https://app.example.test/");

    @ParameterizedTest
    @ValueSource(strings = {"https://app.example.test", "https://APP.example.test:443"})
    void exactOriginAndDefaultPortAreAccepted(String origin) { assertTrue(policy.allows(origin)); }

    @ParameterizedTest
    @ValueSource(strings = {"null", "", "https://app.example.test.evil.test", "http://app.example.test",
            "https://app.example.test:444", "https://user@app.example.test", "https://app.example.test/",
            "https://app.example.test/path", "https://app.example.test?x=1", "https://app.example.test#part",
            "https://app.example.test https://evil.test", "https://evil.test,https://app.example.test", "//app.example.test"})
    void malformedOrDifferentOriginsAreRejected(String origin) { assertFalse(policy.allows(origin)); }

    @ParameterizedTest
    @ValueSource(strings = {"null", "ftp://example.test", "/relative", "https://user@app.example.test", "https://app.example.test?secret=1"})
    void badConfigurationFailsClosedWithoutEchoingItsValue(String value) {
        var error = assertThrows(IllegalArgumentException.class, () -> new BrowserOriginPolicy(value));
        assertFalse(error.getMessage().contains(value));
    }

    @Test void missingOriginFailsEvenWithSpoofedHostOrForwardingHeaders() throws Exception {
        var request = new MockHttpServletRequest("POST", "/api/auth/logout");
        request.addHeader("Host", "app.example.test");request.addHeader("X-Forwarded-Host", "app.example.test");
        request.addHeader("Sec-Fetch-Site", "same-origin");
        assertBlocked(request);
    }

    @Test void duplicateOriginAndCrossSiteMetadataFailEvenWithACorrectOrigin() throws Exception {
        var request = trusted("/api/auth/refresh");request.addHeader("Origin", "https://app.example.test");assertBlocked(request);
        request = trusted("/api/auth/login");request.addHeader("Sec-Fetch-Site", "cross-site");assertBlocked(request);
        request = trusted("/api/auth/register");request.addHeader("Sec-Fetch-Site", "none");assertBlocked(request);
        request = trusted("/api/oauth-links/cancel");request.addHeader("Sec-Fetch-Site", "same-site");
        request.addHeader("Sec-Fetch-Site", "same-origin");assertBlocked(request);
    }

    @Test void allowedAjaxDoesNotDependOnFetchMetadataSupport() throws Exception {
        for (String metadata : new String[]{null, "same-origin", "same-site"}) {
            var request = trusted("/api/auth/refresh");if(metadata != null)request.addHeader("Sec-Fetch-Site", metadata);
            assertPassed(request);
        }
    }

    @Test void oauthNavigationsAndBearerApisRemainSeparateFromCookieMutations() throws Exception {
        assertPassed(new MockHttpServletRequest("GET", "/login/oauth2/code/google"));
        assertPassed(new MockHttpServletRequest("GET", "/oauth2/authorization/discord"));
        assertPassed(new MockHttpServletRequest("GET", "/api/auth/verify"));
        assertPassed(new MockHttpServletRequest("OPTIONS", "/api/auth/refresh"));
        assertPassed(new MockHttpServletRequest("PATCH", "/api/users/me"));
    }

    @Test void contextPathDoesNotBypassCookieApiPolicy() throws Exception {
        var request = new MockHttpServletRequest("POST", "/backend/api/auth/logout");request.setContextPath("/backend");
        assertBlocked(request);
    }

    private MockHttpServletRequest trusted(String path) {
        var request = new MockHttpServletRequest("POST", path);request.addHeader("Origin", "https://app.example.test");return request;
    }
    private void assertBlocked(MockHttpServletRequest request) throws Exception {
        var response = new MockHttpServletResponse();var called = new AtomicBoolean();
        new BrowserOriginFilter(policy).doFilter(request, response, (req, res) -> called.set(true));
        assertFalse(called.get());assertEquals(403, response.getStatus());assertEquals("no-store", response.getHeader("Cache-Control"));
        assertNull(response.getHeader("Set-Cookie"));assertNull(request.getSession(false));
    }
    private void assertPassed(MockHttpServletRequest request) throws Exception {
        var called = new AtomicBoolean();new BrowserOriginFilter(policy).doFilter(request, new MockHttpServletResponse(), (req, res) -> called.set(true));
        assertTrue(called.get());
    }
}
