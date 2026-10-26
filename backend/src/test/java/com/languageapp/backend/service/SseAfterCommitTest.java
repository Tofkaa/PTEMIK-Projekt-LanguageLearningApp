package com.languageapp.backend.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.mockito.Mockito.*;

class SseAfterCommitTest {
    private final SseService sse = spy(new SseService());
    @AfterEach void cleanup() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) TransactionSynchronizationManager.clearSynchronization();
        sse.shutdown();
    }
    @Test void updatePingWaitsUntilTheRewardIsCommitted() {
        TransactionSynchronizationManager.initSynchronization();
        sse.sendPingAfterCommit("winner@example.test");
        verify(sse, never()).sendPing(anyString());
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        verify(sse).sendPing("winner@example.test");
    }
    @Test void rollbackDoesNotRefreshClientsWithAnUncommittedReward() {
        TransactionSynchronizationManager.initSynchronization();
        sse.sendPingAfterCommit("winner@example.test");
        TransactionSynchronizationManager.getSynchronizations().forEach(callback -> callback.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        verify(sse, never()).sendPing(anyString());
    }
}
