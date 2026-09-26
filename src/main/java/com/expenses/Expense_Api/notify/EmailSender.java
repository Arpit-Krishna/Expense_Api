package com.expenses.Expense_Api.notify;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * Sends email through Resend (resend.com, free tier: 100 emails a day). Emails stay off until
 * RESEND_API_KEY is set. Without a verified domain Resend only delivers to the address the Resend
 * account was created with, which is fine for a personal tracker.
 */
@Component
public class EmailSender {
    private static final Logger log = LoggerFactory.getLogger(EmailSender.class);

    private final String apiKey;
    private final String from;
    private final RestClient client;

    public EmailSender(@Value("${app.mail.resend-api-key:}") String apiKey,
                       @Value("${app.mail.from:Expensify <onboarding@resend.dev>}") String from,
                       @Value("${app.mail.resend-url:https://api.resend.com}") String baseUrl) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.from = from;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(20_000);
        this.client = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    public boolean isEnabled() {
        return !apiKey.isEmpty();
    }

    /** Returns true when the provider accepted the email. Never throws. */
    public boolean send(String to, String subject, String html, String text) {
        if (!isEnabled() || to == null || to.isBlank()) return false;
        try {
            client.post().uri("/emails")
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("from", from, "to", List.of(to), "subject", subject, "html", html, "text", text))
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (RuntimeException e) {
            // The message never includes the key; it is only sent as a header.
            log.warn("Email '{}' could not be sent: {}", subject, e.getMessage());
            return false;
        }
    }
}
