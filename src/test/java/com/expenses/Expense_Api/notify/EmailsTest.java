package com.expenses.Expense_Api.notify;

import com.expenses.Expense_Api.DTO.BudgetStatus;
import com.expenses.Expense_Api.DTO.RecurringView;
import com.expenses.Expense_Api.model.RecurringPayment;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EmailsTest {

    private static BudgetStatus status(List<BudgetStatus.CategoryStatus> categories) {
        return new BudgetStatus("2026-09", 0, 1000, 0, 0, "none", 80, 4, 0, 0, 0, 0,
                7000, 5000, 71.4, "ok", 1200, List.of(), categories, List.of());
    }

    @Test
    void escapesUserTextInHtmlAndStripsNewlinesFromSubject() {
        Emails.Email e = Emails.limitAlert(List.of("<script>x</script> is over\r\nBcc: someone"), status(List.of()), "https://app");

        assertThat(e.html()).doesNotContain("<script>").contains("&lt;script&gt;");
        assertThat(e.subject()).doesNotContain("\n").doesNotContain("\r");
    }

    @Test
    void weeklyShowsSafeToSpendAndCategoriesToWatch() {
        Emails.Email e = Emails.weekly(LocalDate.of(2026, 9, 27), 3000, 3000,
                status(List.of(new BudgetStatus.CategoryStatus("Food", 6500, 6000, 500, 92.3, "warning"),
                        new BudgetStatus.CategoryStatus("Rent", 9000, 9000, 0, 100, "ok"))), "https://app");

        assertThat(e.subject()).isEqualTo("Your week: ₹3,000 spent, ₹1,200 safe to spend");
        assertThat(e.text()).contains("Mon 21 Sep to Sun 27 Sep").contains("Food has used 92%").doesNotContain("Rent");
    }

    @Test
    void dueDateFallsOnLastDayInShortMonths() {
        RecurringPayment p = new RecurringPayment();
        p.setDayOfMonth(31);
        p.setActive(true);

        assertThat(p.dueDate(YearMonth.of(2027, 2))).isEqualTo(LocalDate.of(2027, 2, 28));
        assertThat(p.dueDate(YearMonth.of(2028, 2))).isEqualTo(LocalDate.of(2028, 2, 29));
        assertThat(p.dueDate(YearMonth.of(2026, 10))).isEqualTo(LocalDate.of(2026, 10, 31));
    }

    @Test
    void viewWorksOutNextDueAndDueTomorrow() {
        RecurringPayment p = new RecurringPayment();
        p.setDayOfMonth(5);
        p.setActive(true);

        RecurringView before = RecurringView.of(p, LocalDate.of(2026, 9, 4));
        assertThat(before.nextDue()).isEqualTo(LocalDate.of(2026, 9, 5));
        assertThat(before.dueTomorrow()).isTrue();

        p.setLastPostedMonth("2026-09");
        RecurringView after = RecurringView.of(p, LocalDate.of(2026, 9, 5));
        assertThat(after.loggedThisMonth()).isTrue();
        assertThat(after.nextDue()).isEqualTo(LocalDate.of(2026, 10, 5));

        p.setActive(false);
        assertThat(RecurringView.of(p, LocalDate.of(2026, 10, 4)).dueTomorrow()).isFalse();
    }
}
