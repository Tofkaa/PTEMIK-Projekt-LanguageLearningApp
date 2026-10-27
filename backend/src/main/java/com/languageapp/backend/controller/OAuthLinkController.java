package com.languageapp.backend.controller;

import com.languageapp.backend.security.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @RequestMapping("/api/oauth-links") @RequiredArgsConstructor
public class OAuthLinkController {
    private final OAuthLinkService links;
    private final JwtService jwt;
    @ModelAttribute public void preventCaching(jakarta.servlet.http.HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Referrer-Policy", "no-referrer");
    }
    @GetMapping public List<OAuthLinkService.Status> list(Authentication auth){return links.list(auth.getName());}
    @PostMapping("/request") public OAuthLinkService.RequestInfo request(Authentication auth,@RequestHeader("Authorization") String access,
            @CookieValue(value="refreshToken",required=false)String cookie,@RequestBody Map<String,String> body){return links.request(auth.getName(),sid(access),cookie,body.get("provider"));}
    @PostMapping("/verify") public OAuthLinkService.Authorization verify(Authentication auth,@RequestHeader("Authorization") String access,
            @CookieValue(value="refreshToken",required=false)String cookie,@RequestBody Map<String,String> body){return links.verify(auth.getName(),sid(access),cookie,body.get("code"));}
    @PostMapping("/preview") public OAuthLinkService.Preview preview(Authentication auth,@RequestHeader("Authorization") String access,
            @CookieValue(value="refreshToken",required=false)String cookie,@RequestBody Map<String,String> body){return links.preview(auth.getName(),sid(access),cookie,body.get("proof"));}
    public record Confirmation(String proof,boolean confirmed) { }
    @PostMapping("/confirm") public OAuthLinkService.Preview confirm(Authentication auth,@RequestHeader("Authorization") String access,
            @CookieValue(value="refreshToken",required=false)String cookie,@RequestBody Confirmation body){return links.confirm(auth.getName(),sid(access),cookie,body.proof(),body.confirmed());}
    @PostMapping("/cancel") public void cancel(Authentication auth,@RequestHeader("Authorization") String access,
            @CookieValue(value="refreshToken",required=false)String cookie){links.cancel(auth.getName(),sid(access),cookie);}
    private UUID sid(String access){return access!=null && access.startsWith("Bearer ")?jwt.extractSessionId(access.substring(7)):null;}
}
