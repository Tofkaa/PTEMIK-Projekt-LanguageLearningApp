package com.languageapp.backend.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Schedule external email only after its data commits. This is not a durable delivery queue. */
@Slf4j
public final class CommittedEmail {
    private CommittedEmail() { }

    public static void send(Runnable dispatch) {
        Runnable safeDispatch = () -> {
            try { dispatch.run(); }
            catch (RuntimeException failure) {
                // A scheduling failure cannot roll back a committed change; never log addresses or usable tokens.
                log.warn("Committed email could not be scheduled.");
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { safeDispatch.run(); }
            });
        } else {
            safeDispatch.run();
        }
    }
}
