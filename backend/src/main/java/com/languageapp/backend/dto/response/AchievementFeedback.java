package com.languageapp.backend.dto.response;

import java.util.UUID;

/** Stable award identity, independent of the trigger and delivery channel. */
public record AchievementFeedback(UUID userAchievementId, String name, String description,
                                  String iconUrl, String achievedAt) { }
