package com.languageapp.backend.security;

import com.languageapp.backend.validation.AnswerLimits;
import jakarta.servlet.http.HttpServletRequestWrapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.json.JsonMapper;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;

class AnswerPayloadFilterTest {
    final AnswerPayloadFilter filter = new AnswerPayloadFilter(JsonMapper.builder().build(),Clock.systemUTC());
    @ParameterizedTest @ValueSource(strings={"/api/assignments/sessions/session/exercises/exercise/check","/api/exercises/id/check","/api/lessons/id/attempts/a/exercises/e/check",
            "/api/lessons/id/submit","/api/assignments/sessions/id/submit","/api/assignments/sessions/id/draft",
            "/api/vocabulary/lookup","/api/vocabulary/id/practice","/api/vocabulary/known-batch","/api/vocabulary/practice/submit"})
    void oversizedAnswerPathsAreRejectedBeforeTheController(String path) throws Exception {
        var request=new MockHttpServletRequest(path.endsWith("draft")?"PUT":"POST",path);
        request.setContent(new byte[AnswerLimits.MAX_BATCH_BYTES+1]);
        var response=new MockHttpServletResponse();var called=new AtomicBoolean();
        filter.doFilter(request,response,(req,res)->called.set(true));
        assertEquals(413,response.getStatus());assertFalse(called.get());
        assertTrue(response.getContentAsString(StandardCharsets.UTF_8).contains("összmérete túl nagy"));
    }
    @Test void unknownLengthBodiesAreCountedInsteadOfTrustingTheHeader() throws Exception {
        var original=new MockHttpServletRequest("POST","/api/exercises/id/check");
        original.setContent(new byte[AnswerLimits.MAX_CHECK_BYTES+1]);
        var request=new HttpServletRequestWrapper(original) {
            @Override public long getContentLengthLong() { return -1; }
            @Override public int getContentLength() { return -1; }
        };
        var response=new MockHttpServletResponse();var called=new AtomicBoolean();
        filter.doFilter(request,response,(req,res)->called.set(true));
        assertEquals(413,response.getStatus());assertFalse(called.get());
    }
    @Test void preservesTheExactUtf8BodyAtTheLimitAndUnderAContextPath() throws Exception {
        byte[] body=("á".repeat(AnswerLimits.MAX_CHECK_BYTES/2)).getBytes(StandardCharsets.UTF_8);
        var request=new MockHttpServletRequest("POST","/app/api/exercises/id/check");request.setContextPath("/app");request.setContent(body);
        var response=new MockHttpServletResponse();var called=new AtomicBoolean();
        filter.doFilter(request,response,(req,res)->{assertArrayEquals(body,req.getInputStream().readAllBytes());called.set(true);});
        assertTrue(called.get());assertEquals(200,response.getStatus());
    }
    @Test void doesNotApplyAnswerBoundsToImageUploads() throws Exception {
        var request=new MockHttpServletRequest("POST","/api/users/me/avatar");request.setContent(new byte[AnswerLimits.MAX_BATCH_BYTES+1]);
        var response=new MockHttpServletResponse();var called=new AtomicBoolean();
        filter.doFilter(request,response,(req,res)->called.set(true));
        assertTrue(called.get());assertEquals(200,response.getStatus());
    }
}
