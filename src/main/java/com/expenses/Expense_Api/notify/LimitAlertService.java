package com.expenses.Expense_Api.notify;

import com.expenses.Expense_Api.DTO.BudgetStatus;
import com.expenses.Expense_Api.model.NotificationSettings;
import com.expenses.Expense_Api.services.BudgetService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Emails the user the moment an expense takes a limit to its warning level or over it, once per limit per month. */
@Service
public class LimitAlertService {
    private static final Logger log = LoggerFactory.getLogger(LimitAlertService.class);

    private final BudgetService budgetService;
    private final NotificationService notifications;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "limit-alerts");
        t.setDaemon(true);
        return t;
    });

    public LimitAlertService(@Lazy BudgetService budgetService, NotificationService notifications) {
        this.budgetService = budgetService;
        this.notifications = notifications;
    }

    /** Checks in the background, so saving an expense is never slowed down or broken by email. */
    public void expenseChanged(String userId, LocalDateTime date) {
        if (userId == null || !notifications.emailReady()) return;
        YearMonth month = YearMonth.from(date == null ? LocalDateTime.now() : date);
        executor.execute(() -> {
            try {
                check(userId, month, LocalDate.now());
            } catch (RuntimeException e) {
                log.warn("Limit alert check failed: {}", e.getMessage());
            }
        });
    }

    /** Returns the alerts that were emailed. */
    public synchronized List<String> check(String userId, YearMonth month, LocalDate today) {
        NotificationSettings settings = notifications.settingsFor(userId);
        if (!settings.isLimitAlerts() || settings.getEmail() == null || !notifications.emailReady()) return List.of();

        BudgetStatus status = budgetService.statusFor(userId, month, today);
        Set<String> sent = new LinkedHashSet<>(settings.getSentAlerts());
        List<String> keys = new ArrayList<>();
        List<String> messages = new ArrayList<>();
        collect(month, "Monthly budget", status.level(), keys, sent);
        collect(month, "Day-to-day", status.spendableLevel(), keys, sent);
        for (BudgetStatus.CategoryStatus c : status.categories()) collect(month, c.category(), c.level(), keys, sent);
        if (keys.isEmpty()) return List.of();

        for (String key : keys) {
            String[] parts = key.split("\\|", 3);
            String scope = parts[1];
            messages.add(status.alerts().stream()
                    .filter(a -> a.startsWith(scope) || (scope.equals("Monthly budget") && a.contains("monthly budget"))
                            || (scope.equals("Day-to-day") && a.startsWith("Day-to-day")))
                    .findFirst()
                    .orElse(scope + (parts[2].equals("over") ? " is over its limit" : " is close to its limit")));
        }
        Emails.Email email = Emails.limitAlert(messages, status, notifications.appUrl());
        if (!notifications.send(settings, email)) return List.of();

        // Keep only this and last month's markers so the list stays small.
        String keep = month.minusMonths(1).toString();
        sent.removeIf(k -> k.substring(0, 7).compareTo(keep) < 0);
        sent.addAll(keys);
        settings.setSentAlerts(sent);
        notifications.save(settings);
        return messages;
    }

    private static void collect(YearMonth month, String scope, String level, List<String> keys, Set<String> sent) {
        if (!level.equals("warning") && !level.equals("over")) return;
        String key = month + "|" + scope + "|" + level;
        // A warning after an "over" email for the same limit adds nothing, so skip it.
        String overKey = month + "|" + scope + "|over";
        if (sent.contains(key) || (level.equals("warning") && sent.contains(overKey))) return;
        keys.add(key);
    }
}
