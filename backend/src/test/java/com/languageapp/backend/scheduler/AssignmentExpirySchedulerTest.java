package com.languageapp.backend.scheduler;

import com.languageapp.backend.repository.AssignmentSessionRepository;
import com.languageapp.backend.service.AssignmentService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.*;
import java.util.*;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AssignmentExpirySchedulerTest {
    @Mock AssignmentSessionRepository sessions;
    @Mock AssignmentService assignments;
    @Mock Clock clock;
    AssignmentExpiryScheduler scheduler;
    Instant now=Instant.parse("2026-10-27T12:00:00Z");
    @BeforeEach void setup() {scheduler=new AssignmentExpiryScheduler(sessions,assignments,clock);when(clock.instant()).thenReturn(now);}
    @Test void noDueCandidatesNeverLocksOrLoadsAnAttempt() {
        scheduler.closeExpired();verify(sessions).findExpiredCandidateIds(now,null,100);verifyNoInteractions(assignments);
    }
    @Test void aFailedAttemptDoesNotSkipLaterCandidates() {
        UUID bad=new UUID(0,1),good=new UUID(0,2);
        when(sessions.findExpiredCandidateIds(now,null,100)).thenReturn(List.of(bad,good));
        doThrow(new IllegalStateException("private draft must not be logged")).when(assignments).closeExpiredSession(bad);
        scheduler.closeExpired();verify(assignments).closeExpiredSession(good);
        verify(sessions,times(1)).findExpiredCandidateIds(any(),any(),anyInt());
    }
    @Test void keysetPagingUsesOneCutoffAndResetsAfterAnIncompleteBatch() {
        List<UUID> first=IntStream.range(0,100).mapToObj(i -> new UUID(0,i)).toList();UUID last=new UUID(0,100);
        when(sessions.findExpiredCandidateIds(now,null,100)).thenReturn(first,List.of());
        when(sessions.findExpiredCandidateIds(now,first.getLast(),100)).thenReturn(List.of(last));
        scheduler.closeExpired();scheduler.closeExpired();
        verify(assignments,times(101)).closeExpiredSession(any());verify(clock,times(2)).instant();
        verify(sessions,times(2)).findExpiredCandidateIds(now,null,100);
    }
    @Test void scanBudgetRetainsItsPositionAcrossTicksEvenWhenAnEarlyCandidateKeepsFailing() {
        for(int page=0;page<10;page++) {
            UUID after=page==0?null:new UUID(0,page*100-1);
            List<UUID> ids=IntStream.range(page*100,(page+1)*100).mapToObj(i -> new UUID(0,i)).toList();
            when(sessions.findExpiredCandidateIds(now,after,100)).thenReturn(ids);
        }
        doThrow(new IllegalStateException("invalid stored allocation")).when(assignments).closeExpiredSession(new UUID(0,0));
        scheduler.closeExpired();verify(assignments,times(1000)).closeExpiredSession(any());
        UUID later=new UUID(0,1000);when(sessions.findExpiredCandidateIds(now,new UUID(0,999),100)).thenReturn(List.of(later));
        scheduler.closeExpired();verify(assignments).closeExpiredSession(later);
        verify(sessions,times(11)).findExpiredCandidateIds(any(),any(),eq(100));
    }
}
