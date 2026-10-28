package com.languageapp.backend.service;

import com.languageapp.backend.dto.request.*;
import com.languageapp.backend.dto.response.DynamicExerciseDTO;
import com.languageapp.backend.entity.*;
import com.languageapp.backend.exception.*;
import com.languageapp.backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.text.Normalizer;
import java.util.*;
import java.util.stream.Collectors;

@Service @RequiredArgsConstructor
public class DynamicPracticeService {
    private final UserRepository users;
    private final StudentVocabularyRepository vocabularyRepository;
    private final DynamicPracticeAttemptRepository attempts;
    private final StreakService streak;
    private final AchievementService achievements;
    private final Clock clock;
    private static final int MAX_QUESTIONS = 10;
    private static final int DISTRACTOR_POOL_SIZE = 100;

    public record Start(UUID attemptId, List<DynamicExerciseDTO> exercises) { }
    public record Checked(String answer,boolean correct,String correctAnswer) { }

    @Transactional
    public Start start(String email,UUID courseId) {
        User user=account(email);requireCourse(user,courseId);
        var pending=attempts.findFirstByUser_UserIdAndCourse_CourseIdAndCompletedAtIsNullOrderByStartedAtDesc(user.getUserId(),courseId);
        if(pending.isPresent())return publicStart(pending.get());
        var dueWords=vocabularyRepository.findAllByUser_UserIdAndCourse_CourseIdAndNextPracticeAtLessThanEqualOrderByNextPracticeAtAsc(
                user.getUserId(),courseId,clock.instant(),PageRequest.of(0,MAX_QUESTIONS));
        if(dueWords.isEmpty())return new Start(null,List.of());
        var allCourseWords=vocabularyRepository.findAllByUserAndCourse(user,user.getActiveCourse(),PageRequest.of(0,DISTRACTOR_POOL_SIZE));
        var generated=generate(user,dueWords,allCourseWords);
        var allocation=new ArrayList<DynamicPracticeAttempt.Question>();
        for(var exercise:generated){
            var word=dueWords.stream().filter(v->v.getWord().equals(exercise.getTargetWord())).findFirst().orElseThrow();
            String expected=exercise.getCorrectAnswer();
            exercise.setCorrectAnswer(null);exercise.setTargetWord(null);exercise.setHint(null);
            allocation.add(new DynamicPracticeAttempt.Question(word.getVocabularyId(),exercise,expected));
        }
        var attempt=new DynamicPracticeAttempt();attempt.setUser(user);attempt.setCourse(user.getActiveCourse());
        attempt.setStartedAt(clock.instant());attempt.setQuestions(allocation);attempts.saveAndFlush(attempt);
        return publicStart(attempt);
    }

    @Transactional
    public Checked check(String email,DynamicPracticeCheckRequest request){
        requireAnswer(request.answer());if(request.attemptId()==null || request.exerciseId()==null)throw invalid();
        User user=account(email);var attempt=owned(user,request.attemptId());
        var question=question(attempt,request.exerciseId());
        if (attempt.getCompletedAt() != null) {
            // A second browser tab may still be stepping through a completed attempt: replay only its frozen feedback.
            String accepted = attempt.getAcceptedAnswers().get(request.exerciseId());
            if (accepted == null) throw invalid();
            return new Checked(accepted, equal(accepted, question.expectedAnswer()), question.expectedAnswer());
        }
        var answers=new LinkedHashMap<>(attempt.getAcceptedAnswers());answers.putIfAbsent(request.exerciseId(),request.answer());attempt.setAcceptedAnswers(answers);
        String accepted=answers.get(request.exerciseId());return new Checked(accepted,equal(accepted,question.expectedAnswer()),question.expectedAnswer());
    }

    /** Shared account lock serializes allocation, first-answer checks, SRS changes and one final reward. */
    @Transactional
    public Map<String,Object> submit(String email, DynamicPracticeSubmitRequest request) {
        if (request.getAttemptId() == null || request.getResults() == null || request.getResults().isEmpty()
                || request.getResults().size() > MAX_QUESTIONS || request.getTimeTakenSeconds() < 0) throw invalid();
        var submittedIds = new HashSet<UUID>();
        for (var answer : request.getResults()) {
            if (answer == null || answer.getExerciseId() == null || !submittedIds.add(answer.getExerciseId())) throw invalid();
            requireAnswer(answer.getAnswer());
        }

        User user = account(email);
        var attempt = owned(user, request.getAttemptId());
        if (attempt.getCompletedAt() != null) return attempt.getResult();
        if (request.getResults().size() != attempt.getQuestions().size()) throw invalid();

        int correct = 0, earnedXp = 0, applied = 0;
        Instant now = clock.instant();
        var accepted = new LinkedHashMap<>(attempt.getAcceptedAnswers());
        for (var answer : request.getResults()) {
            var question = question(attempt, answer.getExerciseId());
            // A revealed first answer is final. Final submission cannot replace it with the exposed solution.
            if (accepted.containsKey(answer.getExerciseId())
                    && !accepted.get(answer.getExerciseId()).equals(answer.getAnswer())) throw invalid();
            accepted.put(answer.getExerciseId(), answer.getAnswer());
            boolean right = equal(answer.getAnswer(), question.expectedAnswer());
            if (right) correct++;

            var word = vocabularyRepository.findById(question.vocabularyId()).orElse(null);
            if (word == null || !word.getUser().getUserId().equals(user.getUserId())
                    || !word.getCourse().getCourseId().equals(attempt.getCourse().getCourseId())
                    || word.getNextPracticeAt() != null && word.getNextPracticeAt().isAfter(now)) continue;
            // Another practice mode may have reviewed this word since allocation; only still-due words earn.
            applied++;
            word.setLastPracticedAt(now);
            word.setSrsLevel(right ? Math.min(6, word.getSrsLevel() + 1) : 0);
            if (right) earnedXp += 2;
            int days = switch (word.getSrsLevel()) {
                case 0 -> 0; case 1 -> 1; case 2 -> 3; case 3 -> 7;
                case 4 -> 14; case 5 -> 30; default -> 90;
            };
            word.setNextPracticeAt(now.plus(days, ChronoUnit.DAYS));
        }

        int total = attempt.getQuestions().size();
        if (correct == total && applied == total) earnedXp += 10;
        List<?> awards = List.of();
        if (applied > 0) {
            user.setXp(user.getXp() + earnedXp);
            streak.updateActivity(user);
            awards = achievements.checkAndAwardAchievements(user, correct * 100 / total);
        }
        var result = new LinkedHashMap<String,Object>();
        result.put("newAchievements", awards);
        result.put("correctCount", correct);
        result.put("totalQuestions", total);
        result.put("earnedXp", earnedXp);
        result.put("newStreak", user.getStreak());
        attempt.setAcceptedAnswers(accepted);
        attempt.setCompletedAt(now);
        attempt.setResult(result);
        return result;
    }

    private User account(String email) {
        return users.findByEmailForAuthentication(email)
                .filter(user -> user.isActive() && user.getRole() != com.languageapp.backend.enums.Role.DELETED)
                .orElseThrow(() -> new ForbiddenException("A fiók nem érhető el."));
    }

    private void requireCourse(User user, UUID course) {
        if (course == null || !course.equals(user.getActiveCourse().getCourseId()))
            throw new ConflictException("A kurzus megváltozott. Nyisd meg újra a gyakorlást.");
    }

    private DynamicPracticeAttempt owned(User user, UUID id) {
        var attempt = attempts.findById(id).orElseThrow(() -> new ResourceNotFoundException("A gyakorlás nem található."));
        if (!attempt.getUser().getUserId().equals(user.getUserId()))
            throw new ForbiddenException("Ez a gyakorlás másik felhasználóhoz tartozik.");
        requireCourse(user, attempt.getCourse().getCourseId());
        return attempt;
    }

    private DynamicPracticeAttempt.Question question(DynamicPracticeAttempt attempt, UUID id) {
        return attempt.getQuestions().stream().filter(question -> question.exercise().getExerciseId().equals(id))
                .findFirst().orElseThrow(this::invalid);
    }

    private Start publicStart(DynamicPracticeAttempt attempt) {
        return new Start(attempt.getAttemptId(), attempt.getQuestions().stream()
                .map(DynamicPracticeAttempt.Question::exercise).toList());
    }

    private boolean equal(String answer, String expected) {
        return Normalizer.normalize(answer.trim(), Normalizer.Form.NFC).toLowerCase(Locale.ROOT)
                .equals(Normalizer.normalize(expected.trim(), Normalizer.Form.NFC).toLowerCase(Locale.ROOT));
    }

    private void requireAnswer(String answer) { if (answer == null || answer.length() > 10000) throw invalid(); }
    private BadRequestException invalid() { return new BadRequestException("Érvénytelen gyakorlási válasz vagy kiosztás."); }

    private List<DynamicExerciseDTO> generate(User user,List<StudentVocabulary> dueWords,List<StudentVocabulary> allCourseWords){
        List<DynamicExerciseDTO> dynamicExercises=new ArrayList<>();
        for (StudentVocabulary item : dueWords) {
            int exerciseTypeSelector = new Random().nextInt(3);

            if (exerciseTypeSelector == 0) {

                List<String> wrongOptions = allCourseWords.stream()
                        .filter(w -> !w.getWord().equalsIgnoreCase(item.getWord()))
                        .map(StudentVocabulary::getTranslation)
                        .filter(translation -> !translation.isBlank() && !translation.equalsIgnoreCase(item.getTranslation()))
                        .distinct()
                        .collect(Collectors.toList());
                Collections.shuffle(wrongOptions);

                List<String> options = new ArrayList<>();
                options.add(item.getTranslation());
                if (wrongOptions.size() >= 3) {
                    options.addAll(wrongOptions.subList(0, 3));
                } else {
                    options.addAll(wrongOptions);
                    options.add("alternatív jelentés");
                }
                Collections.shuffle(options);

                dynamicExercises.add(DynamicExerciseDTO.builder()
                        .exerciseId(UUID.randomUUID())
                        .type("MULTIPLE_CHOICE")
                        .question("Mi a jelentése ennek a szónak: " + item.getWord() + "?")
                        .questionLang("hu").answerLang("hu")
                        .questionParts(List.of(new DynamicExerciseDTO.LanguagePart("Mi a jelentése ennek a szónak: ", "hu"),
                                new DynamicExerciseDTO.LanguagePart(item.getWord(), user.getActiveCourse().getLanguageCode()),
                                new DynamicExerciseDTO.LanguagePart("?", "hu")))
                        .targetWord(item.getWord())
                        .correctAnswer(item.getTranslation())
                        .options(options)
                        .build());

            } else if (exerciseTypeSelector == 1 && item.getWord().contains(" ")) {
                List<String> words = Arrays.asList(item.getWord().split(" "));
                List<String> shuffledWords = new ArrayList<>(words);
                Collections.shuffle(shuffledWords);

                dynamicExercises.add(DynamicExerciseDTO.builder()
                        .exerciseId(UUID.randomUUID())
                        .type("WORD_BANK")
                        .question("Rakd sorba a kifejezés szavait: " + item.getTranslation())
                        .questionLang("hu").answerLang(user.getActiveCourse().getLanguageCode())
                        .targetWord(item.getWord())
                        .correctAnswer(item.getWord())
                        .options(shuffledWords)
                        .build());

            } else {
                dynamicExercises.add(DynamicExerciseDTO.builder()
                        .exerciseId(UUID.randomUUID())
                        .type("TRANSLATION")
                        .question("Fordítsd le magyarra: " + item.getWord())
                        .questionLang("hu").answerLang("hu")
                        .questionParts(List.of(new DynamicExerciseDTO.LanguagePart("Fordítsd le magyarra: ", "hu"),
                                new DynamicExerciseDTO.LanguagePart(item.getWord(), user.getActiveCourse().getLanguageCode())))
                        .targetWord(item.getWord())
                        .correctAnswer(item.getTranslation())
                        .hint(item.getWord() + " = " + item.getTranslation())
                        .build());
            }
        }

        return dynamicExercises;
    }
}
