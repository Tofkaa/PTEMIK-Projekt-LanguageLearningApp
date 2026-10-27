package com.languageapp.backend.service;

import com.languageapp.backend.entity.VerificationToken;
import com.languageapp.backend.enums.Role;
import com.languageapp.backend.exception.BadRequestException;
import com.languageapp.backend.repository.UserRepository;
import com.languageapp.backend.repository.VerificationTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.util.UUID;

/** Self-service only; issuance and verification serialize on the same user row. */
@Service @RequiredArgsConstructor
public class VerificationEmailService {
    private final UserRepository users;
    private final VerificationTokenRepository tokens;
    private final EmailService mail;
    private final Clock clock;

    @Transactional
    public void resend(String email) {
        var user=users.findByEmailForAuthentication(email)
                .filter(u -> u.isActive() && u.getRole()!=Role.DELETED)
                .orElseThrow(() -> new BadRequestException("A fiók nem érhető el."));
        if(user.isVerified()) throw new BadRequestException("Az e-mail-címed már meg van erősítve. Frissítsd a fiók állapotát.");
        var now=clock.instant();
        if(tokens.lastCreatedAt(user.getUserId()).filter(last -> now.isBefore(last.plusSeconds(60))).isPresent())
            throw new BadRequestException("Új megerősítő levelet egy perc elteltével kérhetsz.");
        if(tokens.countByUser_UserIdAndCreatedAtAfter(user.getUserId(),now.minusSeconds(3600))>=5)
            throw new BadRequestException("Egy órán belül legfeljebb öt megerősítő levelet kérhetsz.");
        // Old links expire atomically; retain recent issuance timestamps for the durable request budget.
        tokens.expirePrevious(user.getUserId(),now);
        tokens.pruneOld(user.getUserId(),now.minusSeconds(86400));
        var token=new VerificationToken();String secret=UUID.randomUUID().toString();
        token.setUser(user);token.setToken(secret);token.setExpiryDate(now.plusSeconds(86400));tokens.saveAndFlush(token);
        String recipient=user.getEmail(),name=user.getName();
        CommittedEmail.send(() -> mail.sendVerificationEmail(recipient,name,secret));
    }
}
