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
import org.mockito.Spy;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AchievementServiceTest {
    @Mock private MasteryService masteryService;
    @Mock private com.languageapp.backend.repository.UserRepository userRepository;
    @Spy private Clock clock = Clock.fixed(Instant.parse("2026-10-16T16:00:00Z"), ZoneOffset.UTC);

    @Mock
    private AchievementRepository achievementRepository;
    @Mock
    private UserAchievementRepository userAchievementRepository;
    @Mock
    private ProgressRepository progressRepository;
    @Mock
    private com.languageapp.backend.repository.FriendshipRepository friendshipRepository;
    @Mock private SseService sseService;
    @Mock private com.languageapp.backend.repository.StudentVocabularyRepository vocabularyRepository;
    @Mock
    private ChallengeRepository challengeRepository;

    @InjectMocks
    private AchievementService achievementService;

    private User testUser;
    private Achievement perfectScoreAch;
    private Achievement socialFriendsAch;

    @Test void masteryTrophyAcceptsAnImperfectSuccessfulCompletion() {
        perfectScoreAch.setCriteria(Map.of("type", "LESSON_MASTERY", "target", 1));
        when(achievementRepository.findAll()).thenReturn(List.of(perfectScoreAch));
        when(masteryService.countMasteredBoth(testUser)).thenReturn(new MasteryService.MasteryCounts(1,0));
        achievementService.checkAndAwardAchievements(testUser, 67);
        verify(userAchievementRepository).save(any());
    }

    @Test void historicalMasteryIsReconciledWhenViewingAchievements() {
        perfectScoreAch.setCriteria(Map.of("type", "TOPIC_MASTERY", "target", 1));
        when(userRepository.findForLearningUpdate(testUser.getUserId())).thenReturn(java.util.Optional.of(testUser));
        when(achievementRepository.findAll()).thenReturn(List.of(perfectScoreAch));
        when(masteryService.countMasteredBoth(testUser)).thenReturn(new MasteryService.MasteryCounts(0,1));
        achievementService.getUserAchievements(testUser.getUserId());
        verify(userAchievementRepository).save(any());
    }

    @Test void suspendedTrophiesCannotBeAwarded() {
        perfectScoreAch.setActive(false);
        when(achievementRepository.findAll()).thenReturn(List.of(perfectScoreAch));
        achievementService.checkAndAwardAchievements(testUser, 100);
        verify(userAchievementRepository, never()).save(any());
    }

    @Test void completingThreeLevelsOfOneLessonDoesNotCountAsThreeLessons() {
        perfectScoreAch.setCriteria(Map.of("type", "LESSON_COUNT", "target", 2));
        when(achievementRepository.findAll()).thenReturn(List.of(perfectScoreAch));
        when(progressRepository.countCompletedLessons(testUser.getUserId())).thenReturn(1L);
        achievementService.checkAndAwardAchievements(testUser, 100);
        verify(userAchievementRepository, never()).save(any());
    }

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setUserId(UUID.randomUUID());
        testUser.setEmail("diak@test.com");
        lenient().when(masteryService.countMasteredBoth(any())).thenReturn(new MasteryService.MasteryCounts(0,0));
        testUser.setStreak(5); // A streak teszteléséhez
        lenient().when(userRepository.findForLearningUpdate(testUser.getUserId())).thenReturn(java.util.Optional.of(testUser));

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

        // Act: A diák befejez egy leckét 100%-os eredménnyel
        achievementService.checkAndAwardAchievements(testUser, 100);

        // Assert: A rendszer elmenti az új trófeát
        verify(userAchievementRepository, times(1)).save(any());
    }

    @Test
    void testCheckAndAwardAchievements_ImperfectScore_IgnoresTrophy() {
        // Arrange
        when(achievementRepository.findAll()).thenReturn(List.of(perfectScoreAch));

        // Act: A diák 99%-os eredménnyel végez
        achievementService.checkAndAwardAchievements(testUser, 99);

        // Assert: A rendszer NEM adja meg a trófeát
        verify(userAchievementRepository, never()).save(any());
    }

    @Test
    void testCheckAndAwardAchievements_SocialFriendsMet_AwardsTrophy() {
        // Arrange
        when(achievementRepository.findAll()).thenReturn(List.of(socialFriendsAch));

        when(friendshipRepository.countFriendships(testUser.getUserId(), com.languageapp.backend.enums.FriendshipStatus.ACCEPTED)).thenReturn(5L);

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
        when(userAchievementRepository.findOwnedAchievementIds(testUser.getUserId())).thenReturn(List.of(perfectScoreAch.getAchievementId()));

        // Act
        achievementService.checkAndAwardAchievements(testUser, 100);

        // Assert: A rendszer azonnal kilép (continue), a save() nem hívódik meg
        verify(userAchievementRepository, never()).save(any());
    }
    @Test void awardCarriesStableIdentityAndOnlyNotifiesAfterCommit() {
        when(achievementRepository.findAll()).thenReturn(List.of(perfectScoreAch));
        UUID awardId = UUID.randomUUID();
        when(userAchievementRepository.save(any())).thenAnswer(invocation -> {
            com.languageapp.backend.entity.UserAchievement award = invocation.getArgument(0);
            award.setUserAchievementId(awardId); return award;
        });
        org.springframework.transaction.support.TransactionSynchronizationManager.initSynchronization();
        try {
            var awards = achievementService.checkAndAwardAchievements(testUser, 100);
            org.junit.jupiter.api.Assertions.assertEquals(awardId, awards.getFirst().userAchievementId());
            verifyNoInteractions(sseService);
            var callbacks = org.springframework.transaction.support.TransactionSynchronizationManager.getSynchronizations();
            callbacks.forEach(org.springframework.transaction.support.TransactionSynchronization::afterCommit);
            verify(sseService).sendPing(testUser.getEmail());
        } finally { org.springframework.transaction.support.TransactionSynchronizationManager.clearSynchronization(); }
    }

    @Test void presentationDoesNotMarkReadAndForeignIdsAreIgnored() {
        var award = new com.languageapp.backend.entity.UserAchievement();
        award.setUserAchievementId(UUID.randomUUID()); award.setFeedbackEnabled(true);
        when(userAchievementRepository.findByUserUserId(testUser.getUserId())).thenReturn(List.of(award));
        achievementService.acknowledgeFeedback(testUser.getUserId(), List.of(UUID.randomUUID()), true);
        org.junit.jupiter.api.Assertions.assertNull(award.getPresentedAt());
        achievementService.acknowledgeFeedback(testUser.getUserId(), List.of(award.getUserAchievementId()), false);
        org.junit.jupiter.api.Assertions.assertEquals(clock.instant(), award.getPresentedAt());
        org.junit.jupiter.api.Assertions.assertNull(award.getReadAt());
        achievementService.acknowledgeFeedback(testUser.getUserId(), List.of(award.getUserAchievementId()), true);
        org.junit.jupiter.api.Assertions.assertEquals(clock.instant(), award.getReadAt());
    }

    @Test void preFeatureAwardsAreNotNewOrAcknowledged() {
        var award = new com.languageapp.backend.entity.UserAchievement();
        award.setUserAchievementId(UUID.randomUUID()); award.setAchievement(perfectScoreAch);
        when(userAchievementRepository.findByUserUserId(testUser.getUserId())).thenReturn(List.of(award));
        when(achievementRepository.findAll()).thenReturn(List.of(perfectScoreAch));
        org.junit.jupiter.api.Assertions.assertFalse(achievementService.getUserAchievements(testUser.getUserId()).getFirst().isUnread());
        achievementService.acknowledgeFeedback(testUser.getUserId(), List.of(award.getUserAchievementId()), true);
        org.junit.jupiter.api.Assertions.assertNull(award.getReadAt());
    }


    private Achievement trophy(String type, int target) {
        Achievement a = new Achievement(); a.setAchievementId(UUID.randomUUID());
        a.setName(type + target); a.setCriteria(Map.of("type", type, "target", target)); return a;
    }

    @Test void metricsAreLazyAndEvaluatedOnlyOnceForAllThresholds() {
        var trophies = new java.util.ArrayList<Achievement>();
        for (String type : List.of("FIRST_LESSON", "LESSON_COUNT", "SOCIAL_FRIENDS", "SOCIAL_DUELS", "VOCAB_COUNT", "MASTERED_VOCAB_COUNT", "LESSON_MASTERY", "TOPIC_MASTERY")) {
            trophies.add(trophy(type, 50)); trophies.add(trophy(type, 100));
        }
        when(achievementRepository.findAll()).thenReturn(trophies);
        achievementService.checkAndAwardAchievements(testUser, -1);
        verify(userAchievementRepository).findOwnedAchievementIds(testUser.getUserId());
        verify(progressRepository).countCompletedLessons(testUser.getUserId());
        verify(friendshipRepository).countFriendships(testUser.getUserId(), com.languageapp.backend.enums.FriendshipStatus.ACCEPTED);
        verify(challengeRepository).countWins(testUser.getUserId(), com.languageapp.backend.enums.ChallengeStatus.COMPLETED);
        verify(vocabularyRepository).countByUser_UserId(testUser.getUserId());
        verify(vocabularyRepository).countByUser_UserIdAndSrsLevelGreaterThanEqual(testUser.getUserId(), 6);
        verify(masteryService).countMasteredBoth(testUser);
        verifyNoMoreInteractions(progressRepository, friendshipRepository, challengeRepository, vocabularyRepository, masteryService);
    }

    @Test void localOnlyRulesNeverLoadHistoricalData() {
        when(achievementRepository.findAll()).thenReturn(List.of(trophy("XP_MILESTONE", 500), trophy("STREAK_DAYS", 10), trophy("PERFECT_SCORE", 1)));
        achievementService.checkAndAwardAchievements(testUser, 99);
        verifyNoInteractions(progressRepository, friendshipRepository, challengeRepository, vocabularyRepository, masteryService);
    }

    @Test void metricsAreNotCachedAcrossRequestsOrLearners() {
        when(achievementRepository.findAll()).thenReturn(List.of(trophy("VOCAB_COUNT", 50)));
        when(vocabularyRepository.countByUser_UserId(testUser.getUserId())).thenReturn(49L, 50L);
        org.junit.jupiter.api.Assertions.assertTrue(achievementService.checkAndAwardAchievements(testUser, -1).isEmpty());
        org.junit.jupiter.api.Assertions.assertEquals(1, achievementService.checkAndAwardAchievements(testUser, -1).size());
        User other = new User(); other.setUserId(UUID.randomUUID()); other.setEmail("other@example.test");
        when(userRepository.findForLearningUpdate(other.getUserId())).thenReturn(java.util.Optional.of(other));
        org.junit.jupiter.api.Assertions.assertTrue(achievementService.checkAndAwardAchievements(other, -1).isEmpty());
        verify(vocabularyRepository, times(2)).countByUser_UserId(testUser.getUserId());
        verify(vocabularyRepository).countByUser_UserId(other.getUserId());
    }

    @Test void profileReconciliationReusesMetricsAndIncludesNewAwardsImmediately() {
        var first = trophy("LESSON_MASTERY", 1); var second = trophy("LESSON_MASTERY", 2);
        when(achievementRepository.findAll()).thenReturn(List.of(first, second));
        when(masteryService.countMasteredBoth(testUser)).thenReturn(new MasteryService.MasteryCounts(2,0));
        var response = achievementService.getUserAchievements(testUser.getUserId());
        org.junit.jupiter.api.Assertions.assertTrue(response.stream().allMatch(com.languageapp.backend.dto.response.AchievementResponse::isUnlocked));
        verify(masteryService).countMasteredBoth(testUser);
        verify(userAchievementRepository).findByUserUserId(testUser.getUserId());
        verify(userAchievementRepository, never()).existsByUserUserIdAndAchievementAchievementId(any(), any());
        verifyNoInteractions(progressRepository);
    }

    @Test void malformedOrUnknownRulesCannotPreventValidRulesFromAwarding() {
        var invalid = trophy("VOCAB_COUNT", 5); invalid.setCriteria(Map.of("type", "VOCAB_COUNT", "target", "not-a-number"));
        when(achievementRepository.findAll()).thenReturn(List.of(invalid, trophy("UNKNOWN", 1), trophy("PERFECT_SCORE", 1)));
        org.junit.jupiter.api.Assertions.assertEquals(1, achievementService.checkAndAwardAchievements(testUser, 100).size());
        verifyNoInteractions(vocabularyRepository, progressRepository);
    }

}
