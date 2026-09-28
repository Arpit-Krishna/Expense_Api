package com.expenses.Expense_Api.notify;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class EmailSenderTest {

    private HttpServer server;
    private final AtomicReference<String> body = new AtomicReference<>();
    private final AtomicReference<String> path = new AtomicReference<>();
    private final AtomicReference<String> brevoKey = new AtomicReference<>();
    private final AtomicReference<String> bearer = new AtomicReference<>();

    private String start(int status, String response) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            path.set(exchange.getRequestURI().getPath());
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            brevoKey.set(exchange.getRequestHeaders().getFirst("api-key"));
            bearer.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] out = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, out.length);
            exchange.getResponseBody().write(out);
            exchange.close();
        });
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private static EmailSender brevo(String url, String from) {
        return new EmailSender("xkeysib-test", "", from, url, "http://unused");
    }

    private static EmailSender resend(String url) {
        return new EmailSender("", "re_test_key", "", "http://unused", url);
    }

    @AfterEach
    void stop() {
        if (server != null) server.stop(0);
    }

    @Test
    void postsToBrevoFromTheVerifiedSender() throws IOException {
        EmailSender sender = brevo(start(201, "{\"messageId\":\"<1@smtp>\"}"), "Expensify <me@gmail.com>");

        assertThat(sender.deliver("friend@example.com", "Hi", "<p>Hi</p>", "Hi").sent()).isTrue();
        assertThat(path.get()).isEqualTo("/v3/smtp/email");
        assertThat(brevoKey.get()).isEqualTo("xkeysib-test");
        assertThat(bearer.get()).isNull();
        assertThat(body.get()).contains("\"email\":\"me@gmail.com\"").contains("\"name\":\"Expensify\"")
                .contains("\"to\":[{\"email\":\"friend@example.com\"}]").contains("\"htmlContent\":\"<p>Hi</p>\"")
                .contains("\"textContent\":\"Hi\"");
    }

    @Test
    void brevoPrefersItsKeyAndNeedsASender() {
        assertThat(new EmailSender("xkeysib", "re_key", "me@gmail.com", "a", "b").provider()).isEqualTo(EmailSender.Provider.BREVO);
        EmailSender noSender = new EmailSender("xkeysib", "", "", "a", "b");
        assertThat(noSender.isEnabled()).isFalse();
    }

    @Test
    void explainsBrevoFailures() throws IOException {
        EmailSender sender = brevo(start(400, "{\"code\":\"invalid_parameter\",\"message\":\"Sender is not valid. Please validate your sender or authenticate your domain\"}"),
                "me@gmail.com");
        EmailSender.Result result = sender.deliver("friend@example.com", "Hi", "<p>Hi</p>", "Hi");

        assertThat(result.sent()).isFalse();
        assertThat(result.reason()).startsWith("Brevo does not accept the sender address yet");
        assertThat(EmailSender.explain(EmailSender.Provider.BREVO, 401, "Key not found")).contains("BREVO_API_KEY");
        assertThat(EmailSender.explain(EmailSender.Provider.BREVO, 401,
                "We have detected you are using an unrecognised IP address 1.2.3.4. If you performed this action make sure to add the new IP address in this link: https://app.brevo.com/security/authorised_ips"))
                .contains("Authorised IPs");
        assertThat(EmailSender.explain(EmailSender.Provider.BREVO, 400, "Invalid email address x@y.z"))
                .isEqualTo("Brevo did not accept the email: Invalid email address [address]");
    }

    @Test
    void cleansPastedKeysAndSendsTheCleanKey() throws IOException {
        assertThat(EmailSender.cleanKey("  \"xkeysib-abc\"\n")).isEqualTo("xkeysib-abc");
        assertThat(EmailSender.cleanKey("'xkeysib-abc'")).isEqualTo("xkeysib-abc");
        assertThat(EmailSender.cleanKey("xkeysib-a bc")).isEqualTo("xkeysib-abc");

        EmailSender sender = new EmailSender(" \"xkeysib-test\" ", "", "me@gmail.com", start(201, "{}"), "http://unused");
        assertThat(sender.deliver("friend@example.com", "Hi", "<p>Hi</p>", "Hi").sent()).isTrue();
        assertThat(brevoKey.get()).isEqualTo("xkeysib-test");
    }

    @Test
    void spotsAnSmtpKeyWithoutCallingBrevo() {
        EmailSender sender = new EmailSender("xsmtpsib-abc", "", "me@gmail.com", "http://127.0.0.1:1", "http://unused");

        EmailSender.Result result = sender.deliver("friend@example.com", "Hi", "<p>Hi</p>", "Hi");
        assertThat(result.sent()).isFalse();
        assertThat(result.reason()).contains("SMTP key").contains("xkeysib-");
    }

    @Test
    void rejectedKeyShowsBrevosOwnWords() {
        assertThat(EmailSender.explain(EmailSender.Provider.BREVO, 401, "Key not found"))
                .startsWith("Brevo rejected the API key (Brevo said: Key not found)").contains("xkeysib-");
    }

    @Test
    void postsToResendWithTheTestSenderByDefault() throws IOException {
        EmailSender sender = resend(start(200, "{\"id\":\"1\"}"));

        assertThat(sender.send("me@example.com", "Hi", "<p>Hi</p>", "Hi")).isTrue();
        assertThat(path.get()).isEqualTo("/emails");
        assertThat(bearer.get()).isEqualTo("Bearer re_test_key");
        assertThat(body.get()).contains("\"to\":[\"me@example.com\"]").contains("\"from\":\"Expensify <onboarding@resend.dev>\"");
    }

    @Test
    void explainsResendsTestingOnlyRule() throws IOException {
        String response = "{\"statusCode\":403,\"name\":\"validation_error\",\"message\":\"You can only send testing emails to your own "
                + "email address (owner@example.com). To send emails to other recipients, please verify a domain at resend.com/domains, "
                + "and change the `from` address to an email using this domain.\"}";
        EmailSender.Result result = resend(start(403, response)).deliver("me@example.com", "Hi", "<p>Hi</p>", "Hi");

        assertThat(result.sent()).isFalse();
        assertThat(result.reason()).startsWith("Resend only sends to the email address your Resend account was created with")
                .doesNotContain("owner@example.com");
        assertThat(EmailSender.explain(EmailSender.Provider.RESEND, 401, "API key is invalid")).contains("RESEND_API_KEY");
        assertThat(EmailSender.explain(EmailSender.Provider.RESEND, 429, "Too many requests")).contains("limit");
    }

    @Test
    void staysOffWithoutAKey() {
        EmailSender sender = new EmailSender("", "", "", "http://127.0.0.1:1", "http://127.0.0.1:1");

        assertThat(sender.isEnabled()).isFalse();
        assertThat(sender.deliver("me@example.com", "Hi", "<p>Hi</p>", "Hi").reason()).isEqualTo("Emails are not set up on the server yet");
    }
}
