package com.expenses.Expense_Api.services;

import com.expenses.Expense_Api.DTO.BudgetStatus;
import com.expenses.Expense_Api.model.Budget;
import com.expenses.Expense_Api.model.Expence;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class BudgetServiceTest {

    private static Expence expense(String category, double amount) {
        Expence e = new Expence();
        e.setTitle("x");
        e.setCategory(category);
        e.setAmount(amount);
        return e;
    }

    private static Budget budget(double monthly, Map<String, Double> categories) {
        Budget b = new Budget();
        b.setMonthlyLimit(monthly);
        b.setCategoryLimits(new LinkedHashMap<>(categories));
        return b;
    }

    @Test
    void flagsOverspendingOverallAndPerCategory() {
        Budget b = budget(1000, Map.of("Food", 300.0, "Travel", 500.0));
        List<Expence> spent = List.of(expense("Food", 350), expense("Travel", 100), expense("Shopping", 600));

        BudgetStatus s = BudgetService.computeStatus(b, YearMonth.of(2026, 9), spent, LocalDate.of(2026, 9, 26));

        assertThat(s.spent()).isEqualTo(1050);
        assertThat(s.level()).isEqualTo("over");
        assertThat(s.remaining()).isEqualTo(-50);
        BudgetStatus.CategoryStatus food = s.categories().stream().filter(c -> c.category().equals("Food")).findFirst().orElseThrow();
        assertThat(food.level()).isEqualTo("over");
        BudgetStatus.CategoryStatus travel = s.categories().stream().filter(c -> c.category().equals("Travel")).findFirst().orElseThrow();
        assertThat(travel.level()).isEqualTo("ok");
        BudgetStatus.CategoryStatus shopping = s.categories().stream().filter(c -> c.category().equals("Shopping")).findFirst().orElseThrow();
        assertThat(shopping.level()).isEqualTo("none");
        assertThat(s.alerts()).anyMatch(a -> a.contains("monthly budget")).anyMatch(a -> a.startsWith("Food"));
    }

    @Test
    void warnsAtThresholdAndProjectsPace() {
        Budget b = budget(1000, Map.of());
        List<Expence> spent = List.of(expense("Food", 800));

        BudgetStatus s = BudgetService.computeStatus(b, YearMonth.of(2026, 9), spent, LocalDate.of(2026, 9, 10));

        assertThat(s.level()).isEqualTo("warning");
        assertThat(s.percent()).isEqualTo(80.0);
        assertThat(s.daysLeft()).isEqualTo(20);
        assertThat(s.projectedSpend()).isEqualTo(2400);
        assertThat(s.alerts()).anyMatch(a -> a.contains("80%")).anyMatch(a -> a.contains("At this pace"));
    }

    @Test
    void noLimitsMeansNoAlerts() {
        BudgetStatus s = BudgetService.computeStatus(budget(0, Map.of()), YearMonth.of(2026, 8),
                List.of(expense("Food", 100)), LocalDate.of(2026, 9, 26));

        assertThat(s.level()).isEqualTo("none");
        assertThat(s.alerts()).isEmpty();
        assertThat(s.daysLeft()).isZero();
    }

    @Test
    void legacyExpensesUseDescriptionAsCategory() {
        Expence legacy = new Expence();
        legacy.setDescription("Food");
        legacy.setAmount(400);

        BudgetStatus s = BudgetService.computeStatus(budget(0, Map.of("Food", 300.0)), YearMonth.of(2026, 9),
                List.of(legacy), LocalDate.of(2026, 9, 26));

        assertThat(s.categories().get(0).level()).isEqualTo("over");
    }

    @Test
    void salaryPlanTracksSpendableMoneyAndSavings() {
        Budget b = budget(56500, Map.of("EMI", 15000.0, "Parents", 15000.0, "Rent", 9000.0, "Bills", 3000.0,
                "Subscriptions", 2000.0, "Food", 6500.0, "Transport", 2000.0, "Personal & Fun", 3000.0, "Other", 1000.0));
        b.setMonthlyIncome(66000);
        b.setSavingsTarget(9500);
        b.setFixedCategories(List.of("EMI", "Parents", "Rent", "Bills", "Subscriptions"));
        List<Expence> spent = List.of(expense("EMI", 15000), expense("Parents", 15000), expense("Rent", 9000),
                expense("Food", 5000), expense("Personal & Fun", 3500));

        BudgetStatus s = BudgetService.computeStatus(b, YearMonth.of(2026, 9), spent, LocalDate.of(2026, 9, 12));

        assertThat(s.spendableLimit()).isEqualTo(12500);
        assertThat(s.spendableSpent()).isEqualTo(8500);
        assertThat(s.spendableLevel()).isEqualTo("ok");
        assertThat(s.savedSoFar()).isEqualTo(18500);
        // 4000 left over 19 days including today, about 2.71 weeks
        assertThat(s.weeklyAllowance()).isEqualTo(1473.68);
        assertThat(s.alerts()).anyMatch(a -> a.startsWith("Personal & Fun is over"));
        assertThat(s.alerts()).noneMatch(a -> a.contains("savings"));
        assertThat(s.alerts()).noneMatch(a -> a.startsWith("EMI") || a.startsWith("Rent"));
        // 39000 fixed + 8500 day-to-day over 12 days extrapolated to 30
        assertThat(s.projectedSpend()).isEqualTo(60250);
    }

    @Test
    void warnsWhenSpendingEatsIntoSavings() {
        Budget b = budget(0, Map.of());
        b.setMonthlyIncome(66000);
        b.setSavingsTarget(9500);

        BudgetStatus s = BudgetService.computeStatus(b, YearMonth.of(2026, 9), List.of(expense("Rent", 60000)), LocalDate.of(2026, 9, 20));

        assertThat(s.savedSoFar()).isEqualTo(6000);
        assertThat(s.alerts()).anyMatch(a -> a.contains("savings target"));
    }
}
