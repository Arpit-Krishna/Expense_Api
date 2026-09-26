package com.expenses.Expense_Api.services;

import com.expenses.Expense_Api.DTO.BudgetStatus;
import com.expenses.Expense_Api.exception.ApiException;
import com.expenses.Expense_Api.model.Budget;
import com.expenses.Expense_Api.model.Expence;
import com.expenses.Expense_Api.model.User;
import com.expenses.Expense_Api.repository.BudgetRepository;
import com.expenses.Expense_Api.repository.ExpensesRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class BudgetService {

    static final int MAX_CATEGORIES = 50;

    @Autowired
    private BudgetRepository budgetRepository;
    @Autowired
    private ExpensesRepository expensesRepository;
    @Autowired
    private UserServicies userServicies;

    public Budget getBudget() {
        return budgetFor(userServicies.currentUser().getId());
    }

    public Budget budgetFor(String userId) {
        return budgetRepository.findByUserId(userId).orElseGet(() -> {
            Budget empty = new Budget();
            empty.setUserId(userId);
            return empty;
        });
    }

    public Budget saveBudget(Budget input) {
        User user = userServicies.currentUser();
        if (input.getMonthlyLimit() < 0) throw ApiException.badRequest("Monthly limit cannot be negative");
        if (input.getMonthlyIncome() < 0 || input.getSavingsTarget() < 0) {
            throw ApiException.badRequest("Income and savings cannot be negative");
        }
        if (input.getAlertThreshold() < 1 || input.getAlertThreshold() > 100) {
            throw ApiException.badRequest("Alert threshold must be between 1 and 100");
        }

        Map<String, Double> limits = new LinkedHashMap<>();
        if (input.getCategoryLimits() != null && input.getCategoryLimits().size() > MAX_CATEGORIES) {
            throw ApiException.badRequest("A budget can have at most " + MAX_CATEGORIES + " categories");
        }
        if (input.getCategoryLimits() != null) {
            for (Map.Entry<String, Double> e : input.getCategoryLimits().entrySet()) {
                String name = e.getKey() == null ? "" : e.getKey().trim();
                Double value = e.getValue();
                if (name.isEmpty() || value == null || value == 0) continue;
                if (value < 0) throw ApiException.badRequest("Limit for " + name + " cannot be negative");
                if (name.length() > 40) throw ApiException.badRequest("Category names must be at most 40 characters");
                limits.put(name, value);
            }
        }

        Budget budget = budgetRepository.findByUserId(user.getId()).orElseGet(Budget::new);
        budget.setUserId(user.getId());
        budget.setMonthlyLimit(input.getMonthlyLimit());
        budget.setCategoryLimits(limits);
        budget.setAlertThreshold(input.getAlertThreshold());
        budget.setMonthlyIncome(input.getMonthlyIncome());
        budget.setSavingsTarget(input.getSavingsTarget());
        budget.setFixedCategories(input.getFixedCategories().stream()
                .filter(c -> c != null && !c.isBlank()).map(String::trim).distinct().limit(MAX_CATEGORIES).toList());
        return budgetRepository.save(budget);
    }

    public BudgetStatus getStatus(String month, LocalDate today) {
        YearMonth ym;
        try {
            ym = month == null || month.isBlank() ? YearMonth.from(today) : YearMonth.parse(month);
        } catch (DateTimeParseException e) {
            throw ApiException.badRequest("Month must look like 2026-09");
        }
        return statusFor(userServicies.currentUser().getId(), ym, today);
    }

    /** Budget status for any user, for the scheduler and email alerts which run without a login. */
    public BudgetStatus statusFor(String userId, YearMonth ym, LocalDate today) {
        Budget budget = budgetFor(userId);
        List<Expence> expenses = expensesRepository.findByUserIdAndDateBetween(
                userId, ym.atDay(1).atStartOfDay().minusNanos(1), ym.plusMonths(1).atDay(1).atStartOfDay());
        return computeStatus(budget, ym, expenses, today);
    }

    /** Pure calculation, kept separate so it can be unit tested without a database. */
    static BudgetStatus computeStatus(Budget budget, YearMonth ym, List<Expence> expenses, LocalDate today) {
        int threshold = budget.getAlertThreshold();
        double spent = round(expenses.stream().mapToDouble(Expence::getAmount).sum());
        Map<String, Double> byCategory = expenses.stream()
                .collect(Collectors.groupingBy(Expence::getCategory, Collectors.summingDouble(Expence::getAmount)));

        List<String> alerts = new ArrayList<>();
        double limit = budget.getMonthlyLimit();
        String level = level(spent, limit, threshold);
        if (level.equals("over")) {
            alerts.add(String.format("You are over your monthly budget by %.2f", spent - limit));
        } else if (level.equals("warning")) {
            alerts.add(String.format("You have used %.0f%% of your monthly budget", percent(spent, limit)));
        }

        List<String> fixed = budget.getFixedCategories();
        List<BudgetStatus.CategoryStatus> categories = new ArrayList<>();
        Map<String, Double> limits = budget.getCategoryLimits() == null ? Map.of() : budget.getCategoryLimits();
        for (Map.Entry<String, Double> e : limits.entrySet()) {
            double catSpent = round(byCategory.getOrDefault(e.getKey(), 0.0));
            double catLimit = e.getValue();
            String catLevel = level(catSpent, catLimit, threshold);
            // A fixed payment reaching its amount is expected, not a warning.
            if (fixed.contains(e.getKey()) && catLevel.equals("warning")) catLevel = "ok";
            categories.add(new BudgetStatus.CategoryStatus(e.getKey(), catLimit, catSpent,
                    round(catLimit - catSpent), percent(catSpent, catLimit), catLevel));
            if (catLevel.equals("over")) {
                alerts.add(String.format("%s is over budget by %.2f", e.getKey(), catSpent - catLimit));
            } else if (catLevel.equals("warning")) {
                alerts.add(String.format("%s has used %.0f%% of its budget", e.getKey(), percent(catSpent, catLimit)));
            }
        }
        // Categories with spending but no limit still show up, so the breakdown is complete.
        byCategory.forEach((cat, amount) -> {
            if (!limits.containsKey(cat)) {
                categories.add(new BudgetStatus.CategoryStatus(cat, 0, round(amount), 0, 0, "none"));
            }
        });

        double spendableLimit = limits.entrySet().stream()
                .filter(e -> !fixed.contains(e.getKey())).mapToDouble(Map.Entry::getValue).sum();
        double spendableSpent = round(byCategory.entrySet().stream()
                .filter(e -> !fixed.contains(e.getKey())).mapToDouble(Map.Entry::getValue).sum());
        String spendableLevel = level(spendableSpent, spendableLimit, threshold);
        if (spendableLevel.equals("over")) {
            alerts.add(String.format("Day-to-day spending is over its %.2f budget by %.2f", spendableLimit, spendableSpent - spendableLimit));
        } else if (spendableLevel.equals("warning")) {
            alerts.add(String.format("Day-to-day spending has used %.0f%% of its budget", percent(spendableSpent, spendableLimit)));
        }
        double income = budget.getMonthlyIncome();
        double savedSoFar = income > 0 ? round(income - spent) : 0;
        if (income > 0 && budget.getSavingsTarget() > 0 && savedSoFar < budget.getSavingsTarget()) {
            alerts.add(String.format("Spending is now eating into your %.2f savings target", budget.getSavingsTarget()));
        }

        int daysInMonth = ym.lengthOfMonth();
        int daysLeft;
        double projected;
        if (YearMonth.from(today).equals(ym)) {
            int dayOfMonth = today.getDayOfMonth();
            daysLeft = daysInMonth - dayOfMonth;
            // Fixed payments happen once a month, so only day-to-day spending is extrapolated.
            double fixedSpent = fixed.isEmpty() ? 0 : spent - spendableSpent;
            projected = round(fixedSpent + (spent - fixedSpent) / dayOfMonth * daysInMonth);
        } else {
            daysLeft = YearMonth.from(today).isBefore(ym) ? daysInMonth : 0;
            projected = spent;
        }
        if (limit > 0 && !level.equals("over") && projected > limit && daysLeft > 0) {
            alerts.add(String.format("At this pace you will spend about %.0f this month, above your %.0f limit", projected, limit));
        }

        // Include today, so the last day of the month still counts as part of a week.
        double weeksLeft = Math.max(1, (daysLeft + 1) / 7.0);
        double weeklyAllowance = spendableLimit > 0 && daysLeft >= 0 && YearMonth.from(today).equals(ym)
                ? round(Math.max(0, spendableLimit - spendableSpent) / weeksLeft) : 0;

        return new BudgetStatus(ym.toString(), limit, spent, round(limit - spent), percent(spent, limit),
                level, threshold, daysLeft, projected, income, budget.getSavingsTarget(), savedSoFar,
                round(spendableLimit), spendableSpent, percent(spendableSpent, spendableLimit), spendableLevel,
                weeklyAllowance, fixed, categories, alerts);
    }

    private static String level(double spent, double limit, int threshold) {
        if (limit <= 0) return "none";
        if (spent > limit) return "over";
        if (spent >= limit * threshold / 100.0) return "warning";
        return "ok";
    }

    private static double percent(double spent, double limit) {
        return limit <= 0 ? 0 : Math.round(spent / limit * 1000) / 10.0;
    }

    private static double round(double v) {
        return Math.round(v * 100) / 100.0;
    }
}
