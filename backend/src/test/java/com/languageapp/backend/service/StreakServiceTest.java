package com.languageapp.backend.service;

import com.languageapp.backend.entity.User;
import com.languageapp.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.Spy;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StreakServiceTest {
    @Spy private Clock clock = Clock.fixed(Instant.parse("2026-10-16T16:00:00Z"), ZoneOffset.UTC);

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private StreakService streakService;

    private User testUser;
    private LocalDate today;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setEmail("diak@test.com");
        testUser.setStreak(0);

        // A teszt "mai napja" megegyezik a Service által használt időzónával
        today = LocalDate.now(clock.withZone(ZoneId.of("Europe/Budapest")));
    }

    @Test
    void testUpdateActivity_FirstTimeEver_SetsStreakToOne() {
        // Arrange
        testUser.setLastActivityDate(null);

        // Act
        streakService.updateActivity(testUser);

        // Assert
        assertEquals(1, testUser.getStreak());
        assertEquals(today, testUser.getLastActivityDate());
        verify(userRepository, times(1)).save(testUser);
    }

    @Test
    void testUpdateActivity_ConsecutiveDay_IncrementsStreak() {
        // Arrange: Tegnap tanult, és volt egy 5-ös streakje
        testUser.setLastActivityDate(today.minusDays(1));
        testUser.setStreak(5);

        // Act
        streakService.updateActivity(testUser);

        // Assert: 6-ra kell ugrania
        assertEquals(6, testUser.getStreak());
        assertEquals(today, testUser.getLastActivityDate());
        verify(userRepository, times(1)).save(testUser);
    }

    @Test
    void testUpdateActivity_SameDay_DoesNotChangeStreak() {
        // Arrange: Ma már tanult egyszer
        testUser.setLastActivityDate(today);
        testUser.setStreak(5);

        // Act: Megcsinál még egy leckét
        streakService.updateActivity(testUser);

        // Assert: Marad 5
        assertEquals(5, testUser.getStreak());
        assertEquals(today, testUser.getLastActivityDate());
        // Nem is hívjuk meg a save-et feleslegesen, mert nem változott semmi (a Service-ben van egy return)
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void testUpdateActivity_SkippedDay_ResetsStreakToOne() {
        // Arrange: Tegnapelőtt tanult utoljára, megszakadt a lánc
        testUser.setLastActivityDate(today.minusDays(2));
        testUser.setStreak(10);

        // Act
        streakService.updateActivity(testUser);

        // Assert: Visszaesik 1-re
        assertEquals(1, testUser.getStreak());
        assertEquals(today, testUser.getLastActivityDate());
        verify(userRepository, times(1)).save(testUser);
    }

    @Test
    void testResetExpiredStreaks_CallsRepositoryWithYesterday() {
        // Act
        streakService.resetExpiredStreaks();

        // Assert: Elkapjuk a paramétert, amit a Service átadott a Repository-nak
        ArgumentCaptor<LocalDate> dateCaptor = ArgumentCaptor.forClass(LocalDate.class);
        verify(userRepository, times(1)).resetExpiredStreaks(dateCaptor.capture());

        LocalDate passedDate = dateCaptor.getValue();
        assertEquals(today.minusDays(1), passedDate, "A Cron jobnak a tegnapi napot kell átadnia a query-nek!");
    }

    @ParameterizedTest
    @CsvSource({
            "2026-01-16T23:00:00Z, 2026-01-17",
            "2026-07-16T22:00:00Z, 2026-07-17",
            "2026-03-29T00:30:00Z, 2026-03-29",
            "2026-03-29T01:30:00Z, 2026-03-29",
            "2026-10-25T00:30:00Z, 2026-10-25",
            "2026-10-25T01:30:00Z, 2026-10-25"
    })
    void streakDay_ShouldUseBudapestRegardlessOfClockZone(String instant, String expectedDate) {
        StreakService service = new StreakService(
                Clock.fixed(Instant.parse(instant), ZoneId.of("America/New_York")), userRepository);
        service.updateActivity(testUser);
        assertEquals(LocalDate.parse(expectedDate), testUser.getLastActivityDate());
        service.resetExpiredStreaks();
        verify(userRepository).resetExpiredStreaks(LocalDate.parse(expectedDate).minusDays(1));
    }
}
