package com.expenses.Expense_Api.notify;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Sends email through Resend (resend.com, free tier: 100 emails a day). Emails stay off until
 * RESEND_API_KEY is set. With the default sender (onboarding@resend.dev) Resend only delivers to the
 * address the Resend account was created with. To email any address, verify a domain in Resend and set
 * MAIL_FROM to an address on it, e.g. "Expensify <alerts@yourdomain.com>".
 */
@Component
public class EmailSender {
    private static final Logger log = LoggerFactory.getLogger(EmailSender.class);
    private static final Pattern EMAIL = Pattern.compile("[\\w.+-]+@[\\w.-]+");

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

    /** Whether the provider accepted the email, and if not, a reason a person can act on. */
    public record Result(boolean sent, String reason) {
        static Result ok() { return new Result(true, null); }
        static Result failed(String reason) { return new Result(false, reason); }
    }

    /** Returns true when the provider accepted the email. Never throws. */
    public boolean send(String to, String subject, String html, String text) {
        return deliver(to, subject, html, text).sent();
    }

    /** Like send, but explains a failure. Never throws. */
    public Result deliver(String to, String subject, String html, String text) {
        if (!isEnabled()) return Result.failed("Emails are not set up on the server yet");
        if (to == null || to.isBlank()) return Result.failed("Add an email address first");
        try {
            client.post().uri("/emails")
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("from", from, "to", List.of(to), "subject", subject, "html", html, "text", text))
                    .retrieve()
                    .toBodilessEntity();
            return Result.ok();
        } catch (RestClientResponseException e) {
            String providerMessage = providerMessage(e.getResponseBodyAsString());
            // The key is only ever sent as a header, so it never appears in these messages.
            log.warn("Resend refused email '{}' with HTTP {}: {}", subject, e.getStatusCode().value(), providerMessage);
            return Result.failed(explain(e.getStatusCode().value(), providerMessage));
        } catch (RuntimeException e) {
            log.warn("Email '{}' could not be sent: {}", subject, e.getMessage());
            return Result.failed("Could not reach the email provider. Try again in a minute.");
        }
    }

    private static String providerMessage(String body) {
        try {
            JsonNode node = new ObjectMapper().readTree(body);
            String message = node.path("message").asText("");
            return message.isBlank() ? body : message;
        } catch (Exception e) {
            return body == null ? "" : body;
        }
    }

    /** Turns Resend's answer into advice. Addresses are hidden, since the provider account belongs to the server owner. */
    static String explain(int status, String providerMessage) {
        String m = providerMessage == null ? "" : providerMessage.toLowerCase(Locale.ROOT);
        if (m.contains("testing emails") || m.contains("verify a domain") || m.contains("own email address")) {
            return "Resend only sends to the email address your Resend account was created with until you verify a "
                    + "domain. Use that address here, or verify a domain in Resend and set MAIL_FROM on the server.";
        }
        if (status == 401 || status == 403 && m.contains("api key")) {
            return "Resend rejected the API key. Check RESEND_API_KEY on the server.";
        }
        if (m.contains("domain") && m.contains("not verified")) {
            return "The sender domain in MAIL_FROM is not verified in Resend yet.";
        }
        if (status == 429) return "Resend's sending limit was reached. Try again later.";
        String cleaned = providerMessage == null ? "" : EMAIL.matcher(providerMessage).replaceAll("[address]").trim();
        if (cleaned.length() > 200) cleaned = cleaned.substring(0, 200) + "...";
        return cleaned.isEmpty() ? "Resend did not accept the email (HTTP " + status + ")." : "Resend did not accept the email: " + cleaned;
    }
}
