package com.languageapp.backend.scheduler;

import com.languageapp.backend.repository.AssignmentSessionRepository;
import com.languageapp.backend.service.AssignmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Finalization also runs when every student browser is closed. Each attempt locks independently. */
@Component
@RequiredArgsConstructor
@Slf4j
public class AssignmentExpiryScheduler {
    private final AssignmentSessionRepository sessions;
    private final AssignmentService assignments;

    @Scheduled(fixedDelayString = "${app.assignments.expiry-scan-ms:15000}")
    public void closeExpired() {
        for (var id : sessions.findRecoverableOngoingIds()) {
            try { assignments.closeExpiredSession(id); }
            catch (RuntimeException e) { log.warn("Could not finalize expired assignment session {}", id, e); }
        }
    }
}
