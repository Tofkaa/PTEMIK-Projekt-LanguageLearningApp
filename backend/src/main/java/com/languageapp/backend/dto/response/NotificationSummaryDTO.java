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

    private List<String> teacherPendingJoinRequestIds;
    private List<String> teacherUngradedSubmissionIds;

    private List<String> studentActiveAssignmentIds;
    private List<String> studentGradedSessionIds;

    private long lastPingTime;

    public int getTotal() {
        return pendingFriendRequests + pendingChallenges +
                (teacherPendingJoinRequestIds != null ? teacherPendingJoinRequestIds.size() : 0) +
                (teacherUngradedSubmissionIds != null ? teacherUngradedSubmissionIds.size() : 0);
    }
}