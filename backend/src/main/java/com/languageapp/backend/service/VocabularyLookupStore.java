package com.languageapp.backend.service;

import com.languageapp.backend.dto.response.VocabularyResponse;
import com.languageapp.backend.entity.*;
import com.languageapp.backend.enums.Role;
import com.languageapp.backend.exception.*;
import com.languageapp.backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Short database phases only. No network IO and no entities leave the snapshot transaction. */
@Service @RequiredArgsConstructor
public class VocabularyLookupStore {
    private final UserRepository users;
    private final StudentVocabularyRepository vocabulary;
    private final CourseRepository courses;
    private final AchievementService achievements;
    private final Clock clock;
    public record Snapshot(UUID userId,UUID courseId,String language,Map<String,VocabularyResponse> existing,boolean explicitCourse) { }
    @Transactional(readOnly=true)
    public Snapshot snapshot(String email,List<String> words) {return snapshot(email,words,null);}
    @Transactional(readOnly=true)
    public Snapshot snapshot(String email,List<String> words,UUID courseId) {
        var context=users.findVocabularyContext(email).orElseThrow(()->new ResourceNotFoundException("User not found"));
        UUID selected=courseId==null?context.courseId():courseId;
        if(selected==null)throw new BadRequestException("Előbb válassz kurzust.");
        String language=courseId==null?context.language():courses.findById(courseId).orElseThrow(()->new ResourceNotFoundException("Course not found")).getLanguageCode();
        Map<String,VocabularyResponse> existing=new HashMap<>();
        vocabulary.findLookupWordSnapshots(context.userId(),selected,words).stream().filter(v->usableTranslation(v.translation()))
                .forEach(v->existing.put(v.word().toLowerCase(Locale.ROOT),VocabularyResponse.builder().vocabularyId(v.id()).word(v.word())
                        .translation(v.translation()).srsLevel(v.level()).nextPracticeAt(v.nextPractice()).isNewAddition(false).build()));
        return new Snapshot(context.userId(),selected,language,Map.copyOf(existing),courseId!=null);
    }
    @Transactional
    public VocabularyResponse saveLookup(String email,Snapshot snapshot,String word,String translation,String source) {
        User user=current(email,snapshot);
        var existing=vocabulary.findByUser_UserIdAndCourse_CourseIdAndWordIgnoreCase(user.getUserId(),snapshot.courseId(),word);
        if(existing.isPresent()) {
            var entry=existing.get();
            if(!usableTranslation(entry.getTranslation()))entry.setTranslation(translation);
            return response(entry,false);
        }
        var entry=entry(user,snapshot,word,translation,source==null?"DICTIONARY":source,false);vocabulary.saveAndFlush(entry);
        achievements.checkAndAwardAchievements(user,-1);return response(entry,true);
    }
    @Transactional
    public void saveKnown(String email,Snapshot snapshot,Map<String,String> translations) {
        User user=current(email,snapshot);
        Map<String,StudentVocabulary> existing=new HashMap<>();
        vocabulary.findLookupWords(user.getUserId(),snapshot.courseId(),translations.keySet()).forEach(v->existing.put(v.getWord().toLowerCase(Locale.ROOT),v));
        boolean changed=false;
        for(var translation:translations.entrySet()) {
            var previous=existing.get(translation.getKey());
            if(previous!=null) {
                if(!usableTranslation(previous.getTranslation())){previous.setTranslation(translation.getValue());changed=true;}
                continue;
            }
            vocabulary.save(entry(user,snapshot,translation.getKey(),translation.getValue(),"AUTO_LEARNED",true));changed=true;
        }
        if(changed){vocabulary.flush();achievements.checkAndAwardAchievements(user,-1);}
    }
    private User current(String email,Snapshot snapshot) {
        User user=users.findByEmailForAuthentication(email).filter(this::active).orElseThrow(()->new ResourceNotFoundException("User not found"));
        if(!user.getUserId().equals(snapshot.userId()) || (!snapshot.explicitCourse() && (user.getActiveCourse()==null || !user.getActiveCourse().getCourseId().equals(snapshot.courseId()))))
            throw new ConflictException("A fiók vagy a kiválasztott kurzus megváltozott. Nyisd meg újra a szót az aktuális kurzusban.");
        return user;
    }
    private boolean usableTranslation(String text) {
        return text!=null && !text.isBlank() && !Set.of("Ismeretlen jelentés","Fordítás nem érhető el").contains(text);
    }
    private boolean active(User u){return u.isActive() && u.getRole()!=Role.DELETED;}
    private StudentVocabulary entry(User user,Snapshot snapshot,String word,String translation,String source,boolean known) {
        var entry=new StudentVocabulary();entry.setUser(user);entry.setCourse(courses.getReferenceById(snapshot.courseId()));entry.setWord(word);entry.setTranslation(translation);entry.setSource(source);
        if(known){entry.setSrsLevel(2);entry.setNextPracticeAt(clock.instant().plus(3,ChronoUnit.DAYS));entry.setLastPracticedAt(clock.instant());}
        return entry;
    }
    private VocabularyResponse response(StudentVocabulary v,boolean added){return VocabularyResponse.builder().vocabularyId(v.getVocabularyId()).word(v.getWord())
            .translation(v.getTranslation()).srsLevel(v.getSrsLevel()).nextPracticeAt(v.getNextPracticeAt()).isNewAddition(added).build();}
}
