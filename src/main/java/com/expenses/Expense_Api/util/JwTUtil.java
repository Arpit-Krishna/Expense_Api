package com.expenses.Expense_Api.util;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.SecureRandom;
import java.util.Date;

@Component
public class JwTUtil {
    private static final Logger log = LoggerFactory.getLogger(JwTUtil.class);
    private static final String TOKEN_PREFIX = "Bearer ";
    /** HS256 needs a key of at least 256 bits. */
    private static final int MIN_SECRET_BYTES = 32;

    private final Key signingKey;
    private final long expirationMs;

    public JwTUtil(@Value("${app.jwt.secret:}") String secret,
                   @Value("${app.jwt.expiration-hours:168}") long expirationHours) {
        this.signingKey = new SecretKeySpec(keyBytes(secret), "HmacSHA256");
        this.expirationMs = expirationHours * 60 * 60 * 1000;
    }

    /**
     * The secret used to sit in this public repo as a fallback, so anyone could sign a token for any
     * username. Without JWT_SECRET we now use a random key instead: tokens stop working when the app
     * restarts, but nobody can forge one.
     */
    private static byte[] keyBytes(String secret) {
        if (secret != null && secret.getBytes(StandardCharsets.UTF_8).length >= MIN_SECRET_BYTES) {
            return secret.getBytes(StandardCharsets.UTF_8);
        }
        log.warn("JWT_SECRET is missing or shorter than {} characters. Using a random key, so everyone is "
                + "logged out whenever the app restarts. Set JWT_SECRET to fix this.", MIN_SECRET_BYTES);
        byte[] random = new byte[64];
        new SecureRandom().nextBytes(random);
        return random;
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
