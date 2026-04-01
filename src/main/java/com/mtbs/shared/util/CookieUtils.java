package com.mtbs.shared.util;

import com.mtbs.shared.constant.CookieConstants;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class CookieUtils {

    @Value("${app.cookie.secure}")
    private boolean secureCookie;

    @Value("${jwt.expiration}")
    private long accessTokenExpirationMs;

    @Value("${jwt.refresh-expiration}")
    private long refreshTokenExpirationMs;

    public void addAuthCookies(HttpServletResponse response, String accessToken, String refreshToken) {
        addAccessCookie(response, accessToken);
        addRefreshCookie(response, refreshToken, CookieConstants.REFRESH_PATH);
    }

    public void addAdminAuthCookies(HttpServletResponse response, String accessToken, String refreshToken) {
        addAccessCookie(response, accessToken);
        addRefreshCookie(response, refreshToken, CookieConstants.ADMIN_REFRESH_PATH);
    }

    public void clearAuthCookies(HttpServletResponse response) {
        ResponseCookie accessCookie = buildClearCookie(CookieConstants.ACCESS_TOKEN_COOKIE, CookieConstants.ACCESS_PATH);
        response.addHeader(HttpHeaders.SET_COOKIE, accessCookie.toString());

        ResponseCookie refreshCookie = buildClearCookie(CookieConstants.REFRESH_TOKEN_COOKIE, CookieConstants.REFRESH_PATH);
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString());

        ResponseCookie adminRefreshCookie = buildClearCookie(CookieConstants.REFRESH_TOKEN_COOKIE, CookieConstants.ADMIN_REFRESH_PATH);
        response.addHeader(HttpHeaders.SET_COOKIE, adminRefreshCookie.toString());
    }

    public Optional<String> extractAccessToken(HttpServletRequest request) {
        return extractCookie(request, CookieConstants.ACCESS_TOKEN_COOKIE);
    }

    public Optional<String> extractRefreshToken(HttpServletRequest request) {
        return extractCookie(request, CookieConstants.REFRESH_TOKEN_COOKIE);
    }

    private Optional<String> extractCookie(HttpServletRequest request, String cookieName) {
        if (request.getCookies() == null) {
            return Optional.empty();
        }
        for (Cookie cookie : request.getCookies()) {
            if (cookieName.equals(cookie.getName())) {
                return Optional.of(cookie.getValue());
            }
        }
        return Optional.empty();
    }

    private void addAccessCookie(HttpServletResponse response, String token) {
        ResponseCookie cookie = ResponseCookie.from(CookieConstants.ACCESS_TOKEN_COOKIE, token)
                .httpOnly(true)
                .secure(secureCookie)
                .sameSite("Strict")
                .path(CookieConstants.ACCESS_PATH)
                .maxAge(Duration.ofMillis(accessTokenExpirationMs))
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void addRefreshCookie(HttpServletResponse response, String token, String path) {
        ResponseCookie cookie = ResponseCookie.from(CookieConstants.REFRESH_TOKEN_COOKIE, token)
                .httpOnly(true)
                .secure(secureCookie)
                .sameSite("Strict")
                .path(path)
                .maxAge(Duration.ofMillis(refreshTokenExpirationMs))
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private ResponseCookie buildClearCookie(String name, String path) {
        return ResponseCookie.from(name, "")
                .httpOnly(true)
                .secure(secureCookie)
                .sameSite("Strict")
                .path(path)
                .maxAge(0) // 0 maxAge to clear the cookie immediately
                .build();
    }
}
