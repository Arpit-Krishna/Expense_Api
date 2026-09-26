package com.expenses.Expense_Api.DTO;

import com.expenses.Expense_Api.model.RecurringPayment;

import java.time.LocalDate;
import java.time.YearMonth;

/** A recurring payment plus when it is next due and whether this month's one is already logged. */
public record RecurringView(
        String id,
        String title,
        String category,
        double amount,
        int dayOfMonth,
        boolean active,
        String note,
        LocalDate dueThisMonth,
        boolean loggedThisMonth,
        LocalDate nextDue,
        boolean dueTomorrow
) {
    public static RecurringView of(RecurringPayment p, LocalDate today) {
        YearMonth month = YearMonth.from(today);
        LocalDate thisMonth = p.dueDate(month);
        boolean logged = p.postedFor(month);
        LocalDate next = logged || thisMonth.isBefore(today) ? p.dueDate(month.plusMonths(1)) : thisMonth;
        return new RecurringView(p.getId(), p.getTitle(), p.getCategory(), p.getAmount(), p.getDayOfMonth(),
                p.isActive(), p.getNote(), thisMonth, logged, next, p.isActive() && next.equals(today.plusDays(1)));
    }
}
