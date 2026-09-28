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

    private String start(int status, AtomicReference<String> body, AtomicReference<String> auth) throws IOException {
        return start(status, "{\"id\":\"1\"}", body, auth);
    }

    private String start(int status, String response, AtomicReference<String> body, AtomicReference<String> auth) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/emails", exchange -> {
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            auth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] out = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, out.length);
            exchange.getResponseBody().write(out);
            exchange.close();
        });
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stop() {
        if (server != null) server.stop(0);
    }

    @Test
    void postsTheEmailToResend() throws IOException {
        AtomicReference<String> body = new AtomicReference<>();
        AtomicReference<String> auth = new AtomicReference<>();
        EmailSender sender = new EmailSender("re_test_key", "Expensify <onboarding@resend.dev>", start(200, body, auth));

        assertThat(sender.send("me@example.com", "Hi", "<p>Hi</p>", "Hi")).isTrue();
        assertThat(auth.get()).isEqualTo("Bearer re_test_key");
        assertThat(body.get()).contains("\"to\":[\"me@example.com\"]").contains("\"subject\":\"Hi\"")
                .contains("\"from\":\"Expensify <onboarding@resend.dev>\"");
    }

    @Test
    void reportsFailureInsteadOfThrowing() throws IOException {
        EmailSender sender = new EmailSender("re_test_key", "x@y.z", start(422, new AtomicReference<>(), new AtomicReference<>()));

        assertThat(sender.send("me@example.com", "Hi", "<p>Hi</p>", "Hi")).isFalse();
    }

    @Test
    void staysOffWithoutAKey() {
        EmailSender sender = new EmailSender("", "x@y.z", "http://127.0.0.1:1");

        assertThat(sender.isEnabled()).isFalse();
        assertThat(sender.send("me@example.com", "Hi", "<p>Hi</p>", "Hi")).isFalse();
    }

    @Test
    void explainsResendsTestingOnlyRule() throws IOException {
        String resend = "{\"statusCode\":403,\"name\":\"validation_error\",\"message\":\"You can only send testing emails to your own "
                + "email address (owner@example.com). To send emails to other recipients, please verify a domain at resend.com/domains, "
                + "and change the `from` address to an email using this domain.\"}";
        EmailSender sender = new EmailSender("re_test_key", "x@y.z", start(403, resend, new AtomicReference<>(), new AtomicReference<>()));

        EmailSender.Result result = sender.deliver("me@example.com", "Hi", "<p>Hi</p>", "Hi");

        assertThat(result.sent()).isFalse();
        assertThat(result.reason()).startsWith("Resend only sends to the email address your Resend account was created with")
                .doesNotContain("owner@example.com");
    }

    @Test
    void explainsOtherFailuresWithoutLeakingAddresses() {
        assertThat(EmailSender.explain(401, "API key is invalid")).contains("RESEND_API_KEY");
        assertThat(EmailSender.explain(429, "Too many requests")).contains("limit");
        assertThat(EmailSender.explain(422, "Invalid `to` field: bad@example.com")).isEqualTo("Resend did not accept the email: Invalid `to` field: [address]");
    }
}
