package com.expenses.Expense_Api.DTO;

import java.util.List;

/**
 * Spending against limits for one month. level is "ok", "warning" (at or past the alert threshold)
 * or "over" (past the limit); it is "none" when no limit is set.
 */
public record BudgetStatus(
        String month,
        double monthlyLimit,
        double spent,
        double remaining,
        double percent,
        String level,
        int alertThreshold,
        int daysLeft,
        double projectedSpend,
        double monthlyIncome,
        double savingsTarget,
        /** What is left of income after spending so far (income - spent); 0 when no income is set. */
        double savedSoFar,
        /** Sum of limits on non-fixed categories: the money meant for day-to-day spending. */
        double spendableLimit,
        double spendableSpent,
        double spendablePercent,
        String spendableLevel,
        /** Spendable money left divided by the weeks left in the month. */
        double weeklyAllowance,
        List<String> fixedCategories,
        List<CategoryStatus> categories,
        List<String> alerts
) {
    public record CategoryStatus(String category, double limit, double spent, double remaining, double percent, String level) {}
}
