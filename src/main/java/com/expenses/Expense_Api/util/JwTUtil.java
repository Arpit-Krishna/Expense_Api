package com.expenses.Expense_Api.util;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;

@Component
public class JwTUtil {
    private static final String TOKEN_PREFIX = "Bearer ";

    private final Key signingKey;
    private final long expirationMs;

    public JwTUtil(@Value("${app.jwt.secret}") String secret,
                   @Value("${app.jwt.expiration-hours:168}") long expirationHours) {
        this.signingKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        this.expirationMs = expirationHours * 60 * 60 * 1000;
    }

    public String generateToken(String username) {
        Date now = new Date();
        return TOKEN_PREFIX + Jwts.builder()
                .setSubject(username)
                .setIssuedAt(now)
                .setExpiration(new Date(now.getTime() + expirationMs))
                .signWith(signingKey, SignatureAlgorithm.HS256)
                .compact();
    }

    /** Returns the username for a valid, unexpired token, or null if the token is invalid. */
    public String extractUsername(String token) {
        if (token == null) return null;
        if (token.startsWith(TOKEN_PREFIX)) {
            token = token.substring(TOKEN_PREFIX.length());
        }
        try {
            return Jwts.parserBuilder()
                    .setSigningKey(signingKey)
                    .build()
                    .parseClaimsJws(token)
                    .getBody()
                    .getSubject();
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }

    public boolean validateToken(String token) {
        return extractUsername(token) != null;
    }
}
