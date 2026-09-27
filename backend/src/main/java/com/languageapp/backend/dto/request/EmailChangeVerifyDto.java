package com.languageapp.backend.dto.request;

import lombok.Data;

@Data
public class EmailChangeVerifyDto {
    private String otpCode;
}