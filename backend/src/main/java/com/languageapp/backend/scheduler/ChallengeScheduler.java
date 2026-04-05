package com.languageapp.backend.scheduler;

import com.languageapp.backend.entity.Challenge;
import com.languageapp.backend.enums.ChallengeStatus;
import com.languageapp.backend.repository.ChallengeRepository;
import com.languageapp.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChallengeScheduler {

    private final ChallengeRepository challengeRepository;
    private final UserRepository userRepository;

    /**
     * This method runs in the background every hour.
     * (Cron expression: second, minute, hour, day, month, day of the week)
     * "0 0 * * * *" = at exactly the 0th minute of every hour, second 0.
     */
    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void closeExpiredChallenges() {
        log.info("Cron Job indítva: Lejárt kihívások keresése...");

        List<ChallengeStatus> activeStatuses = Arrays.asList(ChallengeStatus.DRAFT, ChallengeStatus.PENDING);
        List<Challenge> expiredChallenges = challengeRepository.findByStatusInAndEndTimeBefore(activeStatuses, LocalDateTime.now());

        if (expiredChallenges.isEmpty()) {
            return;
        }

        for (Challenge challenge : expiredChallenges) {
            if (challenge.getStatus() == ChallengeStatus.PENDING) {
                // The challenger finished, but their friend was lazy -> AUTO WIN for the Challenger!
                challenge.setWinner(challenge.getChallenger());
                challenge.getChallenger().setXp(challenge.getChallenger().getXp() + 50); // XP bónusz
                userRepository.save(challenge.getChallenger());

                log.info("Kihívás (ID: {}) lejárt PENDING állapotban. Győztes: {}",
                        challenge.getChallengeId(), challenge.getChallenger().getEmail());
            } else if (challenge.getStatus() == ChallengeStatus.DRAFT) {
                // The challenger started but never finished their own lesson -> No winner
                challenge.setWinner(null);
                log.info("Kihívás (ID: {}) lejárt DRAFT állapotban.", challenge.getChallengeId());
            }

            challenge.setStatus(ChallengeStatus.EXPIRED);
            challengeRepository.save(challenge);
        }

        log.info("Siker, {} db lejárt kihívás sikeresen lezárva.", expiredChallenges.size());
    }
}