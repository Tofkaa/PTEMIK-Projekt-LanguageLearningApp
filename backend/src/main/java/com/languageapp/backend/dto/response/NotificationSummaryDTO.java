package com.languageapp.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class NotificationSummaryDTO {
    private int pendingFriendRequests;
    private int pendingChallenges;
    private int totalAcceptedFriends;
    private int totalHistoryItems;

    private int teacherPendingJoinRequests;
    private int teacherUngradedSubmissions;


    private List<String> studentActiveAssignmentIds;
    private List<String> studentGradedSessionIds;

    private long lastPingTime;

    public int getTotal() {
        return pendingFriendRequests + pendingChallenges + teacherPendingJoinRequests + teacherUngradedSubmissions;
    }
}