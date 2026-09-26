package com.expenses.Expense_Api.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** One budget document per user: an overall monthly limit plus optional per-category monthly limits. */
@Document(collection = "Budget")
public class Budget {
    @Id
    private String id;

    @Indexed(unique = true)
    private String userId;

    /** Overall monthly spending limit. 0 means no overall limit. */
    private double monthlyLimit;

    /** Category name -> monthly limit. */
    private Map<String, Double> categoryLimits = new LinkedHashMap<>();

    /** Monthly take-home pay, used to show how much is left to save. 0 means not set. */
    private double monthlyIncome;

    /** Amount the user plans to move to savings each month. */
    private double savingsTarget;

    /**
     * Categories paid automatically every month (EMI, rent...). Limits on the other
     * categories add up to the "spendable" budget the dashboard tracks week by week.
     */
    private List<String> fixedCategories = new ArrayList<>();

    /** Warn once spending reaches this percentage of a limit (1-100). */
    private int alertThreshold = 80;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public double getMonthlyLimit() { return monthlyLimit; }
    public void setMonthlyLimit(double monthlyLimit) { this.monthlyLimit = monthlyLimit; }

    public Map<String, Double> getCategoryLimits() { return categoryLimits; }
    public void setCategoryLimits(Map<String, Double> categoryLimits) { this.categoryLimits = categoryLimits; }

    public double getMonthlyIncome() { return monthlyIncome; }
    public void setMonthlyIncome(double monthlyIncome) { this.monthlyIncome = monthlyIncome; }

    public double getSavingsTarget() { return savingsTarget; }
    public void setSavingsTarget(double savingsTarget) { this.savingsTarget = savingsTarget; }

    public List<String> getFixedCategories() { return fixedCategories == null ? new ArrayList<>() : fixedCategories; }
    public void setFixedCategories(List<String> fixedCategories) { this.fixedCategories = fixedCategories; }

    public int getAlertThreshold() { return alertThreshold; }
    public void setAlertThreshold(int alertThreshold) { this.alertThreshold = alertThreshold; }
}
