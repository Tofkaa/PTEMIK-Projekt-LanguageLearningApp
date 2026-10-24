package com.languageapp.backend.dto.request;

import lombok.Getter;
import lombok.Setter;

/** Separate credential verification from confirmation of the destructive action. */
@Getter
@Setter
public class DeleteAccountRequest {
    private String password;
    private boolean confirmed;
}
