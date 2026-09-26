package com.expenses.Expense_Api.notify;

import com.expenses.Expense_Api.DTO.BudgetStatus;
import com.expenses.Expense_Api.model.RecurringPayment;
import org.springframework.web.util.HtmlUtils;

import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Builds the email bodies. Pure functions, so they can be tested without sending anything. */
public final class Emails {
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH);

    private Emails() {}

    public record Email(String subject, String html, String text) {}

    public static String money(double amount) {
        NumberFormat f = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-IN"));
        f.setMaximumFractionDigits(0);
        return f.format(Math.round(amount));
    }

    /** Sunday check-in: last 7 days, what is safe to spend next week, and categories near or over. */
    public static Email weekly(LocalDate today, double spentThisWeek, double dayToDayThisWeek,
                               BudgetStatus nextWeek, String appUrl) {
        List<String> lines = new ArrayList<>();
        lines.add("You spent " + money(spentThisWeek) + " from " + DAY.format(today.minusDays(6)) + " to " + DAY.format(today)
                + (dayToDayThisWeek != spentThisWeek ? " (" + money(dayToDayThisWeek) + " of it day-to-day)." : "."));
        if (nextWeek.spendableLimit() > 0) {
            lines.add("Safe to spend this coming week: " + money(nextWeek.weeklyAllowance()) + ".");
            lines.add("Day-to-day budget left this month: " + money(Math.max(0, nextWeek.spendableLimit() - nextWeek.spendableSpent()))
                    + " of " + money(nextWeek.spendableLimit()) + ".");
        } else if (nextWeek.monthlyLimit() > 0) {
            lines.add("Left in your monthly budget: " + money(nextWeek.remaining()) + ".");
        } else {
            lines.add("Set category limits on the Budgets page to see what is safe to spend each week.");
        }
        List<String> watch = new ArrayList<>();
        for (BudgetStatus.CategoryStatus c : nextWeek.categories()) {
            if (c.level().equals("over")) watch.add(c.category() + " is over its limit: " + money(c.spent()) + " of " + money(c.limit()));
            else if (c.level().equals("warning")) watch.add(c.category() + " has used " + Math.round(c.percent()) + "%: " + money(c.spent()) + " of " + money(c.limit()));
        }
        String subject = "Your week: " + money(spentThisWeek) + " spent"
                + (nextWeek.spendableLimit() > 0 ? ", " + money(nextWeek.weeklyAllowance()) + " safe to spend" : "");
        return build(subject, "Weekly check-in", lines, watch.isEmpty() ? null : "Keep an eye on", watch, appUrl);
    }

    /** Sent the moment an expense pushes a limit to its warning level or over it. */
    public static Email limitAlert(List<String> alerts, BudgetStatus status, String appUrl) {
        List<String> lines = new ArrayList<>();
        lines.add("Spent so far in " + status.month() + ": " + money(status.spent())
                + (status.monthlyLimit() > 0 ? " of " + money(status.monthlyLimit()) : "") + ".");
        if (status.spendableLimit() > 0 && status.weeklyAllowance() >= 0) {
            lines.add("Safe to spend per week for the rest of the month: " + money(status.weeklyAllowance()) + ".");
        }
        String subject = alerts.size() == 1 ? alerts.get(0) : alerts.size() + " budget alerts";
        return build(subject, "Budget alert", lines, "What changed", alerts, appUrl);
    }

    /** Sent the day before recurring payments are due. */
    public static Email dueTomorrow(List<RecurringPayment> due, LocalDate dueDate, String appUrl) {
        double total = due.stream().mapToDouble(RecurringPayment::getAmount).sum();
        List<String> items = due.stream().map(p -> p.getTitle() + ": " + money(p.getAmount())).toList();
        List<String> lines = List.of("These payments are due tomorrow, " + DAY.format(dueDate) + ". Total " + money(total) + ".",
                "They will be logged as expenses automatically, so there is nothing to add by hand.");
        String subject = due.size() == 1
                ? "Due tomorrow: " + due.get(0).getTitle() + " " + money(due.get(0).getAmount())
                : "Due tomorrow: " + due.size() + " payments, " + money(total);
        return build(subject, "Payment reminder", lines, "Due", items, appUrl);
    }

    public static Email test(String appUrl) {
        return build("Expensify emails are working", "Test email",
                List.of("This is a test. Weekly check-ins, budget alerts and payment reminders will come to this address."),
                null, List.of(), appUrl);
    }

    private static Email build(String subject, String heading, List<String> lines, String listTitle, List<String> items, String appUrl) {
        StringBuilder html = new StringBuilder();
        StringBuilder text = new StringBuilder();
        html.append("<div style=\"font-family:-apple-system,Segoe UI,Roboto,sans-serif;max-width:520px;margin:0 auto;color:#1a1a19;line-height:1.5\">");
        html.append("<p style=\"font-size:12px;letter-spacing:.08em;text-transform:uppercase;color:#7d7a74;margin:0 0 8px\">Expensify</p>");
        html.append("<h1 style=\"font-size:22px;font-weight:600;margin:0 0 16px\">").append(esc(heading)).append("</h1>");
        for (String line : lines) {
            html.append("<p style=\"margin:0 0 10px\">").append(esc(line)).append("</p>");
            text.append(line).append("\n");
        }
        if (listTitle != null && !items.isEmpty()) {
            html.append("<p style=\"margin:18px 0 6px;font-weight:600\">").append(esc(listTitle)).append("</p><ul style=\"margin:0;padding-left:20px\">");
            text.append("\n").append(listTitle).append(":\n");
            for (String item : items) {
                html.append("<li style=\"margin:0 0 4px\">").append(esc(item)).append("</li>");
                text.append("- ").append(item).append("\n");
            }
            html.append("</ul>");
        }
        html.append("<p style=\"margin:24px 0 0\"><a href=\"").append(esc(appUrl)).append("\" style=\"color:#1a1a19\">Open Expensify</a></p>");
        html.append("<p style=\"margin:16px 0 0;font-size:12px;color:#7d7a74\">You can turn these emails off in Expensify under Email alerts.</p></div>");
        text.append("\nOpen Expensify: ").append(appUrl).append("\nTurn these emails off in Expensify under Email alerts.\n");
        return new Email(subject.replaceAll("[\\r\\n]+", " "), html.toString(), text.toString());
    }

    private static String esc(String s) {
        return HtmlUtils.htmlEscape(s == null ? "" : s);
    }
}
