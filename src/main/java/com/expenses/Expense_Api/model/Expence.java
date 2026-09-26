package com.expenses.Expense_Api.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "Expence")
public class Expence {
    @Id
    private String id;

    @NotBlank(message = "Title cannot be empty")
    @Size(max = 100, message = "Title must be at most 100 characters")
    private String title;

    /**
     * Optional free-text note. Older records stored the category here, so
     * {@link #getCategory()} falls back to it when no category is set.
     */
    @Size(max = 500, message = "Note must be at most 500 characters")
    private String description;

    @Size(max = 40, message = "Category must be at most 40 characters")
    private String category;

    @Positive(message = "Amount must be greater than zero")
    @Max(value = 1_000_000_000, message = "Amount is too large")
    private double amount;

    /** When the money was spent. Defaults to now when the client sends none. */
    private LocalDateTime date;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private String userId;

    /** Set when the expense was logged automatically from a recurring payment. */
    private String recurringId;

    public Expence() {}

    public Expence(String title, String description, double amount, LocalDateTime date, String userId) {
        this.title = title;
        this.description = description;
        this.amount = amount;
        this.date = date;
        this.userId = userId;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getCategory() {
        if (category != null && !category.isBlank()) return category;
        if (description != null && !description.isBlank()) return description;
        return "Other";
    }
    public void setCategory(String category) { this.category = category; }

    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }

    public LocalDateTime getDate() { return date; }
    public void setDate(LocalDateTime date) { this.date = date; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getRecurringId() { return recurringId; }
    public void setRecurringId(String recurringId) { this.recurringId = recurringId; }
}
