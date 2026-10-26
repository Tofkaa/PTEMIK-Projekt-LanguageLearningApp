package com.languageapp.backend.dto;

import com.languageapp.backend.dto.request.TeacherGradeRequest;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;

class TeacherGradeRequestTest {
    final JsonMapper mapper = JsonMapper.builder().build();
    @Test void retainsValidIntegerEndpoints() {
        assertEquals(0,mapper.readValue("{\"teacherScore\":0,\"revision\":0}",TeacherGradeRequest.class).getTeacherScore());
        assertEquals(100,mapper.readValue("{\"teacherScore\":100,\"revision\":0}",TeacherGradeRequest.class).getTeacherScore());
    }
    @Test void rejectsFractionsAndOverflowRatherThanTruncatingOrWrappingThem() {
        for(String score:new String[]{"100.9","99.5","1e30","4294967296","-4294967296"})
            assertThrows(Exception.class,()->mapper.readValue("{\"teacherScore\":"+score+",\"revision\":0}",TeacherGradeRequest.class));
    }
}
