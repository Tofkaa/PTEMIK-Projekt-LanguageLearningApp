package com.languageapp.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class NotificationSummaryDTO {
    private int pendingFriendRequests;
    private int pendingChallenges;
    private int totalAcceptedFriends;
    private int totalHistoryItems;
    // A jövőben ide jöhet: private int newClassroomInvites;

    public int getTotal() {
        return pendingFriendRequests + pendingChallenges; // + newClassroomInvites
    }
}