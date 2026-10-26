package com.languageapp.backend.validation;

import com.languageapp.backend.dto.request.*;
import com.languageapp.backend.exception.BadRequestException;
import java.util.*;

/** Schema checks for the four exercise types implemented by the learning UI. */
public final class CurriculumRules {
    public static final int MAX_ITEMS = 1000;
    private static final Set<String> TYPES = Set.of("TRANSLATION", "MULTIPLE_CHOICE", "WORD_BANK", "IMAGE_CHOICE");
    private static final Set<String> LEVELS = Set.of("EASY", "MEDIUM", "HARD");
    private CurriculumRules() { }
    public static void list(List<?> items, int maximum) {
        if (items == null || items.size() > maximum || items.stream().anyMatch(Objects::isNull))
            fail("Hiányzó, túl nagy vagy null elemet tartalmazó importlista.");
    }
    public static void topic(TopicImportRequest topic) {
        if (topic == null) fail("Hiányzó témakör.");
        text(topic.getTopicName(),255,"topicName");optional(topic.getDescription(),10_000,"description");
        if (topic.getCourseCode() != null) text(topic.getCourseCode(),10,"courseCode");
    }
    public static void lesson(LessonImportRequest lesson) {
        if (lesson == null) fail("Hiányzó lecke.");
        text(lesson.getTitle(),255,"title");optional(lesson.getDescription(),10_000,"description");
        optional(lesson.getLanguage(),10,"language");
        if (lesson.getLanguage() != null) language(lesson.getLanguage());
        if (lesson.getOrderIndex() != null && lesson.getOrderIndex() < 0) fail("Az orderIndex nem lehet negatív.");
        if (lesson.getDifficulty() != null && !LEVELS.contains(lesson.getDifficulty())) fail("Érvénytelen leckenehézség.");
    }
    public static void exercise(ExerciseImportRequest exercise) {
        if (exercise == null || !TYPES.contains(exercise.getType() == null ? "" : exercise.getType())) fail("Ismeretlen feladattípus.");
        if (!LEVELS.contains(exercise.getDifficulty() == null ? "" : exercise.getDifficulty())) fail("Érvénytelen feladatnehézség.");
        var content=exercise.getContent();var solution=exercise.getCorrectAnswer();
        if (content == null || solution == null) fail("A content és correctAnswer mező kötelező.");
        json(content,0,new int[]{0});json(solution,0,new int[]{0});
        text(content.get("question"),AnswerLimits.MAX_CHARACTERS,"question");
        String answer=text(solution.get("answer"),AnswerLimits.MAX_CHARACTERS,"correctAnswer.answer");
        String normalized=AnswerText.normalize(answer);
        if (normalized.isEmpty()) fail("A mintamegoldás nem állhat csak írásjelekből vagy szóközökből.");
        optional(exercise.getImageUrl(),4096,"imageUrl");
        if (!"TRANSLATION".equals(exercise.getType())) {
            if (!(content.get("options") instanceof List<?> options)) { fail("A feladathoz options lista szükséges."); return; }
            int minimum="WORD_BANK".equals(exercise.getType())?1:2;
            if (options.size()<minimum || options.size()>100) fail("Érvénytelen számú válaszlehetőség.");
            List<String> normalizedOptions=new ArrayList<>();
            for (Object option:options) {
                String value=AnswerText.normalize(text(option,AnswerLimits.MAX_CHARACTERS,"options"));
                if(value.isEmpty()) fail("Üres válaszlehetőség.");normalizedOptions.add(value);
            }
            if ("WORD_BANK".equals(exercise.getType())) {
                Map<String,Integer> available=new HashMap<>();
                for (String option:normalizedOptions) for(String word:option.split(" ")) available.merge(word,1,Integer::sum);
                for(String word:normalized.split(" ")) {
                    int count=available.getOrDefault(word,0);if(count==0) fail("A mintamegoldás nem rakható ki a szóbank szavaiból.");
                    available.put(word,count-1);
                }
            } else {
                if(new HashSet<>(normalizedOptions).size()!=normalizedOptions.size()) fail("Ismétlődő válaszlehetőség.");
                if(!normalizedOptions.contains(normalized)) fail("A helyes válasz nem szerepel a válaszlehetőségek között.");
            }
        }
    }
    public static void language(Object value) {
        String tag=text(value,64,"lang");
        try { new Locale.Builder().setLanguageTag(tag).build(); }
        catch(IllformedLocaleException invalid) { fail("Érvénytelen nyelvjelölés: "+tag); }
    }
    private static String text(Object value,int maximum,String field) {
        if(!(value instanceof String text) || text.isBlank() || text.length()>maximum || text.indexOf('\0')>=0) {
            fail("Hiányzó vagy túl hosszú szöveg: "+field);return "";
        }
        return text;
    }
    private static void optional(String value,int maximum,String field) {
        if(value!=null && (value.length()>maximum || value.indexOf('\0')>=0)) fail("Túl hosszú vagy érvénytelen mező: "+field);
    }
    private static void json(Object value,int depth,int[] nodes) {
        if(depth>8 || ++nodes[0]>10_000) fail("A feladat JSON-szerkezete túl nagy vagy túl mély.");
        if(value instanceof Map<?,?> map) {
            if(map.size()>100) fail("Túl sok JSON-mező.");
            for(var entry:map.entrySet()) { text(entry.getKey(),128,"JSON-kulcs");json(entry.getValue(),depth+1,nodes); }
        } else if(value instanceof List<?> list) {
            if(list.size()>1000) fail("Túl hosszú JSON-lista.");for(var item:list) json(item,depth+1,nodes);
        } else if(value instanceof String string) optional(string,10_000,"JSON-szöveg");
        else if(value instanceof Number number && !Double.isFinite(number.doubleValue())) fail("Érvénytelen JSON-szám.");
        else if(value!=null && !(value instanceof Number) && !(value instanceof Boolean)) fail("Érvénytelen JSON-érték.");
    }
    private static void fail(String message) { throw new BadRequestException(message); }
}
