package com.languageapp.backend.scheduler;

import com.languageapp.backend.security.RequestBudgetStore;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component @RequiredArgsConstructor
@ConditionalOnProperty(name="app.rate-limit.cleanup-enabled",havingValue="true",matchIfMissing=true)
public class RequestBudgetCleanup {
    private final RequestBudgetStore budgets;
    @Scheduled(fixedDelayString="${app.rate-limit.cleanup-ms:60000}",initialDelayString="${app.rate-limit.cleanup-ms:60000}")
    public void clean() { budgets.prune(); }
}
