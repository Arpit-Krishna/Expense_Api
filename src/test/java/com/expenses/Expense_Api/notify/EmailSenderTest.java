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
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/emails", exchange -> {
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            auth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] out = "{\"id\":\"1\"}".getBytes(StandardCharsets.UTF_8);
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
}
