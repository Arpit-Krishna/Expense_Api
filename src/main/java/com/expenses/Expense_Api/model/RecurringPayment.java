package com.expenses.Expense_Api.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;

/** A payment that goes out every month (EMI, rent...). The API logs it as an expense on its due day. */
@Document(collection = "RecurringPayment")
public class RecurringPayment {
    @Id
    private String id;
    private String userId;
    private String title;
    private String category;
    private double amount;
    /** 1-31. In shorter months a day past the end falls on the last day. */
    private int dayOfMonth;
    private boolean active = true;
    private String note;
    /** Month (YYYY-MM) this payment was last logged for, so it is logged at most once a month. */
    private String lastPostedMonth;
    /** Due date (YYYY-MM-DD) the last reminder email was sent for. */
    private String lastRemindedFor;
    private LocalDateTime createdAt;

    /** The due date in the given month. */
    public LocalDate dueDate(YearMonth month) {
        return month.atDay(Math.min(Math.max(dayOfMonth, 1), month.lengthOfMonth()));
    }

    public boolean postedFor(YearMonth month) {
        return month.toString().equals(lastPostedMonth);
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }

    public int getDayOfMonth() { return dayOfMonth; }
    public void setDayOfMonth(int dayOfMonth) { this.dayOfMonth = dayOfMonth; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public String getLastPostedMonth() { return lastPostedMonth; }
    public void setLastPostedMonth(String lastPostedMonth) { this.lastPostedMonth = lastPostedMonth; }

    public String getLastRemindedFor() { return lastRemindedFor; }
    public void setLastRemindedFor(String lastRemindedFor) { this.lastRemindedFor = lastRemindedFor; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
