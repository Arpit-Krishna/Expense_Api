package com.expenses.Expense_Api.util;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.junit.jupiter.api.Test;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

class JwTUtilTest {

    private static final String SECRET = "a-test-secret-that-is-long-enough-for-hs256";

    @Test
    void roundTripsUsername() {
        JwTUtil jwt = new JwTUtil(SECRET, 1);
        String token = jwt.generateToken("arpit");

        assertThat(token).startsWith("Bearer ");
        assertThat(jwt.extractUsername(token)).isEqualTo("arpit");
        assertThat(jwt.extractUsername(token.substring(7))).isEqualTo("arpit");
    }

    @Test
    void rejectsTokenSignedWithAnotherKey() {
        String forged = new JwTUtil("some-other-secret-that-is-also-long-enough", 1).generateToken("arpit");

        assertThat(new JwTUtil(SECRET, 1).extractUsername(forged)).isNull();
    }

    @Test
    void rejectsTokenSignedWithTheOldPublicFallbackSecret() {
        // This secret was committed to the public repo; tokens signed with it must not work
        // unless someone deliberately configures it again.
        String oldSecret = "qwerty@1234567890_ThisIsA256BitSecretKey!";
        String forged = Jwts.builder().setSubject("arpit").setIssuedAt(new Date())
                .signWith(new SecretKeySpec(oldSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"), SignatureAlgorithm.HS256)
                .compact();

        assertThat(new JwTUtil("", 1).extractUsername(forged)).isNull();
        assertThat(new JwTUtil(null, 1).extractUsername(forged)).isNull();
    }

    @Test
    void missingOrShortSecretStillIssuesWorkingTokens() {
        JwTUtil jwt = new JwTUtil("short", 1);

        assertThat(jwt.extractUsername(jwt.generateToken("arpit"))).isEqualTo("arpit");
        // Each instance gets its own random key.
        assertThat(new JwTUtil("short", 1).extractUsername(jwt.generateToken("arpit"))).isNull();
    }

    @Test
    void rejectsExpiredTamperedAndGarbageTokens() {
        JwTUtil expired = new JwTUtil(SECRET, -1);
        assertThat(expired.extractUsername(expired.generateToken("arpit"))).isNull();

        JwTUtil jwt = new JwTUtil(SECRET, 1);
        String token = jwt.generateToken("arpit");
        String[] parts = token.substring(7).split("\\.");
        String otherPayload = jwt.generateToken("someone-else").substring(7).split("\\.")[1];
        assertThat(jwt.extractUsername(parts[0] + "." + otherPayload + "." + parts[2])).isNull();

        assertThat(jwt.extractUsername("not-a-jwt")).isNull();
        assertThat(jwt.extractUsername("")).isNull();
        assertThat(jwt.extractUsername(null)).isNull();
    }

    @Test
    void rejectsUnsignedTokens() {
        String unsigned = Jwts.builder().setSubject("arpit").compact();

        assertThat(new JwTUtil(SECRET, 1).extractUsername(unsigned)).isNull();
    }
}
