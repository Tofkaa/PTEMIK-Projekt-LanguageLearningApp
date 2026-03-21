package com.languageapp.backend.controller;

import com.languageapp.backend.dto.response.RecentResultResponse;
import com.languageapp.backend.entity.Result;
import com.languageapp.backend.entity.User;
import com.languageapp.backend.exception.ResourceNotFoundException;
import com.languageapp.backend.repository.ResultRepository;
import com.languageapp.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/results")
@RequiredArgsConstructor
public class ResultController {

    private final ResultRepository resultRepository;
    private final UserRepository userRepository;

    @GetMapping("/recent")
    public ResponseEntity<List<RecentResultResponse>> getRecentResults(Authentication authentication) {
        // 1. Look up logged-in user
        User user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        // 2. Get last 3 results
        List<Result> recentResults = resultRepository.findTop3ByUserUserIdOrderBySubmittedAtDesc(user.getUserId());

        // 3. Map to DTO for frontend
        List<RecentResultResponse> response = recentResults.stream().map(r -> RecentResultResponse.builder()
                .lessonTitle(r.getLesson().getTitle()) // Ha nálad getName() van a Lesson entitásban, cseréld arra!
                .difficulty(r.getLesson().getDifficulty())
                .score(r.getScore())
                .correctAnswers(r.getCorrectAnswersCount())
                .totalQuestions(r.getTotalQuestionsCount())
                .submittedAt(r.getSubmittedAt())
                .build()
        ).collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }
}