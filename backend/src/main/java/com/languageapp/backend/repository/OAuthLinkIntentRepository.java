package com.languageapp.backend.repository;

import com.languageapp.backend.entity.OAuthLinkIntent;
import com.languageapp.backend.enums.AuthProvider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.*;

public interface OAuthLinkIntentRepository extends JpaRepository<OAuthLinkIntent,UUID> {
    record Hint(UUID userId, UUID requestId, AuthProvider provider, String providerSubject) { }
    @Query("select new com.languageapp.backend.repository.OAuthLinkIntentRepository$Hint(i.userId,i.requestId,i.provider,i.providerSubject) from OAuthLinkIntent i where i.authorizationHash=:hash")
    Optional<Hint> authorizationHint(String hash);
    @Query("select new com.languageapp.backend.repository.OAuthLinkIntentRepository$Hint(i.userId,i.requestId,i.provider,i.providerSubject) from OAuthLinkIntent i where i.confirmationHash=:hash")
    Optional<Hint> confirmationHint(String hash);
}
