package com.languageapp.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@AllArgsConstructor
public class FriendRequestDTO {
    private UUID friendshipId; // Ez kell majd a kérelem elfogadásához/elutasításához
    private String senderName;
    private String senderTag;
    private LocalDateTime sentAt;
}