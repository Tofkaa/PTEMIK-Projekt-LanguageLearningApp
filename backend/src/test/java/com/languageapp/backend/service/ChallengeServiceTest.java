package com.languageapp.backend.service;

import com.languageapp.backend.dto.request.ChallengeCreateRequest;
import com.languageapp.backend.dto.response.ChallengeCreateResponse;
import com.languageapp.backend.entity.Challenge;
import com.languageapp.backend.entity.Lesson;
import com.languageapp.backend.entity.Result;
import com.languageapp.backend.entity.User;
import com.languageapp.backend.enums.ChallengeStatus;
import com.languageapp.backend.exception.BadRequestException;
import com.languageapp.backend.repository.ChallengeRepository;
import com.languageapp.backend.repository.LessonRepository;
import com.languageapp.backend.repository.ResultRepository;
import com.languageapp.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChallengeServiceTest {

    @Mock
    private ChallengeRepository challengeRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private LessonRepository lessonRepository;
    @Mock
    private FriendshipService friendshipService;
    @Mock
    private ResultRepository resultRepository;
    @Mock
    private SseService sseService;

    @InjectMocks
    private ChallengeService challengeService;

    private User challenger;
    private User opponent;
    private Lesson lesson;
    private Challenge activeChallenge;

    @BeforeEach
    void setUp() {
        challenger = new User();
        challenger.setUserId(UUID.randomUUID());
        challenger.setName("Challenger Player");
        challenger.setEmail("challenger@test.com");
        challenger.setXp(100);

        opponent = new User();
        opponent.setUserId(UUID.randomUUID());
        opponent.setName("Opponent Player");
        opponent.setEmail("opponent@test.com");
        opponent.setXp(100);

        lesson = new Lesson();
        lesson.setLessonId(UUID.randomUUID());
        lesson.setTitle("Test Duel Lesson");

        activeChallenge = new Challenge();
        activeChallenge.setChallengeId(UUID.randomUUID());
        activeChallenge.setChallenger(challenger);
        activeChallenge.setOpponent(opponent);
        activeChallenge.setLesson(lesson);
        activeChallenge.setStatus(ChallengeStatus.DRAFT);
    }

    // --- 1. TESZT: Új kihívás létrehozása sikeresen ---
    @Test
    void createDraftChallenge_ValidFriends_CreatesChallengeSuccessfully() {
        // Arrange
        ChallengeCreateRequest request = new ChallengeCreateRequest();
        request.setOpponentId(opponent.getUserId());
        request.setLessonId(lesson.getLessonId());
        request.setExpiresInDays(3);

        when(userRepository.findById(challenger.getUserId())).thenReturn(Optional.of(challenger));
        when(userRepository.findById(opponent.getUserId())).thenReturn(Optional.of(opponent));
        when(lessonRepository.findById(lesson.getLessonId())).thenReturn(Optional.of(lesson));

        // Mockoljuk, hogy barátok (visszaadunk egy DTO-t, aminek a barát ID-ja az opponent ID-ja)
        com.languageapp.backend.dto.response.FriendDTO mockFriend = new com.languageapp.backend.dto.response.FriendDTO(
                UUID.randomUUID(), opponent.getUserId(), "Opponent", "1234", "XXX");
        when(friendshipService.getMyFriends(challenger.getUserId())).thenReturn(List.of(mockFriend));

        when(challengeRepository.save(any(Challenge.class))).thenAnswer(i -> i.getArguments()[0]);

        // Act
        ChallengeCreateResponse response = challengeService.createDraftChallenge(challenger.getUserId(), request);

        // Assert
        assertNotNull(response);
        assertEquals("DRAFT", response.getStatus());

        ArgumentCaptor<Challenge> challengeCaptor = ArgumentCaptor.forClass(Challenge.class);
        verify(challengeRepository, times(1)).save(challengeCaptor.capture());
        Challenge savedChallenge = challengeCaptor.getValue();

        assertEquals(challenger, savedChallenge.getChallenger());
        assertEquals(opponent, savedChallenge.getOpponent());
        assertEquals(ChallengeStatus.DRAFT, savedChallenge.getStatus());
    }

    // --- 2. TESZT: Új kihívás létrehozása idegennel (Kivételt kell dobnia) ---
    @Test
    void createDraftChallenge_NotFriends_ThrowsException() {
        // Arrange
        ChallengeCreateRequest request = new ChallengeCreateRequest();
        request.setOpponentId(opponent.getUserId());
        request.setLessonId(lesson.getLessonId());
        request.setExpiresInDays(3);

        when(userRepository.findById(challenger.getUserId())).thenReturn(Optional.of(challenger));
        when(userRepository.findById(opponent.getUserId())).thenReturn(Optional.of(opponent));
        when(lessonRepository.findById(lesson.getLessonId())).thenReturn(Optional.of(lesson));

        // Üres barátlista mockolása
        when(friendshipService.getMyFriends(challenger.getUserId())).thenReturn(List.of());

        // Act & Assert
        BadRequestException exception = assertThrows(BadRequestException.class, () -> {
            challengeService.createDraftChallenge(challenger.getUserId(), request);
        });

        assertEquals("Csak az elfogadott barátaidat hívhatod ki!", exception.getMessage());
        verify(challengeRepository, never()).save(any());
    }

    // --- 3. TESZT: Az első játékos befejezi a leckét (Átmenet DRAFT -> PENDING) ---
    @Test
    void processChallengeResult_ChallengerFinishes_SetsStatusToPending() {
        // Arrange
        when(challengeRepository.findById(activeChallenge.getChallengeId())).thenReturn(Optional.of(activeChallenge));

        // Act
        challengeService.processChallengeResult(activeChallenge.getChallengeId(), challenger.getUserId(), 80, 120);

        // Assert
        assertEquals(ChallengeStatus.PENDING, activeChallenge.getStatus());
        verify(challengeRepository, times(1)).save(activeChallenge);
        // Ellenőrizzük az értesítés küldését az ellenfélnek
        verify(sseService, times(1)).sendPing(opponent.getEmail());
    }

    // --- 4. TESZT: Értékelő motor - A második játékos jobb pontszámot ér el ---
    @Test
    void processChallengeResult_OpponentFinishesWithHigherScore_OpponentWins() {
        // Arrange
        activeChallenge.setStatus(ChallengeStatus.PENDING); // A kihívó már lejátszotta
        when(challengeRepository.findById(activeChallenge.getChallengeId())).thenReturn(Optional.of(activeChallenge));

        // A kihívó korábbi eredménye: 80 pont
        Result challengerResult = new Result();
        challengerResult.setScore(80);
        challengerResult.setTimeTaken(120);
        when(resultRepository.findByChallengeChallengeIdAndUserUserId(activeChallenge.getChallengeId(), challenger.getUserId()))
                .thenReturn(Optional.of(challengerResult));

        // Act: Az ellenfél 90 pontot ér el
        challengeService.processChallengeResult(activeChallenge.getChallengeId(), opponent.getUserId(), 90, 150);

        // Assert
        assertEquals(ChallengeStatus.COMPLETED, activeChallenge.getStatus());
        assertEquals(opponent, activeChallenge.getWinner()); // Az ellenfél nyert

        // Ellenőrizzük, hogy a győztes kapott-e 50 XP-t
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository, times(1)).save(userCaptor.capture());
        assertEquals(150, userCaptor.getValue().getXp()); // 100 volt az alap + 50 jutalom
    }

    // --- 5. TESZT: Értékelő motor - Tie-Breaker (Azonos pontszám, gyorsabb idő nyer) ---
    @Test
    void processChallengeResult_SameScoreFasterTime_ChallengerWins() {
        // Arrange
        activeChallenge.setStatus(ChallengeStatus.PENDING);
        when(challengeRepository.findById(activeChallenge.getChallengeId())).thenReturn(Optional.of(activeChallenge));

        // A kihívó eredménye: 100 pont, 60 másodperc alatt
        Result challengerResult = new Result();
        challengerResult.setScore(100);
        challengerResult.setTimeTaken(60);
        when(resultRepository.findByChallengeChallengeIdAndUserUserId(activeChallenge.getChallengeId(), challenger.getUserId()))
                .thenReturn(Optional.of(challengerResult));

        // Act: Az ellenfél is 100 pontot ér el, de lassabban (90 másodperc)
        challengeService.processChallengeResult(activeChallenge.getChallengeId(), opponent.getUserId(), 100, 90);

        // Assert
        assertEquals(ChallengeStatus.COMPLETED, activeChallenge.getStatus());
        assertEquals(challenger, activeChallenge.getWinner(), "Azonos pontszám esetén a gyorsabb játékosnak kell nyernie!");
        verify(userRepository, times(1)).save(challenger); // A kihívó kapja az XP-t
    }
}