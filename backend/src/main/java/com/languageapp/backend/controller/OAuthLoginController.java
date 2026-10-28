package com.languageapp.backend.controller;

import com.languageapp.backend.security.*;
import com.languageapp.backend.exception.ForbiddenException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/auth/oauth") @RequiredArgsConstructor
public class OAuthLoginController {
    private final OAuthLoginHandoff handoff;
    private final FederatedIdentityService identities;
    private final BrowserOriginPolicy browserOriginPolicy;
    public record Exchange(String code) { }
    public record Access(String accessToken) { }
    @PostMapping(value="/exchange",consumes=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Access> exchange(@RequestBody Exchange body,@RequestHeader(value="Origin",required=false)String origin,HttpServletRequest request) {
        if(!browserOriginPolicy.allows(origin))throw new ForbiddenException("A belépési jóváhagyás csak az alkalmazás oldaláról váltható be.");
        var identity=handoff.consume(request,body.code());
        FederatedIdentityService.Login login;
        try { login=identities.issueSession(identity); }
        catch(org.springframework.security.oauth2.core.OAuth2AuthenticationException rejected) {
            throw new com.languageapp.backend.exception.BadRequestException("A közösségi belépés már nem hagyható jóvá. Indíts új belépést.");
        }
        var cookie=ResponseCookie.from("refreshToken",login.refreshToken()).httpOnly(true).secure(true).path("/").maxAge(7*24*60*60).sameSite("Lax").build();
        var oldState=ResponseCookie.from("JSESSIONID","").httpOnly(true).secure(true).path("/").maxAge(0).sameSite("Lax").build();
        return ResponseEntity.ok().header("Cache-Control","no-store").header("Referrer-Policy","no-referrer")
                .header(HttpHeaders.SET_COOKIE,cookie.toString(),oldState.toString()).body(new Access(login.accessToken()));
    }
}
