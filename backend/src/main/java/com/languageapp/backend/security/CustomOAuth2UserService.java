package com.languageapp.backend.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.oauth2.client.http.OAuth2ErrorResponseErrorHandler;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import java.time.Duration;

@Slf4j @Service
public class CustomOAuth2UserService extends DefaultOAuth2UserService {
    private final FederatedIdentityService identities;
    private final OAuthLinkService links;
    public CustomOAuth2UserService(FederatedIdentityService identities, OAuthLinkService links) {
        this.links=links;
        this.identities=identities;
        SimpleClientHttpRequestFactory factory=new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5)); factory.setReadTimeout(Duration.ofSeconds(10));
        RestTemplate client=new RestTemplate(factory); client.setErrorHandler(new OAuth2ErrorResponseErrorHandler());
        setRestOperations(client);
    }
    @Override
    public OAuth2User loadUser(OAuth2UserRequest request) throws OAuth2AuthenticationException {
        try {
            OAuth2User providerUser=super.loadUser(request);
            OAuthClaims claims=OAuthClaims.from(request,providerUser);
            Object context=request.getAdditionalParameters().get(OAuthLinkService.CONTEXT_KEY);
            if(context instanceof OAuthLinkService.LinkContext link)
                return new OAuthLinkVerifiedUser(providerUser,links.providerVerified(link,claims));
            return new FederatedOAuth2User(identities.bind(claims),providerUser);
        } catch (OAuth2AuthenticationException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            // Includes enrollment races/DB constraint failures; do not expose provider data or credentials.
            log.warn("OAuth2 sign-in could not complete.");
            throw OAuthClaims.failure("identity_unavailable");
        }
    }
}
