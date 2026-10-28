package com.languageapp.backend.service;

import com.languageapp.backend.security.ApiRequestLimiter;
import com.languageapp.backend.security.RequestOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Shared account/action quotas; IP admission is enforced by the API filter. */
@Component @RequiredArgsConstructor
public class InvitationRequestLimiter {
    private final ApiRequestLimiter limiter;
    public void check(String subject,String action) {
        limiter.account(switch(action) {
            case "preview" -> RequestOperation.INVITATION_PREVIEW;
            case "confirm" -> RequestOperation.INVITATION_CONFIRM;
            default -> RequestOperation.INVITATION_MANAGE;
        },subject);
    }
}
