package com.expenses.Expense_Api.notify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Sends email through Brevo (brevo.com) or Resend (resend.com). Emails stay off until one key is set.
 *
 * Brevo (BREVO_API_KEY, preferred): free for 300 emails a day and needs no domain. MAIL_FROM must be
 * a sender verified in Brevo (Senders, Domains & Dedicated IPs > Senders), e.g. "Expensify <you@gmail.com>".
 *
 * Resend (RESEND_API_KEY): with the default sender (onboarding@resend.dev) it only delivers to the address
 * the Resend account was created with; to email anyone, verify a domain and set MAIL_FROM to an address on it.
 */
@Component
public class EmailSender {
    private static final Logger log = LoggerFactory.getLogger(EmailSender.class);
    private static final Pattern EMAIL = Pattern.compile("[\\w.+-]+@[\\w.-]+");
    private static final Pattern NAMED = Pattern.compile("^\\s*(.*?)\\s*<\\s*([^>]+?)\\s*>\\s*$");
    static final String RESEND_TEST_SENDER = "Expensify <onboarding@resend.dev>";

    enum Provider { BREVO, RESEND, NONE }

    private final Provider provider;
    private final String apiKey;
    private final String senderName;
    private final String senderEmail;
    private final RestClient client;

    @Autowired
    public EmailSender(@Value("${app.mail.brevo-api-key:}") String brevoKey,
                       @Value("${app.mail.resend-api-key:}") String resendKey,
                       @Value("${app.mail.from:}") String from,
                       @Value("${app.mail.brevo-url:https://api.brevo.com}") String brevoUrl,
                       @Value("${app.mail.resend-url:https://api.resend.com}") String resendUrl) {
        String brevo = trim(brevoKey);
        String resend = trim(resendKey);
        String sender = trim(from);
        if (!brevo.isEmpty()) {
            provider = sender.isEmpty() ? Provider.NONE : Provider.BREVO;
            apiKey = brevo;
            if (sender.isEmpty()) log.warn("BREVO_API_KEY is set but MAIL_FROM is not. Set MAIL_FROM to a sender verified in Brevo.");
        } else if (!resend.isEmpty()) {
            provider = Provider.RESEND;
            apiKey = resend;
            if (sender.isEmpty()) sender = RESEND_TEST_SENDER;
        } else {
            provider = Provider.NONE;
            apiKey = "";
        }
        Matcher m = NAMED.matcher(sender);
        senderName = m.matches() && !m.group(1).isBlank() ? m.group(1) : "Expensify";
        senderEmail = m.matches() ? m.group(2) : sender;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(20_000);
        client = RestClient.builder().baseUrl(provider == Provider.BREVO ? brevoUrl : resendUrl).requestFactory(factory).build();
        if (provider != Provider.NONE) log.info("Emails go out through {} from {}", provider, senderEmail);
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }

    public boolean isEnabled() {
        return provider != Provider.NONE;
    }

    Provider provider() {
        return provider;
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
            if (provider == Provider.BREVO) {
                client.post().uri("/v3/smtp/email")
                        .header("api-key", apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .body(Map.of("sender", Map.of("name", senderName, "email", senderEmail),
                                "to", List.of(Map.of("email", to)),
                                "subject", subject, "htmlContent", html, "textContent", text))
                        .retrieve()
                        .toBodilessEntity();
            } else {
                client.post().uri("/emails")
                        .header("Authorization", "Bearer " + apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("from", senderName + " <" + senderEmail + ">", "to", List.of(to),
                                "subject", subject, "html", html, "text", text))
                        .retrieve()
                        .toBodilessEntity();
            }
            return Result.ok();
        } catch (RestClientResponseException e) {
            String providerMessage = providerMessage(e.getResponseBodyAsString());
            // The key is only ever sent as a header, so it never appears in these messages.
            log.warn("{} refused email '{}' with HTTP {}: {}", provider, subject, e.getStatusCode().value(), providerMessage);
            return Result.failed(explain(provider, e.getStatusCode().value(), providerMessage));
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

    /** Turns the provider's answer into advice. Addresses are hidden, since the provider account belongs to the server owner. */
    static String explain(Provider provider, int status, String providerMessage) {
        String m = providerMessage == null ? "" : providerMessage.toLowerCase(Locale.ROOT);
        String name = provider == Provider.BREVO ? "Brevo" : "Resend";
        if (provider == Provider.BREVO) {
            if (m.contains("sender") && (m.contains("not valid") || m.contains("not verified") || m.contains("validate"))) {
                return "Brevo does not accept the sender address yet. Verify it in Brevo under Senders, then make "
                        + "MAIL_FROM on the server match it exactly.";
            }
            if (m.contains("not yet activated") || m.contains("account") && m.contains("activat")) {
                return "Your Brevo account is not activated for sending yet. Finish the activation Brevo asks for, then try again.";
            }
            if (m.contains("ip") && m.contains("authorised") || m.contains("unrecognised ip") || m.contains("authorized ips")) {
                return "Brevo blocked the server's IP address. In Brevo, open Security > Authorised IPs and turn off the IP restriction.";
            }
            if (status == 401 || m.contains("key not found")) return "Brevo rejected the API key. Check BREVO_API_KEY on the server.";
        } else {
            if (m.contains("testing emails") || m.contains("verify a domain") || m.contains("own email address")) {
                return "Resend only sends to the email address your Resend account was created with until you verify a "
                        + "domain. Use that address here, or verify a domain in Resend and set MAIL_FROM on the server.";
            }
            if (status == 401 || status == 403 && m.contains("api key")) {
                return "Resend rejected the API key. Check RESEND_API_KEY on the server.";
            }
            if (m.contains("domain") && m.contains("not verified")) return "The sender domain in MAIL_FROM is not verified in Resend yet.";
        }
        if (status == 429) return name + "'s sending limit was reached. Try again later.";
        String cleaned = providerMessage == null ? "" : EMAIL.matcher(providerMessage).replaceAll("[address]").trim();
        if (cleaned.length() > 200) cleaned = cleaned.substring(0, 200) + "...";
        return cleaned.isEmpty() ? name + " did not accept the email (HTTP " + status + ")." : name + " did not accept the email: " + cleaned;
    }
}
