package com.languageapp.backend.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;
import java.util.Collection;
import java.util.Map;

/** A provider proof is deliberately not an authenticated LanguageApp account/session. */
public final class OAuthLinkVerifiedUser implements OAuth2User {
    private final OAuth2User delegate;
    private final OAuthLinkService.ProviderProof proof;
    public OAuthLinkVerifiedUser(OAuth2User delegate, OAuthLinkService.ProviderProof proof) {
        this.delegate=delegate; this.proof=proof;
    }
    public OAuthLinkService.ProviderProof proof(){return proof;}
    @Override public Map<String,Object> getAttributes(){return delegate.getAttributes();}
    @Override public Collection<? extends GrantedAuthority> getAuthorities(){return delegate.getAuthorities();}
    @Override public String getName(){return delegate.getName();}
}
