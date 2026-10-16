package com.languageapp.backend.service;

import com.languageapp.backend.dto.request.ChallengeCreateRequest;
import com.languageapp.backend.dto.response.ChallengeCreateResponse;
import com.languageapp.backend.dto.response.ChallengeDTO;
import com.languageapp.backend.entity.Challenge;
import com.languageapp.backend.entity.Lesson;
import com.languageapp.backend.entity.Result;
import com.languageapp.backend.entity.User;
import com.languageapp.backend.enums.ChallengeStatus;
import com.languageapp.backend.exception.BadRequestException;
import com.languageapp.backend.exception.ResourceNotFoundException;
import com.languageapp.backend.repository.ChallengeRepository;
import com.languageapp.backend.repository.LessonRepository;
import com.languageapp.backend.repository.ResultRepository;
import com.languageapp.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.time.Clock;
import java.time.temporal.ChronoUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChallengeService {
    private final Clock clock;

    private final ChallengeRepository challengeRepository;
    private final UserRepository userRepository;
    private final LessonRepository lessonRepository;
    private final FriendshipService friendshipService;
    private final ResultRepository resultRepository;
    private final SseService sseService;
    private final UserDifficultyCalculator userDifficultyCalculator;

    @Transactional
    public ChallengeCreateResponse createDraftChallenge(UUID challengerId, ChallengeCreateRequest request) {
        User challenger = userRepository.findById(challengerId)
                .orElseThrow(() -> new ResourceNotFoundException("Kihívó nem található."));

        User opponent = userRepository.findById(request.getOpponentId())
                .orElseThrow(() -> new ResourceNotFoundException("Kihívott fél nem található."));

        Lesson lesson = lessonRepository.findById(request.getLessonId())
                .orElseThrow(() -> new ResourceNotFoundException("A kiválasztott lecke nem található."));

        boolean isFriend = friendshipService.getMyFriends(challengerId).stream()
                .anyMatch(friend -> friend.getFriendId().equals(opponent.getUserId()));

        if (!isFriend) {
            throw new BadRequestException("Csak az elfogadott barátaidat hívhatod ki!");
        }

        int days = request.getExpiresInDays();
        if (days < 1 || days > 7) {
            throw new BadRequestException("A lejárati időnek 1 és 7 nap között kell lennie.");
        }

        Challenge challenge = new Challenge();
        challenge.setChallenger(challenger);
        challenge.setOpponent(opponent);
        challenge.setLesson(lesson);

        String reqDiff = request.getDifficulty() != null ? request.getDifficulty().trim().toUpperCase() : "";
        String targetDiff = (!reqDiff.isBlank() && !"DYNAMIC".equals(reqDiff))
                ? reqDiff
                : userDifficultyCalculator.determineTargetDifficulty(challenger);
        challenge.setTargetDifficulty(targetDiff);

        challenge.setStatus(ChallengeStatus.DRAFT);
        challenge.setStartTime(clock.instant());
        challenge.setEndTime(clock.instant().plus(days, ChronoUnit.DAYS));

        challenge = challengeRepository.save(challenge);
        return new ChallengeCreateResponse(
                challenge.getChallengeId(),
                challenge.getStatus().name(),
                challenge.getEndTime()
        );
    }

    @Transactional(readOnly = true)
    public List<ChallengeDTO> getActiveChallenges(UUID currentUserId) {
        return challengeRepository.findByChallengerUserIdOrOpponentUserId(currentUserId, currentUserId)
                .stream()
                .filter(c -> c.getStatus() == ChallengeStatus.DRAFT || c.getStatus() == ChallengeStatus.PENDING)
                .map(challenge -> {
                    boolean iAmChallenger = challenge.getChallenger().getUserId().equals(currentUserId);

                    boolean isMyTurn = (challenge.getStatus() == ChallengeStatus.DRAFT && iAmChallenger) ||
                            (challenge.getStatus() == ChallengeStatus.PENDING && !iAmChallenger);

                    return new ChallengeDTO(
                            challenge.getChallengeId(),
                            challenge.getLesson().getLessonId(),
                            challenge.getChallenger().getName(),
                            challenge.getOpponent().getName(),
                            challenge.getLesson().getTitle(),
                            challenge.getTargetDifficulty(),
                            challenge.getStatus().name(),
                            isMyTurn,
                            challenge.getEndTime(),
                            challenge.getStartTime(),
                            null,
                            null
                    );
                })
                .collect(Collectors.toList());
    }

    @Transactional
    public void processChallengeResult(UUID challengeId, UUID currentUserId, int currentScore, int currentTimeTaken) {
        Challenge challenge = challengeRepository.findById(challengeId)
                .orElseThrow(() -> new ResourceNotFoundException("Kihívás nem található."));

        if (challenge.getStatus() == ChallengeStatus.DRAFT && challenge.getChallenger().getUserId().equals(currentUserId)) {
            log.info("Challenger finished! Changing status to PENDING for challenge: {}", challengeId);
            challenge.setStatus(ChallengeStatus.PENDING);
            challengeRepository.save(challenge);

            sseService.sendPing(challenge.getOpponent().getEmail());

            return;
        }

        if (challenge.getStatus() == ChallengeStatus.PENDING && challenge.getOpponent().getUserId().equals(currentUserId)) {
            log.info("Opponent finished! Evaluating challenge: {}", challengeId);

            Result challengerResult = resultRepository.findByChallengeChallengeIdAndUserUserId(challengeId, challenge.getChallenger().getUserId())
                    .orElseThrow(() -> new IllegalStateException("Nem található a kihívó eredménye!"));

            int challengerScore = challengerResult.getScore();
            int challengerTime = challengerResult.getTimeTaken();

            User winner = null;

            if (currentScore > challengerScore) {
                winner = challenge.getOpponent();
            } else if (challengerScore > currentScore) {
                winner = challenge.getChallenger();
            } else {
                if (currentTimeTaken < challengerTime) {
                    winner = challenge.getOpponent();
                } else if (challengerTime < currentTimeTaken) {
                    winner = challenge.getChallenger();
                }
            }

            challenge.setWinner(winner);
            challenge.setStatus(ChallengeStatus.COMPLETED);
            challenge.setCompletedAt(clock.instant());

            if (winner != null) {
                winner.setXp(winner.getXp() + 50);
                userRepository.save(winner);
            }
            challengeRepository.save(challenge);
            sseService.sendPing(challenge.getChallenger().getEmail());
            sseService.sendPing(challenge.getOpponent().getEmail());
        }
    }

    @Transactional(readOnly = true)
    public List<ChallengeDTO> getChallengeHistory(UUID currentUserId) {
        return challengeRepository.findByChallengerUserIdOrOpponentUserId(currentUserId, currentUserId)
                .stream()
                .filter(c -> c.getStatus() == ChallengeStatus.COMPLETED ||
                        c.getStatus() == ChallengeStatus.DECLINED ||
                        c.getStatus() == ChallengeStatus.EXPIRED)
                .map(challenge -> {
                    String winnerName = null;
                    if (challenge.getWinner() != null) {
                        winnerName = challenge.getWinner().getName();
                    } else if (challenge.getStatus() == ChallengeStatus.COMPLETED) {
                        winnerName = "Döntetlen";
                    }

                    return new ChallengeDTO(
                            challenge.getChallengeId(),
                            challenge.getLesson().getLessonId(),
                            challenge.getChallenger().getName(),
                            challenge.getOpponent().getName(),
                            challenge.getLesson().getTitle(),
                            challenge.getTargetDifficulty(),
                            challenge.getStatus().name(),
                            false,
                            challenge.getEndTime(),
                            challenge.getStartTime(),
                            challenge.getCompletedAt(),
                            winnerName
                    );
                })
                .sorted((c1, c2) -> c2.getExpiresAt().compareTo(c1.getExpiresAt()))
                .collect(Collectors.toList());
    }
}