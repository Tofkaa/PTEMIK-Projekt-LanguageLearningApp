package com.languageapp.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResetPasswordRequest {

    @NotBlank(message = "A token nem lehet üres.")
    private String token;

    @NotBlank(message = "Az új jelszó nem lehet üres.")
    @Size(min = 6, message = "A jelszónak legalább 6 karakternek kell lennie.")
    private String newPassword;
}