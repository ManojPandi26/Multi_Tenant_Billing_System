package com.mtbs.shared.constant;

public final class CookieConstants {
    private CookieConstants() {}
    public static final String ACCESS_TOKEN_COOKIE = "access_token";
    public static final String REFRESH_TOKEN_COOKIE = "refresh_token";
    public static final String ACCESS_PATH = "/";
    public static final String REFRESH_PATH = "/api/auth/refresh";
    public static final String ADMIN_REFRESH_PATH = "/api/admin/auth/refresh";
}