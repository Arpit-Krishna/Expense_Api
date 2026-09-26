package com.expenses.Expense_Api.notify;

import com.expenses.Expense_Api.DTO.BudgetStatus;
import com.expenses.Expense_Api.model.Expence;
import com.expenses.Expense_Api.model.NotificationSettings;
import com.expenses.Expense_Api.model.RecurringPayment;
import com.expenses.Expense_Api.model.User;
import com.expenses.Expense_Api.repository.ExpensesRepository;
import com.expenses.Expense_Api.repository.RecurringPaymentRepository;
import com.expenses.Expense_Api.repository.UserRepository;
import com.expenses.Expense_Api.services.BudgetService;
import com.expenses.Expense_Api.services.RecurringService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Runs every 15 minutes (the keep-alive ping stops Render from sleeping). Each job remembers what it
 * already did, so a run that repeats, or catches up after the server slept, never sends twice.
 * Times are in the app's zone (Asia/Kolkata by default).
 */
@Component
public class NotificationScheduler {
    private static final Logger log = LoggerFactory.getLogger(NotificationScheduler.class);
    static final int REMINDER_HOUR = 8;
    static final int WEEKLY_HOUR = 9;

    private final RecurringService recurringService;
    private final RecurringPaymentRepository recurringRepository;
    private final ExpensesRepository expensesRepository;
    private final UserRepository userRepository;
    private final BudgetService budgetService;
    private final NotificationService notifications;
    private final boolean enabled;

    public NotificationScheduler(RecurringService recurringService, RecurringPaymentRepository recurringRepository,
                                 ExpensesRepository expensesRepository, UserRepository userRepository,
                                 BudgetService budgetService, NotificationService notifications,
                                 @Value("${app.scheduler.enabled:true}") boolean enabled) {
        this.recurringService = recurringService;
        this.recurringRepository = recurringRepository;
        this.expensesRepository = expensesRepository;
        this.userRepository = userRepository;
        this.budgetService = budgetService;
        this.notifications = notifications;
        this.enabled = enabled;
    }

    /** Catch up soon after a restart, in the background so startup never waits on the database. */
    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        if (!enabled) return;
        Thread t = new Thread(() -> {
            try {
                Thread.sleep(30_000);
                tick();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "scheduler-catch-up");
        t.setDaemon(true);
        t.start();
    }

    @Scheduled(cron = "0 */15 * * * *")
    public void scheduled() {
        if (enabled) tick();
    }

    synchronized void tick() {
        run(LocalDateTime.now());
    }

    void run(LocalDateTime now) {
        LocalDate today = now.toLocalDate();
        try {
            int posted = recurringService.postAllDue(today);
            if (posted > 0) log.info("Logged {} recurring payment(s)", posted);
        } catch (RuntimeException e) {
            log.warn("Recurring payments run failed: {}", e.getMessage());
            return; // Most likely the database is down; try again next time.
        }
        if (!notifications.emailReady()) return;
        if (now.getHour() >= REMINDER_HOUR) runSafely("reminders", () -> sendReminders(today));
        if (today.getDayOfWeek() == DayOfWeek.SUNDAY && now.getHour() >= WEEKLY_HOUR) {
            runSafely("weekly summaries", () -> sendWeeklySummaries(today));
        }
    }

    /** One email per user listing payments due tomorrow. */
    public void sendReminders(LocalDate today) {
        LocalDate tomorrow = today.plusDays(1);
        YearMonth month = YearMonth.from(tomorrow);
        Map<String, List<RecurringPayment>> dueByUser = recurringRepository.findByActiveTrue().stream()
                .filter(p -> p.dueDate(month).equals(tomorrow) && !p.postedFor(month)
                        && !tomorrow.toString().equals(p.getLastRemindedFor()))
                .collect(Collectors.groupingBy(RecurringPayment::getUserId));
        dueByUser.forEach((userId, due) -> {
            NotificationSettings s = notifications.settingsFor(userId);
            if (!s.isDueReminders() || s.getEmail() == null) return;
            if (notifications.send(s, Emails.dueTomorrow(due, tomorrow, notifications.appUrl()))) {
                due.forEach(p -> p.setLastRemindedFor(tomorrow.toString()));
                recurringRepository.saveAll(due);
            }
        });
    }

    public void sendWeeklySummaries(LocalDate today) {
        for (User user : userRepository.findAll()) {
            NotificationSettings s = notifications.settingsFor(user.getId());
            if (!s.isWeeklySummary() || s.getEmail() == null || today.toString().equals(s.getLastWeeklySummary())) continue;
            try {
                Emails.Email email = weeklyEmail(user.getId(), today);
                if (notifications.send(s, email)) {
                    s.setLastWeeklySummary(today.toString());
                    notifications.save(s);
                }
            } catch (RuntimeException e) {
                log.warn("Weekly summary for {} failed: {}", user.getId(), e.getMessage());
            }
        }
    }

    Emails.Email weeklyEmail(String userId, LocalDate today) {
        List<Expence> week = expensesRepository.findByUserIdAndDateBetween(userId,
                today.minusDays(6).atStartOfDay().minusNanos(1), today.plusDays(1).atStartOfDay());
        List<String> fixed = budgetService.budgetFor(userId).getFixedCategories();
        double spent = week.stream().mapToDouble(Expence::getAmount).sum();
        double dayToDay = week.stream().filter(e -> !fixed.contains(e.getCategory())).mapToDouble(Expence::getAmount).sum();
        // Seen from Monday, so "safe to spend" covers the coming week.
        LocalDate monday = today.plusDays(1);
        BudgetStatus next = budgetService.statusFor(userId, YearMonth.from(monday), monday);
        return Emails.weekly(today, spent, dayToDay, next, notifications.appUrl());
    }

    private static void runSafely(String what, Runnable job) {
        try {
            job.run();
        } catch (RuntimeException e) {
            log.warn("Sending {} failed: {}", what, e.getMessage());
        }
    }
}
