package com.languageapp.backend.scheduler;

import com.languageapp.backend.service.ExpiredTokenCleanup;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** At most 600 removals/tick; independent replicas may safely skip each other's claimed rows. */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "app.security.token-cleanup.enabled", havingValue = "true", matchIfMissing = true)
public class ExpiredTokenScheduler {
    @org.springframework.beans.factory.annotation.Autowired(required=false)
    private com.languageapp.backend.observability.OperationsMetrics operations;

    private final ExpiredTokenCleanup cleanup;

    @Scheduled(fixedDelayString = "${app.security.token-cleanup.interval-ms:300000}",
            initialDelayString = "${app.security.token-cleanup.initial-delay-ms:300000}")
    public synchronized void cleanExpired() {
        var work=operations==null?null:operations.begin(com.languageapp.backend.observability.OperationsMetrics.Operation.CREDENTIAL_CLEANUP);
        boolean successful=true;
        try {
        for (var kind : ExpiredTokenCleanup.Kind.values()) {
            try {
                int removed = cleanup.purge(kind);
                if (removed > 0) log.debug("job.credentials.cleaned");
            } catch (RuntimeException failure) {
                successful=false;if(operations!=null)operations.jobItemFailed();
                // No token/user/email or raw database exception text in diagnostics; continue other tables.
                log.warn("job.credentials.failed");
            }
        }
        }catch(RuntimeException failure){successful=false;throw failure;}
        finally{if(work!=null)operations.finishJob(work,successful);}

    }
}
