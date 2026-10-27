package com.languageapp.backend.security;

import com.languageapp.backend.enums.AuthProvider;
import jakarta.mail.internet.InternetAddress;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.OAuth2User;
import java.net.URI;
import java.util.Locale;
import java.util.Map;

/** Validates server-fetched userinfo; this does not parse or trust browser-supplied identity claims. */
public record OAuthClaims(AuthProvider provider,String subject,String email,String name,String picture) {
    public static OAuthClaims from(OAuth2UserRequest request,OAuth2User principal) {
        AuthProvider provider = switch (request.getClientRegistration().getRegistrationId()) {
            case "google" -> AuthProvider.GOOGLE;
            case "discord" -> AuthProvider.DISCORD;
            default -> throw failure("identity_unavailable");
        };
        Map<String,Object> attributes=principal.getAttributes();
        String subject=text(attributes.get(provider==AuthProvider.GOOGLE?"sub":"id"));
        if (subject==null || !subject.matches("[\\x21-\\x7E]{1,255}") || !subject.equals(principal.getName())
                || (provider==AuthProvider.DISCORD && !subject.matches("[0-9]{1,20}"))) throw failure("identity_unavailable");
        if (!Boolean.TRUE.equals(attributes.get(provider==AuthProvider.GOOGLE?"email_verified":"verified")))
            throw failure("unverified_email");
        String email=text(attributes.get("email"));
        if (email==null || email.length()>254 || !email.equals(email.trim())) throw failure("identity_unavailable");
        try { new InternetAddress(email,true).validate(); }
        catch (Exception invalid) { throw failure("identity_unavailable"); }
        email=email.toLowerCase(Locale.ROOT);
        String name=text(attributes.get(provider==AuthProvider.GOOGLE?"name":"username"));
        if (name==null || name.isBlank()) name="Felhasználó";
        if (name.codePointCount(0,name.length())>255) name=name.substring(0,name.offsetByCodePoints(0,255));
        String picture=null;
        if (provider==AuthProvider.DISCORD) {
            String avatar=text(attributes.get("avatar"));
            if (avatar!=null && avatar.matches("[A-Za-z0-9_]{1,128}"))
                picture="https://cdn.discordapp.com/avatars/"+subject+"/"+avatar+".png";
        } else {
            String value=text(attributes.get("picture"));
            try {
                if (value!=null && value.length()<=255 && "https".equals(URI.create(value).getScheme())
                        && URI.create(value).getHost()!=null && URI.create(value).getUserInfo()==null) picture=value;
            } catch (IllegalArgumentException ignored) { /* Optional photo is not an identity claim. */ }
        }
        return new OAuthClaims(provider,subject,email,name,picture);
    }
    private static String text(Object value) { return value instanceof String s?s:null; }
    public static OAuth2AuthenticationException failure(String code) {
        return new OAuth2AuthenticationException(new OAuth2Error(code),"A közösségi bejelentkezés nem fejezhető be.");
    }
}
