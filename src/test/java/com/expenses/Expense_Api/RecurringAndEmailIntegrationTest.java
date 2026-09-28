package com.expenses.Expense_Api;

import com.expenses.Expense_Api.model.Expence;
import com.expenses.Expense_Api.model.NotificationSettings;
import com.expenses.Expense_Api.model.RecurringPayment;
import com.expenses.Expense_Api.notify.EmailSender;
import com.expenses.Expense_Api.notify.NotificationScheduler;
import com.expenses.Expense_Api.services.RecurringService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Recurring payments and emails against a real MongoDB. Skipped unless MONGO_TEST_URI is set. */
@EnabledIfEnvironmentVariable(named = "MONGO_TEST_URI", matches = ".+")
@SpringBootTest(properties = {"spring.data.mongodb.uri=${MONGO_TEST_URI}", "app.mongo.ensure-indexes=false",
        "app.scheduler.enabled=false"})
@AutoConfigureMockMvc
class RecurringAndEmailIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired MongoTemplate mongo;
    @Autowired RecurringService recurring;
    @Autowired NotificationScheduler scheduler;
    @MockitoBean EmailSender emailSender;

    private String token;
    private String userId;

    @BeforeEach
    void setUp() throws Exception {
        mongo.getDb().drop();
        when(emailSender.isEnabled()).thenReturn(true);
        when(emailSender.send(anyString(), anyString(), anyString(), anyString())).thenReturn(true);
        when(emailSender.deliver(anyString(), anyString(), anyString(), anyString())).thenReturn(new EmailSender.Result(true, null));
        token = mvc.perform(post("/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"arpit\",\"password\":\"password123\",\"email\":\"arpit@example.com\"}"))
                .andReturn().getResponse().getContentAsString();
        userId = mongo.findOne(new Query(Criteria.where("username").is("arpit")), com.expenses.Expense_Api.model.User.class).getId();
    }

    private List<Expence> expenses() {
        return mongo.find(new Query(Criteria.where("userId").is(userId)), Expence.class);
    }

    @Test
    void defaultsAreAddedOnceAndPostedOncePerMonth() throws Exception {
        mvc.perform(post("/api/recurring/defaults").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$[?(@.category == 'EMI')].amount").value(15000.0));
        mvc.perform(post("/api/recurring/defaults").header("Authorization", token))
                .andExpect(jsonPath("$.length()").value(5));

        // Day 1 has already passed (unless today is the 1st), so nothing is logged for this month.
        if (LocalDate.now().getDayOfMonth() > 1) assertThat(expenses()).isEmpty();

        LocalDate future = LocalDate.now().plusMonths(2).withDayOfMonth(3);
        // Two runs at once, like an overlapping scheduler and page load, must not double-log.
        CompletableFuture.allOf(
                CompletableFuture.runAsync(() -> recurring.postAllDue(future)),
                CompletableFuture.runAsync(() -> recurring.postAllDue(future))).join();
        recurring.postAllDue(future);

        List<Expence> logged = expenses();
        assertThat(logged).hasSize(5);
        assertThat(logged).allMatch(e -> e.getRecurringId() != null && e.getDate().equals(future.withDayOfMonth(1).atTime(9, 0)));
        assertThat(logged.stream().mapToDouble(Expence::getAmount).sum()).isEqualTo(44000);
    }

    @Test
    void crudLogNowAndOwnership() throws Exception {
        String body = mvc.perform(post("/api/recurring").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Gym\",\"category\":\"Health\",\"amount\":1200,\"dayOfMonth\":31}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true))
                .andReturn().getResponse().getContentAsString();
        String id = com.jayway.jsonpath.JsonPath.read(body, "$.id");

        mvc.perform(post("/api/recurring").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"\",\"amount\":-1,\"dayOfMonth\":40}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.dayOfMonth").exists());

        mvc.perform(post("/api/recurring/" + id + "/log").header("Authorization", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.loggedThisMonth").value(true));
        mvc.perform(post("/api/recurring/" + id + "/log").header("Authorization", token)).andExpect(status().isOk());
        assertThat(expenses()).hasSize(1);

        mvc.perform(put("/api/recurring/" + id).header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Gym\",\"category\":\"Health\",\"amount\":1500,\"dayOfMonth\":31,\"active\":false}"))
                .andExpect(jsonPath("$.amount").value(1500.0)).andExpect(jsonPath("$.active").value(false));

        String other = mvc.perform(post("/auth/signup").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"other\",\"password\":\"password123\"}")).andReturn().getResponse().getContentAsString();
        mvc.perform(delete("/api/recurring/" + id).header("Authorization", other)).andExpect(status().isNotFound());
        mvc.perform(get("/api/recurring").header("Authorization", other)).andExpect(jsonPath("$.length()").value(0));

        mvc.perform(delete("/api/recurring/" + id).header("Authorization", token)).andExpect(status().isNoContent());
        mvc.perform(get("/api/recurring").header("Authorization", token)).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void remindersGoOutOnceTheDayBefore() throws Exception {
        mvc.perform(post("/api/recurring/defaults").header("Authorization", token));
        LocalDate eve = LocalDate.now().plusMonths(1).withDayOfMonth(1).minusDays(1);

        scheduler.sendReminders(eve);
        scheduler.sendReminders(eve);

        verify(emailSender, times(1)).send(eq("arpit@example.com"), eq("Due tomorrow: 5 payments, ₹44,000"), anyString(), contains("Loan EMI: ₹15,000"));
    }

    @Test
    void weeklySummaryOncePerSundayAndRespectsOptOut() throws Exception {
        mvc.perform(put("/api/budget").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"monthlyLimit\":20000,\"categoryLimits\":{\"Food\":7000,\"EMI\":15000},\"fixedCategories\":[\"EMI\"]}"));
        LocalDate sunday = LocalDate.of(2026, 9, 27);
        mongo.insert(expense("Groceries", "Food", 6000, sunday.minusDays(2).atTime(10, 0)));
        mongo.insert(expense("EMI", "EMI", 15000, sunday.minusDays(3).atTime(9, 0)));

        scheduler.sendWeeklySummaries(sunday);
        scheduler.sendWeeklySummaries(sunday);

        verify(emailSender, times(1)).send(eq("arpit@example.com"), startsWith("Your week: ₹21,000 spent"), anyString(),
                argThat(t -> t.contains("₹6,000 of it day-to-day") && t.contains("Food has used 86%")));

        mvc.perform(put("/api/notifications").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"arpit@example.com\",\"weeklySummary\":false,\"limitAlerts\":true,\"dueReminders\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.emailReady").value(true));
        scheduler.sendWeeklySummaries(sunday.plusDays(7));
        verify(emailSender, times(1)).send(anyString(), startsWith("Your week"), anyString(), anyString());
    }

    @Test
    void limitAlertsFireOnceAtWarningAndOnceWhenOver() throws Exception {
        mvc.perform(put("/api/budget").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"categoryLimits\":{\"Food\":1000}}"));
        String today = LocalDateTime.now().withNano(0).toString();

        addExpense(850, today);
        verify(emailSender, timeout(3000).times(1)).send(anyString(), anyString(), anyString(), contains("Food has used 85%"));
        addExpense(10, today);
        addExpense(200, today);
        verify(emailSender, timeout(3000).times(1)).send(anyString(), anyString(), anyString(), contains("Food is over budget by 60.00"));
        addExpense(5, today);
        Thread.sleep(500);
        verify(emailSender, times(2)).send(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void settingsDefaultToAccountEmailAndValidate() throws Exception {
        mvc.perform(get("/api/notifications").header("Authorization", token))
                .andExpect(jsonPath("$.email").value("arpit@example.com"))
                .andExpect(jsonPath("$.weeklySummary").value(true));
        mvc.perform(put("/api/notifications").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\",\"weeklySummary\":true,\"limitAlerts\":true,\"dueReminders\":true}"))
                .andExpect(status().isBadRequest());
        when(emailSender.deliver(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new EmailSender.Result(false, "Resend only sends to the email address your Resend account was created with"));
        mvc.perform(post("/api/notifications/test").header("Authorization", token))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.message").value("Resend only sends to the email address your Resend account was created with"));
        // A failed test does not count toward the one-a-minute limit.
        when(emailSender.deliver(anyString(), anyString(), anyString(), anyString())).thenReturn(new EmailSender.Result(true, null));
        mvc.perform(post("/api/notifications/test").header("Authorization", token)).andExpect(status().isOk());
        mvc.perform(post("/api/notifications/test").header("Authorization", token)).andExpect(status().isTooManyRequests());
        mvc.perform(get("/api/notifications")).andExpect(status().isUnauthorized());

        when(emailSender.isEnabled()).thenReturn(false);
        mvc.perform(get("/api/notifications").header("Authorization", token)).andExpect(jsonPath("$.emailReady").value(false));
        assertThat(mongo.findAll(NotificationSettings.class)).isEmpty();
        assertThat(mongo.findAll(RecurringPayment.class)).isEmpty();
    }

    private void addExpense(double amount, String date) throws Exception {
        mvc.perform(post("/api/expense").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Groceries\",\"amount\":" + amount + ",\"category\":\"Food\",\"date\":\"" + date + "\"}"))
                .andExpect(status().isOk());
    }

    private Expence expense(String title, String category, double amount, LocalDateTime date) {
        Expence e = new Expence();
        e.setTitle(title);
        e.setCategory(category);
        e.setAmount(amount);
        e.setDate(date);
        e.setUserId(userId);
        return e;
    }
}
