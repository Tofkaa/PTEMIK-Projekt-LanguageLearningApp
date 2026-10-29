package com.languageapp.backend.exception.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.time.Instant;

@Data
@AllArgsConstructor
public class ErrorResponse {
    private Instant timestamp;
    private int status;
    private String error;
    private String message;
    private String path;
    private String requestId;
    public ErrorResponse(Instant timestamp,int status,String error,String message,String path) {
        this(timestamp,status,error,message,path,com.languageapp.backend.observability.RequestDiagnostics.currentId());
    }
}
