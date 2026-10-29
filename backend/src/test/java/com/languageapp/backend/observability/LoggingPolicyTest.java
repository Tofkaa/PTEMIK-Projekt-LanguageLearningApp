package com.languageapp.backend.observability;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import java.nio.file.*;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.*;

class LoggingPolicyTest {
    @Test void applicationLogCallsCannotFormatArbitraryValuesOutsideReviewedDiagnostics()throws Exception {
        Path root=Path.of("src/main/java/com/languageapp/backend");int checked=0;
        var calls=Pattern.compile("(?:log|logger|LOGGER)\\.(?:debug|info|warn|error|trace)\\((.*?)\\);",Pattern.DOTALL);
        var literal=Pattern.compile("\"(?:[^\"\\\\]|\\\\.)*\"");
        assertFalse(literal.matcher("\"unsafe {}\", value").matches());
        assertFalse(literal.matcher("\"unsafe\" + value").matches());
        assertTrue(literal.matcher("\"safe.event\"").matches());
        try(var paths=Files.walk(root)){
            for(Path file:paths.filter(path->path.toString().endsWith(".java")).toList()){
                if(java.util.Set.of("SafeDiagnostics.java","OperationsMonitor.java").contains(file.getFileName().toString()))continue;
                var source=Files.readString(file);var found=calls.matcher(source);
                while(found.find())assertTrue(literal.matcher(found.group(1).strip()).matches(),"Unreviewed operational log values: "+file);
                assertFalse(source.contains("log.at"),"Unreviewed fluent diagnostic: "+file);checked++;
            }
        }
        assertTrue(checked>50);
    }
    @Test void safeDiagnosticsKeepEventTypeAndLocationWithoutMessagesCausesOrThrowables() {
        Logger logger=(Logger)LoggerFactory.getLogger(SafeDiagnostics.class);var captured=new ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();captured.start();logger.addAppender(captured);
        try{
            var error=new IllegalArgumentException("PRIVATE_EMAIL_TOKEN_PASSWORD",new RuntimeException("PRIVATE_CAUSE"));
            SafeDiagnostics.failure(logger,"http.test.failed",error);var event=captured.list.getFirst();
            assertTrue(event.getFormattedMessage().contains("IllegalArgumentException"));assertTrue(event.getFormattedMessage().contains("LoggingPolicyTest"));
            assertFalse(event.getFormattedMessage().contains("PRIVATE"));assertNull(event.getThrowableProxy());
            SafeDiagnostics.failure(logger,"PRIVATE_INJECTED_EVENT\nsecret",error);assertTrue(captured.list.getLast().getFormattedMessage().contains("diagnostic.failure"));
        }finally{logger.detachAppender(captured);captured.stop();}
    }

    @Test void packagedProductionDefaultsDisableSensitiveDiagnosticChannels()throws Exception {
        var properties=new java.util.Properties();var source=SafeDiagnostics.class.getProtectionDomain().getCodeSource().getLocation().toURI();
        try(var input=source.resolve("application.properties").toURL().openStream()){properties.load(input);}
        assertEquals("false",properties.getProperty("spring.jpa.show-sql"));
        assertEquals("OFF",properties.getProperty("logging.level.org.hibernate.orm.jdbc.bind"));
        assertEquals("OFF",properties.getProperty("logging.level.org.hibernate.orm.jdbc.error"));
        assertEquals("false",properties.getProperty("spring.mail.properties.mail.debug"));
        assertEquals("logstash",properties.getProperty("logging.structured.format.console"));
        assertEquals("stack_trace",properties.getProperty("logging.structured.json.exclude"));
        assertEquals("health,metrics",properties.getProperty("management.endpoints.web.exposure.include"));
        assertEquals("never",properties.getProperty("management.endpoint.health.show-details"));
    }
}
