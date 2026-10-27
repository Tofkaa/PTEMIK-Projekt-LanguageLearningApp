package com.languageapp.backend.scheduler;

import com.languageapp.backend.repository.AssignmentSessionRepository;
import com.languageapp.backend.service.AssignmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.Clock;
import java.util.UUID;

/** Finalization also runs when every student browser is closed. Each attempt locks independently. */
@Component
@RequiredArgsConstructor
@Slf4j
public class AssignmentExpiryScheduler {
    private final AssignmentSessionRepository sessions;
    private final AssignmentService assignments;
    private final Clock clock;
    private static final int BATCH_SIZE = 100;
    private static final int MAX_BATCHES_PER_SCAN = 10;
    // Retain the scan position across ticks so repeated failures cannot starve later UUIDs.
    private UUID afterId;

    @Scheduled(fixedDelayString = "${app.assignments.expiry-scan-ms:15000}")
    public synchronized void closeExpired() {
        var cutoff = clock.instant();
        for (int batch = 0; batch < MAX_BATCHES_PER_SCAN; batch++) {
            var ids = sessions.findExpiredCandidateIds(cutoff, afterId, BATCH_SIZE);
            if (ids.isEmpty()) { afterId = null; return; }
            for (var id : ids) {
                // Selection is only a hint: the transactional consumer rechecks current timing under its lock.
                try { assignments.closeExpiredSession(id); }
                catch (RuntimeException failure) {
                    log.warn("Could not finalize expired assignment session {} ({})", id, failure.getClass().getSimpleName());
                }
            }
            afterId = ids.getLast();
            if (ids.size() < BATCH_SIZE) { afterId = null; return; }
        }
    }
}
