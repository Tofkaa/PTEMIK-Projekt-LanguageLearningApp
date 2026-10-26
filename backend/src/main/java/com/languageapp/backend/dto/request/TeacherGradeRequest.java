package com.languageapp.backend.dto.request;

import lombok.Data;

@Data
public class TeacherGradeRequest {
    @jakarta.validation.constraints.NotNull
    @jakarta.validation.constraints.Min(0) @jakarta.validation.constraints.Max(100)
    @lombok.Setter(lombok.AccessLevel.NONE)
    private Integer teacherScore;
    @jakarta.validation.constraints.Size(max = 4000)
    private String teacherComment;
    @jakarta.validation.constraints.NotNull @jakarta.validation.constraints.Min(0)
    private Long revision;

    /** Do not let JSON number coercion turn 100.9 into an accepted 100 or overflow into a small score. */
    @com.fasterxml.jackson.annotation.JsonSetter("teacherScore")
    public void setTeacherScore(Number value) {
        if (value == null) { teacherScore = null; return; }
        if (!(value instanceof Byte || value instanceof Short || value instanceof Integer
                || value instanceof Long || value instanceof java.math.BigInteger))
            throw new IllegalArgumentException("A pontszám egész szám legyen.");
        var exact = new java.math.BigInteger(value.toString());
        if (exact.bitLength() > 31) throw new IllegalArgumentException("A pontszám nem fér el egész számként.");
        teacherScore = exact.intValue();
    }
}
