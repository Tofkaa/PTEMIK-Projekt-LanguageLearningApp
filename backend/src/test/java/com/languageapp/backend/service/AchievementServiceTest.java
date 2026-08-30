package com.languageapp.backend.service;

import com.languageapp.backend.entity.Achievement;
import com.languageapp.backend.entity.User;
import com.languageapp.backend.repository.AchievementRepository;
import com.languageapp.backend.repository.ChallengeRepository;
import com.languageapp.backend.repository.ProgressRepository;
import com.languageapp.backend.repository.UserAchievementRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AchievementServiceTest {

    @Mock
    private AchievementRepository achievementRepository;
    @Mock
    private UserAchievementRepository userAchievementRepository;
    @Mock
    private ProgressRepository progressRepository;
    @Mock
    private FriendshipService friendshipService;
    @Mock
    private ChallengeRepository challengeRepository;

    @InjectMocks
    private AchievementService achievementService;

    private User testUser;
    private Achievement perfectScoreAch;
    private Achievement socialFriendsAch;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setUserId(UUID.randomUUID());
        testUser.setEmail("diak@test.com");
        testUser.setStreak(5); // A streak teszteléséhez

        // Tökéletes Teszt Kitüntetés (Meglévő logika)
        perfectScoreAch = new Achievement();
        perfectScoreAch.setAchievementId(UUID.randomUUID());
        perfectScoreAch.setName("Tökéletes Teszt");
        perfectScoreAch.setCriteria(Map.of("type", "PERFECT_SCORE"));

        // Közösségi Motor Kitüntetés (Az új szabály)
        socialFriendsAch = new Achievement();
        socialFriendsAch.setAchievementId(UUID.randomUUID());
        socialFriendsAch.setName("Közösségi Motor");
        socialFriendsAch.setCriteria(Map.of("type", "SOCIAL_FRIENDS", "target", 5));
    }

    @Test
    void testCheckAndAwardAchievements_PerfectScore_AwardsTrophy() {
        // Arrange: A rendszerben csak a Perfect Score kitüntetés létezik
        when(achievementRepository.findAll()).thenReturn(List.of(perfectScoreAch));
        // A diák még nem kapta meg ezt a kitüntetést
        when(userAchievementRepository.existsByUserUserIdAndAchievementAchievementId(
                testUser.getUserId(), perfectScoreAch.getAchievementId())).thenReturn(false);

        // Act: A diák befejez egy leckét 100%-os eredménnyel
        achievementService.checkAndAwardAchievements(testUser, 100);

        // Assert: A rendszer elmenti az új trófeát
        verify(userAchievementRepository, times(1)).save(any());
    }

    @Test
    void testCheckAndAwardAchievements_ImperfectScore_IgnoresTrophy() {
        // Arrange
        when(achievementRepository.findAll()).thenReturn(List.of(perfectScoreAch));
        when(userAchievementRepository.existsByUserUserIdAndAchievementAchievementId(
                testUser.getUserId(), perfectScoreAch.getAchievementId())).thenReturn(false);

        // Act: A diák 99%-os eredménnyel végez
        achievementService.checkAndAwardAchievements(testUser, 99);

        // Assert: A rendszer NEM adja meg a trófeát
        verify(userAchievementRepository, never()).save(any());
    }

    @Test
    void testCheckAndAwardAchievements_SocialFriendsMet_AwardsTrophy() {
        // Arrange
        when(achievementRepository.findAll()).thenReturn(List.of(socialFriendsAch));
        when(userAchievementRepository.existsByUserUserIdAndAchievementAchievementId(
                testUser.getUserId(), socialFriendsAch.getAchievementId())).thenReturn(false);

        // Létrehozunk egy Mock listát, ami 5-öt ad vissza a size() hívásra
        List mockFriendList = mock(List.class);
        when(mockFriendList.size()).thenReturn(5);

        // Ezt a kamu listát adjuk vissza a Service hívásakor
        when(friendshipService.getMyFriends(testUser.getUserId())).thenReturn(mockFriendList);

        // Act: A diák befejez egy leckét (a pontszám itt nem számít)
        achievementService.checkAndAwardAchievements(testUser, 50);

        // Assert: A barátok száma miatt megkapja a kitüntetést
        verify(userAchievementRepository, times(1)).save(any());
    }

    @Test
    void testCheckAndAwardAchievements_AlreadyOwned_SkipsEvaluation() {
        // Arrange
        when(achievementRepository.findAll()).thenReturn(List.of(perfectScoreAch));
        // A diák MÁR MEGKAPTA ezt a trófeát korábban
        when(userAchievementRepository.existsByUserUserIdAndAchievementAchievementId(
                testUser.getUserId(), perfectScoreAch.getAchievementId())).thenReturn(true);

        // Act
        achievementService.checkAndAwardAchievements(testUser, 100);

        // Assert: A rendszer azonnal kilép (continue), a save() nem hívódik meg
        verify(userAchievementRepository, never()).save(any());
    }
}