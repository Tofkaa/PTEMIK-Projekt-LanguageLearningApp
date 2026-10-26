package com.languageapp.backend.service;
import java.util.List;
import java.util.UUID;
public record SocialAchievementEvent(List<UUID> userIds) { }
