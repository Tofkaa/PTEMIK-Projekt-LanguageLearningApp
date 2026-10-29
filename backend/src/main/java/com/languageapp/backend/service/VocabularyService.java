package com.languageapp.backend.service;

import com.languageapp.backend.dto.response.VocabularyDetailDTO;
import com.languageapp.backend.dto.response.VocabularyResponse;
import com.languageapp.backend.entity.StudentVocabulary;
import com.languageapp.backend.entity.User;
import com.languageapp.backend.exception.ForbiddenException;
import com.languageapp.backend.exception.ResourceNotFoundException;
import com.languageapp.backend.repository.StudentVocabularyRepository;
import com.languageapp.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class VocabularyService {
    private final Clock clock;

    private final StudentVocabularyRepository vocabularyRepository;
    private final UserRepository userRepository;
    private final VocabularyLookupStore lookupStore;
    private final TranslationClient translations;
    private final StreakService streakService;
    private final AchievementService achievementService;

    @Transactional(propagation=org.springframework.transaction.annotation.Propagation.NEVER)
    public VocabularyResponse lookupAndSaveWord(String email,String word,String source) { return lookupAndSaveWord(email,word,source,null); }
    @Transactional(propagation=org.springframework.transaction.annotation.Propagation.NEVER)
    public VocabularyResponse lookupAndSaveWord(String email,String word,String source,UUID courseId) {
        com.languageapp.backend.validation.AnswerLimits.requireWord(word);
        String normalized=word.trim().toLowerCase(Locale.ROOT);
        com.languageapp.backend.validation.AnswerLimits.requireWord(normalized);
        if(source!=null && source.length()>50)throw new com.languageapp.backend.exception.BadRequestException("A szótári forrás legfeljebb 50 karakter lehet.");
        var snapshot=lookupStore.snapshot(email,List.of(normalized),courseId);
        var existing=snapshot.existing().get(normalized);if(existing!=null)return existing;
        String translation=translations.translateAll(snapshot.language(),List.of(normalized)).get(normalized);
        return lookupStore.saveLookup(email,snapshot,normalized,translation,source);
    }

    /**
     * Processes the training results and updates the SRS levels.
     */
    @Transactional
    public VocabularyResponse recordPracticeResult(UUID vocabularyId, String email, boolean isCorrect) {return recordPracticeResult(vocabularyId,email,isCorrect,null);}
    @Transactional
    public VocabularyResponse recordPracticeResult(UUID vocabularyId, String email, boolean isCorrect, UUID courseId) {
        User owner = userRepository.findByEmailForAuthentication(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        StudentVocabulary voc = vocabularyRepository.findById(vocabularyId)
                .orElseThrow(() -> new ResourceNotFoundException("Szó nem található"));

        if (!voc.getUser().getUserId().equals(owner.getUserId())) {
            throw new ForbiddenException("Nincs jogosultságod módosítani ezt a szót");
        }

        if (courseId != null && !courseId.equals(voc.getCourse().getCourseId()))
            throw new com.languageapp.backend.exception.ConflictException("A szó másik kurzushoz tartozik.");
        voc.setLastPracticedAt(clock.instant());

        if (isCorrect) {
            voc.setSrsLevel(Math.min(6, voc.getSrsLevel() + 1));
        } else {
            voc.setSrsLevel(0);
        }

        int daysToAdd = switch (voc.getSrsLevel()) {
            case 0 -> 0;
            case 1 -> 1;
            case 2 -> 3;
            case 3 -> 7;
            case 4 -> 14;
            case 5 -> 30;
            default -> 90;
        };

        voc.setNextPracticeAt(clock.instant().plus(daysToAdd, ChronoUnit.DAYS));
        StudentVocabulary updatedVoc = vocabularyRepository.save(voc);

        achievementService.checkAndAwardAchievements(voc.getUser(), -1);
        return buildVocabularyResponse(updatedVoc, false);
    }

    /**
     * Retrieves the words that the student needs to repeat up to the current time.
     * Results are in ascending order of next practice time (oldest due first).
     * Now strictly filtered by the user's actively selected language course.
     */
    @Transactional(readOnly = true)
    public List<VocabularyResponse> getDueVocabularyForToday(String email) { return getDueVocabularyForToday(email,null); }
    @Transactional(readOnly = true)
    public List<VocabularyResponse> getDueVocabularyForToday(String email,UUID courseId) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Instant now = clock.instant();

        // Query due vocabulary specifically isolated to the active course ID
        List<StudentVocabulary> dueVocabs = vocabularyRepository
                .findAllByUser_UserIdAndCourse_CourseIdAndNextPracticeAtLessThanEqualOrderByNextPracticeAtAsc(user.getUserId(), courseId != null ? courseId : user.getActiveCourse().getCourseId(), now);

        return dueVocabs.stream()
                .map(voc -> buildVocabularyResponse(voc, false))
                .toList();
    }

    /**
     * Returns the entire student dictionary as a key-value (word -> SRS level) map.
     * This optimizes frontend rendering, avoiding unnecessary API calls.
     * Generates the map exclusively from words belonging to the currently active course.
     */
    @Transactional(readOnly = true)
    public Map<String, Integer> getVocabularyMap(String email) { return getVocabularyMap(email,null); }
    @Transactional(readOnly = true)
    public Map<String, Integer> getVocabularyMap(String email,UUID courseId) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        // Fetch words tied to the currently active course
        List<StudentVocabulary> allVocabs = vocabularyRepository
                .findAllByUser_UserIdAndCourse_CourseIdOrderByFirstSeenAtDesc(user.getUserId(), courseId != null ? courseId : user.getActiveCourse().getCourseId());

        return allVocabs.stream()
                .filter(voc -> voc.getSrsLevel() >= 3)
                .collect(Collectors.toMap(
                        voc -> voc.getWord().toLowerCase(),
                        StudentVocabulary::getSrsLevel,
                        (existing, replacement) -> existing
                ));
    }

    /**
     * Returns all of the student's saved words with detailed SRS data for Vocabulary Hub.
     * Added course-level filtering.
     */
    @Transactional(readOnly = true)
    public List<VocabularyDetailDTO> getDetailedVocabularyForUser(String email) { return getDetailedVocabularyForUser(email,null); }
    @Transactional(readOnly = true)
    public List<VocabularyDetailDTO> getDetailedVocabularyForUser(String email,UUID courseId) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        // Fetch dictionary entries specifically for the active course
        List<StudentVocabulary> entries = courseId == null ? vocabularyRepository.findAllByUserAndCourse(user, user.getActiveCourse())
                : vocabularyRepository.findAllByUser_UserIdAndCourse_CourseIdOrderByFirstSeenAtDesc(user.getUserId(),courseId);

        return entries.stream().map(v -> new VocabularyDetailDTO(
                v.getWord(),
                v.getTranslation(),
                v.getSrsLevel(),
                v.getNextPracticeAt(),
                v.getSrsLevel() >= 4
        )).toList();
    }

    /**
     * Add words to user's vocabulary where they did not request the clickabletext help and still got the answer right.
     * This means the user presumably already knew the meaning well enough to grant them a srs level of 2.
     * The batch process now respects the multi-language boundary.
     */
    @Transactional(propagation=org.springframework.transaction.annotation.Propagation.NEVER)
    public void addKnownWordsBatch(String email,List<String> words) { addKnownWordsBatch(email,words,null); }
    @Transactional(propagation=org.springframework.transaction.annotation.Propagation.NEVER)
    public void addKnownWordsBatch(String email,List<String> words,UUID courseId) {
        if(words==null || words.isEmpty())return;
        com.languageapp.backend.validation.AnswerLimits.requireWords(words);
        List<String> normalized=words.stream().map(word->word.trim().toLowerCase(Locale.ROOT)).distinct().toList();
        var snapshot=lookupStore.snapshot(email,normalized,courseId);
        List<String> missing=normalized.stream().filter(word->!snapshot.existing().containsKey(word)).toList();
        if(missing.isEmpty())return;
        var translated=translations.translateAll(snapshot.language(),missing);
        lookupStore.saveKnown(email,snapshot,translated);
    }

    /**
     * Helper method to map StudentVocabulary to VocabularyResponse
     */
    private VocabularyResponse buildVocabularyResponse(StudentVocabulary voc, boolean isNewAddition) {
        return VocabularyResponse.builder()
                .vocabularyId(voc.getVocabularyId())
                .word(voc.getWord())
                .translation(voc.getTranslation())
                .srsLevel(voc.getSrsLevel())
                .nextPracticeAt(voc.getNextPracticeAt())
                .isNewAddition(isNewAddition)
                .build();
    }
}
