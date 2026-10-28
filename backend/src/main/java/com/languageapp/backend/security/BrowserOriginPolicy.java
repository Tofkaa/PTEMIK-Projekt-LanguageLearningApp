package com.languageapp.backend.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Locale;

/** A configured public origin is trusted; request Host/Forwarded headers never define trust. */
@Component
public final class BrowserOriginPolicy {
    private final URI frontend;
    private final String origin;

    public BrowserOriginPolicy(@Value("${app.frontend.url}") String frontendUrl) {
        try {
            frontend = URI.create(frontendUrl);
            if (!validAuthority(frontend) || frontend.getRawQuery() != null || frontend.getRawFragment() != null) {
                throw new IllegalArgumentException();
            }
            String scheme = frontend.getScheme().toLowerCase(Locale.ROOT);
            int port = port(frontend);
            origin = scheme + "://" + frontend.getHost().toLowerCase(Locale.ROOT)
                    + (port == (scheme.equals("https") ? 443 : 80) ? "" : ":" + port);
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("app.frontend.url must be an absolute HTTP(S) frontend URL.");
        }
    }

    public String origin() { return origin; }

    public boolean allows(String value) {
        if (value == null) return false;
        try {
            URI actual = URI.create(value);
            return validAuthority(actual) && actual.getRawQuery() == null && actual.getRawFragment() == null
                    && (actual.getRawPath() == null || actual.getRawPath().isEmpty())
                    && frontend.getScheme().equalsIgnoreCase(actual.getScheme())
                    && frontend.getHost().equalsIgnoreCase(actual.getHost()) && port(frontend) == port(actual);
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }

    private static boolean validAuthority(URI value) {
        return ("http".equalsIgnoreCase(value.getScheme()) || "https".equalsIgnoreCase(value.getScheme()))
                && value.getHost() != null && value.getRawUserInfo() == null
                && value.getPort() >= -1 && value.getPort() <= 65535;
    }

    private static int port(URI value) {
        return value.getPort() >= 0 ? value.getPort() : "https".equalsIgnoreCase(value.getScheme()) ? 443 : 80;
    }
}
