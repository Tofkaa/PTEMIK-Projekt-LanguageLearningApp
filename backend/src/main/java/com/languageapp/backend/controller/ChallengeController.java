package com.languageapp.backend.controller;

import com.languageapp.backend.dto.request.ChallengeCreateRequest;
import com.languageapp.backend.dto.response.ChallengeCreateResponse;
import com.languageapp.backend.dto.response.ChallengeDTO;
import com.languageapp.backend.entity.User;
import com.languageapp.backend.exception.ResourceNotFoundException;
import com.languageapp.backend.repository.UserRepository;
import com.languageapp.backend.service.ChallengeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for managing user challenges and duels.
 */
@RestController
@RequestMapping("/api/challenges")
@RequiredArgsConstructor
public class ChallengeController {

    private final ChallengeService challengeService;
    private final UserRepository userRepository;

    /**
     * Initializes a new challenge in DRAFT status.
     *
     * @param request The payload containing opponent and lesson details.
     * @param authentication The current authenticated user's security context.
     * @return A response containing the new challenge ID to initiate the lesson bypass.
     */
    @PostMapping("/create")
    public ResponseEntity<ChallengeCreateResponse> createChallenge(
            @RequestBody ChallengeCreateRequest request,
            Authentication authentication) {

        User challenger = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new ResourceNotFoundException("User not found."));

        ChallengeCreateResponse response = challengeService.createDraftChallenge(challenger.getUserId(), request);
        return ResponseEntity.ok(response);
    }

    /**
     * Retrieves all active challenges (DRAFT or PENDING) relevant to the authenticated user.
     *
     * @param authentication The current authenticated user's security context.
     * @return A list of active ChallengeDTOs.
     */
    @GetMapping("/active")
    public ResponseEntity<List<ChallengeDTO>> getActiveChallenges(Authentication authentication) {
        User user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new ResourceNotFoundException("User not found."));

        return ResponseEntity.ok(challengeService.getActiveChallenges(user.getUserId()));
    }

    /**
     * Retrieves the historical record of closed challenges (COMPLETED, DECLINED, EXPIRED).
     *
     * @param authentication The current authenticated user's security context.
     * @return A list of historical ChallengeDTOs.
     */
    @GetMapping("/history")
    public ResponseEntity<List<ChallengeDTO>> getChallengeHistory(Authentication authentication) {
        User user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new ResourceNotFoundException("User not found."));

        return ResponseEntity.ok(challengeService.getChallengeHistory(user.getUserId()));
    }
}