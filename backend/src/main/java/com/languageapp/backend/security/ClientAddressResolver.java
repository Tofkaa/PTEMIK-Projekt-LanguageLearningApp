package com.languageapp.backend.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.net.*;
import java.time.*;
import java.util.*;

/** Only the configured immediate proxy may supply our overwritten client-IP header. */
@Component
public class ClientAddressResolver {
    private final String trustedHost;
    private final Clock clock;
    private Set<String> trusted = Set.of();
    private Instant refreshed = Instant.MIN;
    public ClientAddressResolver(@Value("${app.rate-limit.trusted-proxy-host:}") String host,Clock clock){this.trustedHost=host;this.clock=clock;}
    public String address(HttpServletRequest request) {
        String peer=request.getRemoteAddr();
        if(trustedHost.isBlank() || !trustedPeers().contains(peer)) return peer;
        String supplied=request.getHeader("X-App-Client-IP");
        if(supplied==null || supplied.length()>45 || !supplied.matches("[0-9a-fA-F:.]+")
                || (!supplied.contains(":") && !supplied.matches("[0-9]+(?:\\.[0-9]+){3}"))) return peer;
        try { return InetAddress.getByName(supplied).getHostAddress(); }
        catch(UnknownHostException invalid){return peer;}
    }
    protected Set<String> resolveTrusted() throws UnknownHostException {
        Set<String> result=new HashSet<>();
        for(InetAddress value:InetAddress.getAllByName(trustedHost)) result.add(value.getHostAddress());
        return result;
    }
    private synchronized Set<String> trustedPeers() {
        Instant now=clock.instant();
        if(now.isBefore(refreshed) || !now.isBefore(refreshed.plusSeconds(30))) {
            try { trusted=Set.copyOf(resolveTrusted()); } catch(UnknownHostException unavailable){trusted=Set.of();}
            refreshed=now;
        }
        return trusted;
    }
}
