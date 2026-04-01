package com.mtbs.auth.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.List;
import java.util.function.Function;
import com.mtbs.auth.entity.PlatformAdmin;

@Component
public class JwtTokenProvider {

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.expiration}")
    private long jwtExpiration;

    @Value("${jwt.refresh-expiration}")
    private long refreshExpiration;

    private SecretKey getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(jwtSecret);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateToken(Long userId, Long tenantId, String schemaName,
            String role, List<String> permissions) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + jwtExpiration);

        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("tenantId", tenantId)
                .claim("schemaName", schemaName)
                .claim("role", role)
                .claim("permissions", permissions)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(getSigningKey())
                .compact();
    }

    public String generateSuperAdminToken(PlatformAdmin admin) {
        return Jwts.builder()
                .subject(String.valueOf(admin.getId()))
                .claim("email", admin.getEmail())
                .claim("role", "SUPER_ADMIN")
                .claim("isSuperAdmin", true)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + jwtExpiration))
                .signWith(getSigningKey())
                .compact();
    }

    public boolean isSuperAdminToken(Claims claims) {
        return Boolean.TRUE.equals(claims.get("isSuperAdmin", Boolean.class));
    }

    public Long getUserIdFromToken(String token) {
        return Long.parseLong(getClaimFromToken(token, Claims::getSubject));
    }

    public Long getTenantIdFromToken(String token) {
        Claims claims = getAllClaimsFromToken(token);
        return claims.get("tenantId", Long.class);
    }

    public String getSchemaNameFromToken(String token) {
        Claims claims = getAllClaimsFromToken(token);
        return claims.get("schemaName", String.class);
    }

    public String getRoleFromToken(String token) {
        Claims claims = getAllClaimsFromToken(token);
        return claims.get("role", String.class);
    }

    @SuppressWarnings("unchecked")
    public List<String> getPermissionsFromToken(String token) {
        Claims claims = getAllClaimsFromToken(token);
        return claims.get("permissions", List.class);
    }

    public <T> T getClaimFromToken(String token, Function<Claims, T> claimsResolver) {
        Claims claims = getAllClaimsFromToken(token);
        return claimsResolver.apply(claims);
    }

    private Claims getAllClaimsFromToken(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser().verifyWith(getSigningKey()).build().parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public boolean isTokenExpired(String token) {
        Date expiration = getClaimFromToken(token, Claims::getExpiration);
        return expiration.before(new Date());
    }

    public long getJwtExpiration() {
        return jwtExpiration;
    }

    public long getRefreshExpiration() {
        return refreshExpiration;
    }
}
