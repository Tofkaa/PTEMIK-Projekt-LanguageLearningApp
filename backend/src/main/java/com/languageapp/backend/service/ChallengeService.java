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


import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChallengeService {

    private final ChallengeRepository challengeRepository;
    private final UserRepository userRepository;
    private final LessonRepository lessonRepository;
    private final FriendshipService friendshipService;
    private final ResultRepository resultRepository;
    private final SseService sseService;

    /**
     * Initializes a new challenge in DRAFT mode. Verifies friendship and sets the expiration timer.
     * * @param challengerId The UUID of the user initiating the challenge.
     * @param request The request payload containing opponent and lesson IDs.
     * @return ChallengeCreateResponse containing the generated Challenge ID.
     */
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

        challenge.setStatus(ChallengeStatus.DRAFT);
        challenge.setStartTime(LocalDateTime.now());
        challenge.setEndTime(LocalDateTime.now().plusDays(days));

        challenge = challengeRepository.save(challenge);

        return new ChallengeCreateResponse(
                challenge.getChallengeId(),
                challenge.getStatus().name(),
                challenge.getEndTime()
        );
    }

    /**
     * Fetches active challenges for a user. Filters visibility based on the user's role:
     * Challengers can see their DRAFTs, Opponents can only see PENDING challenges.
     *
     * @param currentUserId The UUID of the authenticated user.
     * @return List of formatted ChallengeDTOs for the frontend UI.
     */
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
                            challenge.getLesson().getDifficulty(),
                            challenge.getStatus().name(),
                            isMyTurn,
                            challenge.getEndTime(),
                            null
                    );
                })
                .collect(Collectors.toList());
    }

    /**
     * The core Challenge Evaluation Engine. Triggered automatically by the EvaluationService
     * upon lesson completion. Manages the state machine transitions (DRAFT -> PENDING -> COMPLETED)
     * and calculates the winner based on Score and Time-taken (tie-breaker).
     *
     * @param challengeId The UUID of the active challenge.
     * @param currentUserId The user who just completed the lesson.
     * @param currentScore The score achieved by the user.
     * @param currentTimeTaken The time taken in seconds.
     */
    @Transactional
    public void processChallengeResult(UUID challengeId, UUID currentUserId, int currentScore, int currentTimeTaken) {
        Challenge challenge = challengeRepository.findById(challengeId)
                .orElseThrow(() -> new ResourceNotFoundException("Kihívás nem található."));

        // CASE 1 Challenger finished the lesson (DRAFT -> PENDING)
        if (challenge.getStatus() == ChallengeStatus.DRAFT && challenge.getChallenger().getUserId().equals(currentUserId)) {
            log.info("Challenger finished! Changing status to PENDING for challenge: {}", challengeId);
            challenge.setStatus(ChallengeStatus.PENDING);
            challengeRepository.save(challenge);

            sseService.sendPing(challenge.getOpponent().getEmail());

            return;
        }

        // Case 2 Challenged finished the lesson (PENDING -> COMPLETED + Evaluation)
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

            if (winner != null) {
                winner.setXp(winner.getXp() + 50);
                userRepository.save(winner);
            }
            challengeRepository.save(challenge);
            sseService.sendPing(challenge.getChallenger().getEmail());
            sseService.sendPing(challenge.getOpponent().getEmail());
        }
    }

    /**
     * Retrieves the user's historical challenges (Completed, Declined, or Expired)
     * sorted by the most recent expiration date.
     *
     * @param currentUserId The UUID of the authenticated user.
     * @return List of historical ChallengeDTOs including winner information.
     */
    @Transactional(readOnly = true)
    public List<ChallengeDTO> getChallengeHistory(UUID currentUserId) {
        return challengeRepository.findByChallengerUserIdOrOpponentUserId(currentUserId, currentUserId)
                .stream()
                .filter(c -> c.getStatus() == ChallengeStatus.COMPLETED ||
                        c.getStatus() == ChallengeStatus.DECLINED ||
                        c.getStatus() == ChallengeStatus.EXPIRED)
                .map(challenge -> {
                   // Get winner name, null if tie
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
                            challenge.getLesson().getDifficulty(),
                            challenge.getStatus().name(),
                            false,
                            challenge.getEndTime(),
                            winnerName
                    );
                })
                .sorted((c1, c2) -> c2.getExpiresAt().compareTo(c1.getExpiresAt())) // Legfrissebbek elöl
                .collect(Collectors.toList());
    }
}