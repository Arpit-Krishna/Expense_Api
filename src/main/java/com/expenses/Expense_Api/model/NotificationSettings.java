package com.expenses.Expense_Api.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.LinkedHashSet;
import java.util.Set;

/** One document per user: where to send emails and which ones they want. */
@Document(collection = "NotificationSettings")
public class NotificationSettings {
    @Id
    private String id;
    private String userId;
    private String email;
    private boolean weeklySummary = true;
    private boolean limitAlerts = true;
    private boolean dueReminders = true;
    /** Date (YYYY-MM-DD) of the last weekly summary, so each week gets one. */
    private String lastWeeklySummary;
    /** Limit alerts already emailed, as "2026-09|Food|over", so each one is sent once a month. */
    private Set<String> sentAlerts = new LinkedHashSet<>();

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public boolean isWeeklySummary() { return weeklySummary; }
    public void setWeeklySummary(boolean weeklySummary) { this.weeklySummary = weeklySummary; }

    public boolean isLimitAlerts() { return limitAlerts; }
    public void setLimitAlerts(boolean limitAlerts) { this.limitAlerts = limitAlerts; }

    public boolean isDueReminders() { return dueReminders; }
    public void setDueReminders(boolean dueReminders) { this.dueReminders = dueReminders; }

    public String getLastWeeklySummary() { return lastWeeklySummary; }
    public void setLastWeeklySummary(String lastWeeklySummary) { this.lastWeeklySummary = lastWeeklySummary; }

    public Set<String> getSentAlerts() { return sentAlerts == null ? new LinkedHashSet<>() : sentAlerts; }
    public void setSentAlerts(Set<String> sentAlerts) { this.sentAlerts = sentAlerts; }
}
