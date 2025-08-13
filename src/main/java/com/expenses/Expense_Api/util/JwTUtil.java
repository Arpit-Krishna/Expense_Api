package com.expenses.Expense_Api.util;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.SignatureAlgorithm;
import org.springframework.stereotype.Component;
import io.jsonwebtoken.Jwts;

import javax.crypto.spec.SecretKeySpec;
import java.security.Key;
import java.util.Date;

@Component
public class JwTUtil {
    private final String SECRET_KEY = "qwerty@1234567890_ThisIsA256BitSecretKey!";
    private final String TOKEN_PREFIX = "Bearer ";

    private Key getSigningKey() {
        return new SecretKeySpec(SECRET_KEY.getBytes(), "HmacSHA256");
    }

    public String generateToken(String username) {
        return TOKEN_PREFIX + Jwts.builder()
                .setSubject(username)
                .setIssuedAt(new Date())
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    public String extractUsername(String token) {
        if (token.startsWith(TOKEN_PREFIX)) {
            token = token.substring(TOKEN_PREFIX.length());
        }
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody()
                .getSubject();
    }

    public boolean validateToken(String token) {
        try {
            if (token.startsWith(TOKEN_PREFIX)) {
                token = token.substring(TOKEN_PREFIX.length());
            }
            // No expiration check, just parse
            Jwts.parserBuilder().setSigningKey(getSigningKey()).build().parseClaimsJws(token);
            return true;
        } catch (JwtException e) {
            return false;
        }
    }
}
