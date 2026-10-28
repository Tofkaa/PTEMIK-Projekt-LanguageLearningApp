package com.languageapp.backend.service;
import com.languageapp.backend.security.*;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
class InvitationRequestLimiterTest {
    @Test void delegatesEveryActionToTheSharedBudget() {
        var shared=mock(ApiRequestLimiter.class);var limiter=new InvitationRequestLimiter(shared);
        limiter.check("owner","manage");limiter.check("owner","preview");limiter.check("owner","confirm");
        verify(shared).account(RequestOperation.INVITATION_MANAGE,"owner");
        verify(shared).account(RequestOperation.INVITATION_PREVIEW,"owner");
        verify(shared).account(RequestOperation.INVITATION_CONFIRM,"owner");
    }
}
