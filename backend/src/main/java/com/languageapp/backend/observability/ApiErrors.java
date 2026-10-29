package com.languageapp.backend.observability;

import com.languageapp.backend.exception.dto.ErrorResponse;
import jakarta.servlet.http.*;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.json.JsonMapper;
import java.io.IOException;
import java.time.Instant;

/** Security errors follow the controller error schema, including the outer filter's request ID. */
public final class ApiErrors {
    private static final JsonMapper JSON=JsonMapper.builder().build();
    private ApiErrors() { }
    public static void write(HttpServletRequest request,HttpServletResponse response,int status,String message)throws IOException {
        response.setStatus(status);response.setContentType("application/json;charset=UTF-8");response.setHeader("Cache-Control","no-store");
        response.getWriter().write(JSON.writeValueAsString(new ErrorResponse(Instant.now(),status,HttpStatus.valueOf(status).getReasonPhrase(),message,request.getRequestURI())));
    }
}
