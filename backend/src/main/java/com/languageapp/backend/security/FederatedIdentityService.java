package com.languageapp.backend.security;

import com.languageapp.backend.entity.*;
import com.languageapp.backend.enums.*;
import com.languageapp.backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.*;

@Service @RequiredArgsConstructor
public class FederatedIdentityService {
    private final UserRepository users;
    private final OAuthIdentityRepository identities;
    private final CourseRepository courses;
    private final RefreshTokenService sessions;
    private final JwtService jwt;
    private final Clock clock;
    private static final SecureRandom RANDOM=new SecureRandom();
    public record Binding(UUID userId,AuthProvider provider,String subject) implements java.io.Serializable { }
    public record Login(String accessToken,String refreshToken) { }

    /** Provider IO finishes before entering this short transaction. No email-based account linking. */
    @Transactional
    public Binding bind(OAuthClaims claims) {
        if (claims.provider()!=AuthProvider.GOOGLE && claims.provider()!=AuthProvider.DISCORD)
            throw OAuthClaims.failure("identity_unavailable");
        // One provider identity cannot be enrolled twice, including across application instances.
        identities.lockIdentity("oauth:"+claims.provider().name()+":"+claims.subject());
        Optional<UUID> linked=identities.findUserId(claims.provider(),claims.subject());
        if (linked.isPresent()) {
            User user=active(linked.get());
            return binding(user,claims);
        }
        List<UUID> legacy=users.findLegacyOAuthIds(claims.provider(),claims.subject(),PageRequest.of(0,2));
        if (legacy.size()>1) throw OAuthClaims.failure("identity_unavailable");
        User user;
        if (!legacy.isEmpty()) {
            user=active(legacy.getFirst());
            // Recheck after waiting: account deletion/profile operations may have invalidated legacy evidence.
            if (user.getAuthProvider()!=claims.provider() || !claims.subject().equals(user.getProviderId()))
                throw OAuthClaims.failure("identity_unavailable");
        } else {
            if (users.existsByEmailIgnoreCase(claims.email())) throw OAuthClaims.failure("email_in_use");
            user=new User(); user.setEmail(claims.email()); user.setName(claims.name());
            user.setAuthProvider(claims.provider()); user.setProviderId(claims.subject());
            user.setRole(Role.STUDENT); user.setPreferredDifficulty(DifficultyLevel.DYNAMIC); user.setVerified(true);
            user.setActiveCourse(courses.findByLanguageCode("en").orElseThrow(() -> OAuthClaims.failure("identity_unavailable")));
            user.setFriendCode(friendCode()); user.setUserTag(tag(claims.name())); user.setProfilePictureUrl(claims.picture());
            users.saveAndFlush(user);
        }
        OAuthIdentity identity=new OAuthIdentity(); identity.setProvider(claims.provider()); identity.setSubject(claims.subject()); identity.setUser(user);
        identities.saveAndFlush(identity);
        return binding(user,claims);
    }
    private Binding binding(User user,OAuthClaims claims) { return new Binding(user.getUserId(),claims.provider(),claims.subject()); }
    private User active(UUID id) {
        return users.findForSessionUpdate(id).filter(u -> u.isActive() && u.isVerified() && u.getRole()!=Role.DELETED)
                .orElseThrow(() -> OAuthClaims.failure("identity_unavailable"));
    }
    /** Resolve the binding again under the account lock immediately before issuing credentials. */
    @Transactional
    public Login issueSession(FederatedOAuth2User principal) {
        return issueSession(new Binding(principal.userId(),principal.provider(),principal.subject()));
    }
    @Transactional
    public Login issueSession(Binding principal) {
        User user=active(principal.userId());
        UUID linked=identities.findUserId(principal.provider(),principal.subject())
                .orElseThrow(() -> OAuthClaims.failure("identity_unavailable"));
        if (!user.getUserId().equals(linked)) throw OAuthClaims.failure("identity_unavailable");
        user.setLastLogin(clock.instant());
        var session=sessions.createSession(user.getUserId());
        var details=org.springframework.security.core.userdetails.User.withUsername(user.getEmail())
                .password(user.getPasswordHash()!=null?user.getPasswordHash():"").authorities("ROLE_"+user.getRole().name()).build();
        return new Login(jwt.generateSessionToken(details,session.id()),session.rawToken());
    }
    private String friendCode() {
        String alphabet="ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        for(int attempt=0;attempt<100;attempt++) {
            StringBuilder code=new StringBuilder();
            for(int i=0;i<6;i++) code.append(i==3?'-':alphabet.charAt(RANDOM.nextInt(alphabet.length())));
            if (!users.existsByFriendCode(code.toString())) return code.toString();
        }
        throw OAuthClaims.failure("identity_unavailable");
    }
    private String tag(String name) {
        for(int attempt=0;attempt<100;attempt++) {
            String tag=String.valueOf(1000+RANDOM.nextInt(9000));
            if (!users.existsByNameAndUserTag(name,tag)) return tag;
        }
        throw OAuthClaims.failure("identity_unavailable");
    }
}
