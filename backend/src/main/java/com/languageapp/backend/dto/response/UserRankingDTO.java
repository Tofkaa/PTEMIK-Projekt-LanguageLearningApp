package com.languageapp.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserRankingDTO {
    private UUID userId;
    private String name;
    private String userTag;
    private int xp;
    private int streak;
    private int rank;
}