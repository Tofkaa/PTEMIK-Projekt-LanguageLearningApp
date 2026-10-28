package com.languageapp.backend.security;

/** Per-account bursts and refill windows. IP quotas deliberately allow a shared classroom NAT. */
public enum RequestOperation {
    LOGIN(20,300), REGISTER(3,3600), RESET_REQUEST(3,3600), RESET_REDEEM(20,300),
    VERIFICATION_RESEND(5,3600), EMAIL_CHANGE_REQUEST(5,3600), OAUTH_LINK_REQUEST(5,3600), DELETION_REQUEST(5,3600), SECURITY_VERIFY(30,300), REFRESH(120,60),
    LOOKUP(120,60), MANUAL_CODE(30,60), CHECK(600,60), UPLOAD(10,60), EXPORT(3,300),
    INVITATION_PREVIEW(60,60), INVITATION_CONFIRM(20,60), INVITATION_MANAGE(10,60),
    OAUTH_EXCHANGE(30,60), VERIFY_EMAIL(30,60);
    public final int capacity;
    public final int seconds;
    RequestOperation(int capacity,int seconds){this.capacity=capacity;this.seconds=seconds;}
    public boolean publicEndpoint() {
        return this==LOGIN || this==REGISTER || this==RESET_REQUEST || this==RESET_REDEEM
                || this==REFRESH || this==OAUTH_EXCHANGE || this==VERIFY_EMAIL;
    }
    public boolean mailIssue() {
        return this==VERIFICATION_RESEND || this==EMAIL_CHANGE_REQUEST || this==OAUTH_LINK_REQUEST || this==DELETION_REQUEST;
    }
    public static RequestOperation route(String method,String path) {
        if("POST".equals(method)) {
            return switch(path) {
                case "/api/auth/login" -> LOGIN;
                case "/api/auth/register" -> REGISTER;
                case "/api/auth/forgot-password" -> RESET_REQUEST;
                case "/api/auth/reset-password" -> RESET_REDEEM;
                case "/api/auth/refresh" -> REFRESH;
                case "/api/auth/oauth/exchange" -> OAUTH_EXCHANGE;
                case "/api/users/me/verification-email" -> VERIFICATION_RESEND;
                case "/api/users/me/email-change/request" -> EMAIL_CHANGE_REQUEST;
                case "/api/users/me/deletion/request" -> DELETION_REQUEST;
                case "/api/oauth-links/request" -> OAUTH_LINK_REQUEST;
                case "/api/users/me/email-change/verify", "/api/users/me/deletion/preview",
                     "/api/users/me/deletion/confirm", "/api/oauth-links/verify", "/api/oauth-links/preview",
                     "/api/oauth-links/confirm" -> SECURITY_VERIFY;
                case "/api/vocabulary/lookup", "/api/vocabulary/known-batch" -> LOOKUP;
                case "/api/vocabulary/practice/check" -> CHECK;
                case "/api/friendships/request", "/api/classrooms/join" -> MANUAL_CODE;
                case "/api/users/me/profile-picture" -> UPLOAD;
                default -> path.startsWith("/api/invitations/") ? INVITATION_MANAGE : path.matches("/api/(?:exercises/[^/]+/check|lessons/[^/]+/attempts/[^/]+/exercises/[^/]+/check)") ? CHECK : null;
            };
        }
        if("PUT".equals(method) && "/api/users/me/password".equals(path)) return SECURITY_VERIFY;
        if("GET".equals(method) && "/api/users/me/export".equals(path)) return EXPORT;
        if("GET".equals(method) && "/api/auth/verify".equals(path)) return VERIFY_EMAIL;
        if(path.startsWith("/api/invitations/")) return INVITATION_MANAGE; // Account/action budget is checked by its controller adapter.
        return null;
    }
}
