package com.languageapp.backend.security;

import com.languageapp.backend.enums.AuthProvider;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;
import java.util.*;

/** Server-created binding. Provider email is display data, never the application account lookup key. */
public final class FederatedOAuth2User implements OAuth2User {
    private final FederatedIdentityService.Binding binding;
    private final Map<String,Object> attributes;
    private final List<? extends GrantedAuthority> authorities;
    public FederatedOAuth2User(FederatedIdentityService.Binding binding,OAuth2User providerUser) {
        this.binding=Objects.requireNonNull(binding);
        attributes=Collections.unmodifiableMap(new LinkedHashMap<>(providerUser.getAttributes()));
        authorities=List.copyOf(providerUser.getAuthorities());
    }
    public UUID userId() { return binding.userId(); }
    public AuthProvider provider() { return binding.provider(); }
    public String subject() { return binding.subject(); }
    @Override public Map<String,Object> getAttributes() { return attributes; }
    @Override public Collection<? extends GrantedAuthority> getAuthorities() { return authorities; }
    @Override public String getName() { return binding.subject(); }
}
