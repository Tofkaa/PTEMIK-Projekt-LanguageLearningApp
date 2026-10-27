package com.languageapp.backend.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.oauth2.client.endpoint.*;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.*;
import org.springframework.security.oauth2.core.endpoint.*;
import java.util.HashMap;

/** Linking metadata travels only through the server-saved authorization request. */
public final class OAuthLinkAuthorization implements OAuth2AuthorizationRequestResolver,
        AuthorizationRequestRepository<OAuth2AuthorizationRequest>,
        OAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest> {
    private final DefaultOAuth2AuthorizationRequestResolver resolver;
    private final HttpSessionOAuth2AuthorizationRequestRepository repository = new HttpSessionOAuth2AuthorizationRequestRepository();
    private final RestClientAuthorizationCodeTokenResponseClient tokenClient = new RestClientAuthorizationCodeTokenResponseClient();
    private final OAuthLinkService links;
    public OAuthLinkAuthorization(ClientRegistrationRepository registrations, OAuthLinkService links) {
        resolver = new DefaultOAuth2AuthorizationRequestResolver(registrations, "/oauth2/authorization");
        this.links = links;
        var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(java.time.Duration.ofSeconds(5));
        factory.setReadTimeout(java.time.Duration.ofSeconds(10));
        tokenClient.setRestClient(org.springframework.web.client.RestClient.builder().requestFactory(factory)
                .messageConverters(converters -> {
                    converters.clear();
                    converters.add(new org.springframework.http.converter.FormHttpMessageConverter());
                    converters.add(new org.springframework.security.oauth2.core.http.converter.OAuth2AccessTokenResponseHttpMessageConverter());
                }).defaultStatusHandler(new org.springframework.security.oauth2.client.http.OAuth2ErrorResponseErrorHandler()).build());
    }
    @Override public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
        return attach(request, resolver.resolve(request));
    }
    @Override public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String registrationId) {
        return attach(request, resolver.resolve(request, registrationId));
    }
    private OAuth2AuthorizationRequest attach(HttpServletRequest request, OAuth2AuthorizationRequest authorization) {
        if (authorization == null || request.getParameter("link") == null) return authorization;
        request.setAttribute(OAuthLinkService.LINK_REQUEST_ATTRIBUTE, true);
        try {
            var context = links.startAuthorization(request.getParameter("link"),
                    authorization.getAttribute("registration_id"), authorization.getState());
            return OAuth2AuthorizationRequest.from(authorization)
                    .attributes(attributes -> attributes.put(OAuthLinkService.CONTEXT_KEY, context)).build();
        } catch (RuntimeException rejected) { throw OAuthClaims.failure("identity_unavailable"); }
    }
    @Override public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
        return repository.loadAuthorizationRequest(request);
    }
    @Override public void saveAuthorizationRequest(OAuth2AuthorizationRequest authorization, HttpServletRequest request, HttpServletResponse response) {
        if (authorization != null && authorization.getAttribute(OAuthLinkService.CONTEXT_KEY) instanceof OAuthLinkService.LinkContext) {
            response.setHeader("Cache-Control", "no-store");
            response.setHeader("Referrer-Policy", "no-referrer");
        }
        repository.saveAuthorizationRequest(authorization, request, response);
    }
    @Override public OAuth2AuthorizationRequest removeAuthorizationRequest(HttpServletRequest request, HttpServletResponse response) {
        var authorization = repository.removeAuthorizationRequest(request, response);
        if (authorization != null && authorization.getAttribute(OAuthLinkService.CONTEXT_KEY) instanceof OAuthLinkService.LinkContext)
            request.setAttribute(OAuthLinkService.LINK_REQUEST_ATTRIBUTE, true);
        return authorization;
    }
    @Override public OAuth2AccessTokenResponse getTokenResponse(OAuth2AuthorizationCodeGrantRequest request) {
        var response = tokenClient.getTokenResponse(request);
        var parameters = new HashMap<>(response.getAdditionalParameters());
        parameters.remove(OAuthLinkService.CONTEXT_KEY);
        Object context = request.getAuthorizationExchange().getAuthorizationRequest().getAttribute(OAuthLinkService.CONTEXT_KEY);
        if (context instanceof OAuthLinkService.LinkContext) parameters.put(OAuthLinkService.CONTEXT_KEY, context);
        return OAuth2AccessTokenResponse.withResponse(response).additionalParameters(parameters).build();
    }
}
