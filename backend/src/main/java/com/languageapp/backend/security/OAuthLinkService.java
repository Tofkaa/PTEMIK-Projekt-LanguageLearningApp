package com.languageapp.backend.security;

import com.languageapp.backend.entity.*;
import com.languageapp.backend.enums.*;
import com.languageapp.backend.exception.*;
import com.languageapp.backend.repository.*;
import com.languageapp.backend.service.CommittedEmail;
import com.languageapp.backend.service.EmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.*;
import java.util.*;

/**
 * One expiring intent per account. User-row locking serializes OTP and nonce consumption.
 * Final linking uses enrollment's provider-subject advisory lock before the user lock;
 * provider verification alone never attaches an identity or creates an application session.
 */
@Service @RequiredArgsConstructor
public class OAuthLinkService {
    public static final String CONTEXT_KEY="languageapp.oauth.link.context";
    public static final String LINK_REQUEST_ATTRIBUTE="languageapp.oauth.link.request";
    private static final SecureRandom RANDOM=new SecureRandom();
    private static final String INVALID="Az összekapcsolási jóváhagyás érvénytelen vagy lejárt. Indíts új kérést a Beállításokban.";
    private final UserRepository users;
    private final OAuthIdentityRepository identities;
    private final OAuthLinkIntentRepository intents;
    private final RefreshTokenService sessions;
    private final EmailService mail;
    private final Clock clock;
    @Value("${app.oauth.authorization-base-url:http://localhost:8080}") private String authorizationBase;
    public record LinkContext(UUID userId,UUID requestId,String stateHash) implements Serializable { }
    public record Status(String provider,boolean linked) { }
    public record RequestInfo(String provider,String email,Instant expiresAt) { }
    public record Authorization(String authorizationUrl) { }
    public record Preview(String provider,String accountEmail,String providerEmail,Instant expiresAt,boolean linked) { }
    public record ProviderProof(String token,AuthProvider provider) { }

    @Transactional(readOnly=true)
    public List<Status> list(String email) {
        User user=users.findByEmail(email).filter(this::usable).orElseThrow(this::invalid);
        var linked=new HashSet<AuthProvider>();identities.findByUserUserId(user.getUserId()).forEach(i -> linked.add(i.getProvider()));
        if(user.getAuthProvider()!=AuthProvider.LOCAL && user.getProviderId()!=null)linked.add(user.getAuthProvider());
        return List.of(new Status("google",linked.contains(AuthProvider.GOOGLE)),new Status("discord",linked.contains(AuthProvider.DISCORD)));
    }
    @Transactional
    public RequestInfo request(String email,UUID sid,String cookie,String target) {
        AuthProvider provider=provider(target);User user=account(email);RefreshToken session=session(user,sid,cookie);
        if(alreadyLinked(user,provider))throw new BadRequestException("Ez a belépési mód már hozzá van kapcsolva a fiókodhoz.");
        OAuthLinkIntent intent=intents.findById(user.getUserId()).orElseGet(OAuthLinkIntent::new);Instant now=clock.instant();
        if(intent.getLastRequestedAt()!=null && now.isBefore(intent.getLastRequestedAt().plusSeconds(60)))
            throw new BadRequestException("Új összekapcsolási kódot egy perc elteltével kérhetsz.");
        if(intent.getRateWindowStart()==null || !now.isBefore(intent.getRateWindowStart().plusSeconds(3600))){intent.setRateWindowStart(now);intent.setRequestsInWindow(0);}
        if(intent.getRequestsInWindow()>=5)throw new BadRequestException("Egy órán belül legfeljebb öt összekapcsolási kódot kérhetsz.");
        String code=String.format(Locale.ROOT,"%06d",RANDOM.nextInt(1_000_000));
        intent.setUserId(user.getUserId());intent.setRequestId(UUID.randomUUID());intent.setProvider(provider);
        intent.setSessionId(session.getRefreshTokenId());intent.setSessionHash(session.getTokenHash());intent.setFingerprint(fingerprint(user));
        intent.setCodeHash(hash(code));intent.setFailedAttempts(0);intent.setAuthorizationHash(null);intent.setStateHash(null);
        intent.setConfirmationHash(null);intent.setProviderSubject(null);intent.setProviderEmail(null);intent.setConfirmedAt(null);
        intent.setExpiresAt(now.plusSeconds(900));intent.setLastRequestedAt(now);intent.setRequestsInWindow(intent.getRequestsInWindow()+1);
        intents.saveAndFlush(intent);String recipient=user.getEmail();
        CommittedEmail.send(() -> mail.sendOAuthLinkCode(recipient,label(provider),code));
        return new RequestInfo(provider.name().toLowerCase(Locale.ROOT),recipient,intent.getExpiresAt());
    }
    @Transactional(noRollbackFor=IncorrectOtpException.class)
    public Authorization verify(String email,UUID sid,String cookie,String code) {
        User user=account(email);OAuthLinkIntent intent=current(user,session(user,sid,cookie));
        if(intent.getCodeHash()==null)throw invalid();
        if(intent.getFailedAttempts()>=5)throw new IncorrectOtpException("Elfogytak a kód próbálkozásai. Kérj új összekapcsolási kódot.");
        if(code==null || !code.matches("[0-9]{6}") || !equal(intent.getCodeHash(),hash(code))){
            intent.setFailedAttempts(intent.getFailedAttempts()+1);
            throw new IncorrectOtpException(intent.getFailedAttempts()>=5?"Elfogytak a kód próbálkozásai. Kérj új összekapcsolási kódot."
                    :"Hibás összekapcsolási kód. Még "+(5-intent.getFailedAttempts())+" próbálkozásod maradt.");
        }
        String nonce=secret();intent.setCodeHash(null);intent.setAuthorizationHash(hash(nonce));
        return new Authorization(authorizationBase.replaceAll("/+$","")+"/oauth2/authorization/"+intent.getProvider().name().toLowerCase(Locale.ROOT)+"?link="+nonce);
    }
    @Transactional
    public LinkContext startAuthorization(String nonce,String target,String state) {
        OAuthLinkIntentRepository.Hint hint=intents.authorizationHint(hashToken(nonce)).orElseThrow(this::invalid);
        User user=account(hint.userId());OAuthLinkIntent intent=intents.findById(user.getUserId()).orElseThrow(this::invalid);
        validate(user,intent);activeBoundSession(user,intent);
        if(!equal(intent.getAuthorizationHash(),hashToken(nonce)) || intent.getProvider()!=provider(target) || state==null || state.length()>1024)throw invalid();
        intent.setAuthorizationHash(null);intent.setStateHash(hash(state));
        return new LinkContext(user.getUserId(),intent.getRequestId(),intent.getStateHash());
    }
    @Transactional
    public ProviderProof providerVerified(LinkContext context,OAuthClaims claims) {
        User user=account(context.userId());OAuthLinkIntent intent=intents.findById(user.getUserId()).orElseThrow(this::invalid);
        validate(user,intent);activeBoundSession(user,intent);
        if(!intent.getRequestId().equals(context.requestId()) || !equal(intent.getStateHash(),context.stateHash())
                || intent.getProvider()!=claims.provider() || intent.getProviderSubject()!=null || alreadyLinked(user,claims.provider()))throw invalid();
        if(identities.findUserId(claims.provider(),claims.subject()).filter(id -> !id.equals(user.getUserId())).isPresent())
            throw new BadRequestException("Ez a szolgáltatói fiók már másik LanguageApp-fiókhoz tartozik. Fiókokat nem egyesítünk automatikusan.");
        rejectLegacyOwner(user,claims.provider(),claims.subject());
        String proof=secret();intent.setProviderSubject(claims.subject());intent.setProviderEmail(claims.email());intent.setConfirmationHash(hash(proof));
        return new ProviderProof(proof,claims.provider());
    }
    @Transactional(readOnly=true)
    public Preview preview(String email,UUID sid,String cookie,String proof) {
        User user=users.findByEmail(email).filter(this::usable).orElseThrow(this::invalid);
        return preview(user,confirmation(user,session(user,sid,cookie),proof));
    }
    @Transactional
    public Preview confirm(String email,UUID sid,String cookie,String proof,boolean confirmed) {
        if(!confirmed)throw new BadRequestException("Az összekapcsolást külön meg kell erősítened.");
        OAuthLinkIntentRepository.Hint hint=intents.confirmationHint(hashToken(proof)).orElseThrow(this::invalid);
        // Enrollment uses this same lock order: provider-subject first, account second.
        identities.lockIdentity("oauth:"+hint.provider().name()+":"+hint.providerSubject());
        User user=account(email);OAuthLinkIntent intent=confirmation(user,session(user,sid,cookie),proof);
        if(!hint.requestId().equals(intent.getRequestId()))throw invalid();
        var owner=identities.findUserId(intent.getProvider(),intent.getProviderSubject());
        if(owner.filter(id -> !id.equals(user.getUserId())).isPresent())throw new BadRequestException("Ez a szolgáltatói fiók már másik LanguageApp-fiókhoz tartozik.");
        rejectLegacyOwner(user,intent.getProvider(),intent.getProviderSubject());
        if(intent.getConfirmedAt()!=null)return preview(user,intent);
        if(alreadyLinked(user,intent.getProvider()))throw new BadRequestException("Ez a belépési mód már hozzá van kapcsolva a fiókodhoz.");
        OAuthIdentity identity=new OAuthIdentity();identity.setUser(user);identity.setProvider(intent.getProvider());identity.setSubject(intent.getProviderSubject());identities.saveAndFlush(identity);
        intent.setConfirmedAt(clock.instant());String recipient=user.getEmail(), providerLabel=label(intent.getProvider());
        CommittedEmail.send(() -> mail.sendOAuthLinkSecurityAlert(recipient,providerLabel));
        return preview(user,intent);
    }
    @Transactional
    public void cancel(String email,UUID sid,String cookie) {
        User user=account(email);RefreshToken session=session(user,sid,cookie);
        intents.findById(user.getUserId()).filter(i -> i.getSessionId().equals(session.getRefreshTokenId())).ifPresent(i -> {
            i.setCodeHash(null);i.setAuthorizationHash(null);i.setStateHash(null);i.setConfirmationHash(null);i.setProviderSubject(null);i.setProviderEmail(null);i.setExpiresAt(clock.instant());
        });
    }
    private OAuthLinkIntent confirmation(User user,RefreshToken session,String proof){
        OAuthLinkIntent i=current(user,session);
        if(i.getProviderSubject()==null || !equal(i.getConfirmationHash(),hashToken(proof)))throw invalid();return i;
    }
    private Preview preview(User user,OAuthLinkIntent i){return new Preview(i.getProvider().name().toLowerCase(Locale.ROOT),user.getEmail(),i.getProviderEmail(),i.getExpiresAt(),i.getConfirmedAt()!=null);}
    private OAuthLinkIntent current(User user,RefreshToken s){
        OAuthLinkIntent i=intents.findById(user.getUserId()).orElseThrow(this::invalid);validate(user,i);
        if(!i.getSessionId().equals(s.getRefreshTokenId()) || !equal(i.getSessionHash(),s.getTokenHash()))throw invalid();return i;
    }
    private void validate(User user,OAuthLinkIntent i){if(!clock.instant().isBefore(i.getExpiresAt()) || !equal(i.getFingerprint(),fingerprint(user)))throw invalid();}
    private void activeBoundSession(User user,OAuthLinkIntent i){if(!sessions.isSessionActive(i.getSessionId(),user.getEmail()))throw invalid();}
    private RefreshToken session(User user,UUID sid,String cookie){
        RefreshToken s=sessions.findByRawToken(cookie).orElseThrow(this::invalid);
        if(sid==null || !sid.equals(s.getRefreshTokenId()) || !s.getUser().getUserId().equals(user.getUserId()) || !clock.instant().isBefore(s.getExpiryDate()))throw invalid();return s;
    }
    private User account(String email){return users.findByEmailForAuthentication(email).filter(this::usable).orElseThrow(this::invalid);}
    private User account(UUID id){return users.findForSessionUpdate(id).filter(this::usable).orElseThrow(this::invalid);}
    private boolean usable(User u){return u.isActive() && u.isVerified() && u.getRole()!=Role.DELETED;}
    private void rejectLegacyOwner(User user,AuthProvider provider,String subject) {
        var legacy=users.findLegacyOAuthIds(provider,subject,org.springframework.data.domain.PageRequest.of(0,2));
        if(legacy.stream().anyMatch(id -> !id.equals(user.getUserId())))
            throw new BadRequestException("Ez a szolgáltatói fiók már másik LanguageApp-fiókhoz tartozik.");
    }
    private boolean alreadyLinked(User u,AuthProvider p){return (u.getAuthProvider()==p && u.getProviderId()!=null) || identities.findByUserUserId(u.getUserId()).stream().anyMatch(i -> i.getProvider()==p);}
    private AuthProvider provider(String p){return switch(p==null?"":p){case "google" -> AuthProvider.GOOGLE;case "discord" -> AuthProvider.DISCORD;default -> throw new BadRequestException("Csak Google- vagy Discord-fiók kapcsolható hozzá.");};}
    private String label(AuthProvider p){return p==AuthProvider.GOOGLE?"Google":"Discord";}
    private String fingerprint(User u){return hash(u.getEmail()+"\n"+u.getPasswordHash()+"\n"+u.getAuthProvider()+"\n"+u.getProviderId());}
    private String secret(){byte[] bytes=new byte[32];RANDOM.nextBytes(bytes);return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);}
    private String hashToken(String s){if(s==null || !s.matches("[A-Za-z0-9_-]{43}"))throw invalid();return hash(s);}
    private static String hash(String s){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException e){throw new IllegalStateException("SHA-256 unavailable");}}
    private static boolean equal(String a,String b){return a!=null && b!=null && MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8),b.getBytes(StandardCharsets.UTF_8));}
    private BadRequestException invalid(){return new BadRequestException(INVALID);}
}
