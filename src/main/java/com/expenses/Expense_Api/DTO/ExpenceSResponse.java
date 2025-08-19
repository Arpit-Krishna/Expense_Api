package com.expenses.Expense_Api.DTO;

import java.time.LocalDateTime;


public class ExpenceSResponse {
    private String id;
    private String title;
    private String description;
    private double amount;
    private LocalDateTime date;
    private String userId; // only expose userId, not the whole User object

    public ExpenceSResponse() {}

    public ExpenceSResponse(String id, String title, String description, double amount, LocalDateTime date, String userId) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.amount = amount;
        this.date = date;
        this.userId = userId;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public double getAmount() {
        return amount;
    }

    public void setAmount(double amount) {
        this.amount = amount;
    }

    public LocalDateTime getDate() {
        return date;
    }

    public void setDate(LocalDateTime date) {
        this.date = date;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }
}
