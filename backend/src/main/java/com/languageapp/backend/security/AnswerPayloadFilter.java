package com.languageapp.backend.security;

import com.languageapp.backend.exception.dto.ErrorResponse;
import com.languageapp.backend.validation.AnswerLimits;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.regex.Pattern;

/** Bounds authenticated answer JSON before deserialization, including bodies without Content-Length. */
public final class AnswerPayloadFilter extends OncePerRequestFilter {
    private static final Pattern CHECK = Pattern.compile("/api/(?:exercises/[^/]+/check|lessons/[^/]+/attempts/[^/]+/exercises/[^/]+/check|vocabulary/(?:lookup|[^/]+/practice)|assignments/sessions/[^/]+/grade)");
    private static final Pattern BATCH = Pattern.compile("/api/(?:lessons/[^/]+/submit|assignments/sessions/[^/]+/(?:submit|draft)|vocabulary/(?:known-batch|practice/submit)|admin/curriculum/(?:import|topic/[^/]+/lesson/import|lesson/[^/]+/exercise/import))");
    private final ObjectMapper mapper;
    private final Clock clock;
    public AnswerPayloadFilter(ObjectMapper mapper, Clock clock) { this.mapper = mapper; this.clock = clock; }

    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = path(request);
        return !(("POST".equals(request.getMethod()) && (CHECK.matcher(path).matches()
                || BATCH.matcher(path).matches()))
                || ("PUT".equals(request.getMethod()) && BATCH.matcher(path).matches()));
    }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        int maximum = CHECK.matcher(path(request)).matches() ? AnswerLimits.MAX_CHECK_BYTES : AnswerLimits.MAX_BATCH_BYTES;
        if (request.getContentLengthLong() > maximum) { tooLarge(request, response); return; }
        byte[] body = request.getInputStream().readNBytes(maximum + 1);
        if (body.length > maximum) { tooLarge(request, response); return; }
        chain.doFilter(new HttpServletRequestWrapper(request) {
            @Override public ServletInputStream getInputStream() {
                var input = new ByteArrayInputStream(body);
                return new ServletInputStream() {
                    @Override public int read() { return input.read(); }
                    @Override public int read(byte[] bytes, int offset, int length) { return input.read(bytes, offset, length); }
                    @Override public boolean isFinished() { return input.available() == 0; }
                    @Override public boolean isReady() { return true; }
                    @Override public void setReadListener(ReadListener listener) {
                        try { if (!isFinished()) listener.onDataAvailable(); if (isFinished()) listener.onAllDataRead(); }
                        catch (IOException error) { listener.onError(error); }
                    }
                };
            }
            @Override public BufferedReader getReader() {
                return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
            }
        }, response);
    }
    private void tooLarge(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setStatus(413);
        response.setContentType("application/json;charset=UTF-8");
        mapper.writeValue(response.getWriter(), new ErrorResponse(clock.instant(), 413, "Payload Too Large",
                "A beküldött válaszok összmérete túl nagy. Rövidítsd a választ, majd próbáld újra.", request.getRequestURI()));
    }
    private String path(HttpServletRequest request) { return request.getRequestURI().substring(request.getContextPath().length()); }
}
