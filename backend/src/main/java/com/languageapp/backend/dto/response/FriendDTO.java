package com.languageapp.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.util.UUID;

@Data
@AllArgsConstructor
public class FriendDTO {

    /** The unique identifier of the friendship connection (used for deleting the connection). */
    private UUID friendshipId;

    /** The unique user ID of the friend (required for sending challenges). */
    private UUID friendId;

    private String name;
    private String userTag;

    /** The friend's globally unique, shareable code. */
    private String friendCode;
}