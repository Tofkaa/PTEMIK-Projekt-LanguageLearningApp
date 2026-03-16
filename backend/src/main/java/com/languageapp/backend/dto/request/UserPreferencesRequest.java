package com.languageapp.backend.dto.request;

import lombok.Data;

@Data
public class UserPreferencesRequest {
    private String preferredDifficulty;
}