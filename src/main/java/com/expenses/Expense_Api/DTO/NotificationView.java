package com.expenses.Expense_Api.DTO;

import com.expenses.Expense_Api.model.NotificationSettings;

/** emailReady is false until the server has an email provider key, so the app can say emails are off. */
public record NotificationView(String email, boolean weeklySummary, boolean limitAlerts, boolean dueReminders,
                               boolean emailReady) {
    public static NotificationView of(NotificationSettings s, boolean emailReady) {
        return new NotificationView(s.getEmail(), s.isWeeklySummary(), s.isLimitAlerts(), s.isDueReminders(), emailReady);
    }
}
