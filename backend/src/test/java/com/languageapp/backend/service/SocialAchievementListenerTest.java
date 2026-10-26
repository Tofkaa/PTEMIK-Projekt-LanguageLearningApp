package com.languageapp.backend.service;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.List;
import java.util.UUID;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SocialAchievementListenerTest {
    @Mock AchievementService achievements;
    @InjectMocks SocialAchievementListener listener;
    @Test void evaluatesBothParticipantsOnceEvenWhenTheFirstEvaluationFails() {
        UUID a = new UUID(0, 1), b = new UUID(0, 2);
        doThrow(new IllegalStateException("temporary failure")).when(achievements).checkSocialAchievements(a);
        listener.onSocialActivity(new SocialAchievementEvent(List.of(b, a, b)));
        verify(achievements).checkSocialAchievements(a);
        verify(achievements).checkSocialAchievements(b);
        verifyNoMoreInteractions(achievements);
    }
}
