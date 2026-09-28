package com.expenses.Expense_Api.notify;

import com.expenses.Expense_Api.DTO.NotificationRequest;
import com.expenses.Expense_Api.DTO.NotificationView;
import com.expenses.Expense_Api.exception.ApiException;
import com.expenses.Expense_Api.model.NotificationSettings;
import com.expenses.Expense_Api.model.User;
import com.expenses.Expense_Api.repository.NotificationSettingsRepository;
import com.expenses.Expense_Api.repository.UserRepository;
import com.expenses.Expense_Api.services.UserServicies;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class NotificationService {
    private final NotificationSettingsRepository repository;
    private final UserRepository userRepository;
    private final UserServicies userServicies;
    private final EmailSender emailSender;
    private final String appUrl;
    private final Map<String, Instant> lastTest = new ConcurrentHashMap<>();
    private final Clock clock = Clock.systemUTC();

    public NotificationService(NotificationSettingsRepository repository, UserRepository userRepository,
                               UserServicies userServicies, EmailSender emailSender,
                               @Value("${app.url:https://expensify-psi.vercel.app}") String appUrl) {
        this.repository = repository;
        this.userRepository = userRepository;
        this.userServicies = userServicies;
        this.emailSender = emailSender;
        this.appUrl = appUrl;
    }

    public String appUrl() {
        return appUrl;
    }

    public boolean emailReady() {
        return emailSender.isEnabled();
    }

    /** Saved settings, or defaults (all on, the account's email) for users who never opened the page. */
    public NotificationSettings settingsFor(String userId) {
        return repository.findByUserId(userId).orElseGet(() -> {
            NotificationSettings s = new NotificationSettings();
            s.setUserId(userId);
            s.setEmail(userRepository.findById(userId).map(User::getEmail).filter(e -> !e.isBlank()).orElse(null));
            return s;
        });
    }

    public NotificationView get() {
        return NotificationView.of(settingsFor(userServicies.currentUser().getId()), emailReady());
    }

    public NotificationView save(NotificationRequest request) {
        String userId = userServicies.currentUser().getId();
        NotificationSettings s = settingsFor(userId);
        s.setEmail(request.email() == null || request.email().isBlank() ? null : request.email().trim());
        s.setWeeklySummary(request.weeklySummary());
        s.setLimitAlerts(request.limitAlerts());
        s.setDueReminders(request.dueReminders());
        return NotificationView.of(repository.save(s), emailReady());
    }

    public void sendTest() {
        String userId = userServicies.currentUser().getId();
        NotificationSettings s = settingsFor(userId);
        if (!emailReady()) throw ApiException.badRequest("Emails are not set up on the server yet");
        if (s.getEmail() == null) throw ApiException.badRequest("Add an email address first");
        Instant now = clock.instant();
        Instant previous = lastTest.get(userId);
        if (previous != null && previous.plusSeconds(60).isAfter(now)) {
            throw ApiException.tooManyRequests("Wait a minute before sending another test email");
        }
        lastTest.put(userId, now);
        Emails.Email email = Emails.test(appUrl);
        EmailSender.Result result = emailSender.deliver(s.getEmail(), email.subject(), email.html(), email.text());
        if (!result.sent()) {
            // Let the user try again straight away after fixing the address.
            lastTest.remove(userId);
            throw new ApiException(org.springframework.http.HttpStatus.BAD_GATEWAY, result.reason());
        }
    }

    /** Sends to the user's address when emails are set up and they have one. */
    public boolean send(NotificationSettings s, Emails.Email email) {
        return Optional.ofNullable(s.getEmail()).map(to -> emailSender.send(to, email.subject(), email.html(), email.text())).orElse(false);
    }

    public NotificationSettings save(NotificationSettings s) {
        return repository.save(s);
    }
}
