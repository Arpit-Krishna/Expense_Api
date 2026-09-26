package com.expenses.Expense_Api.controller;

import com.expenses.Expense_Api.exception.ApiException;
import com.expenses.Expense_Api.model.Expence;
import com.expenses.Expense_Api.services.BudgetService;
import com.expenses.Expense_Api.services.DatabaseHealth;
import com.expenses.Expense_Api.services.ExpenceServicies;
import com.expenses.Expense_Api.services.UserServicies;
import com.expenses.Expense_Api.util.JwTUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.User;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.data.mongodb.uri=mongodb://localhost:27017/test",
        "spring.data.mongodb.auto-index-creation=false", "app.scheduler.enabled=false"
})
@AutoConfigureMockMvc
class ApiSecurityTest {

    @Autowired MockMvc mvc;
    @Autowired JwTUtil jwtUtil;
    @MockitoBean UserServicies userServicies;
    @MockitoBean ExpenceServicies expenceServicies;
    @MockitoBean BudgetService budgetService;

    private String login() {
        when(userServicies.loadUserByUsername("arpit"))
                .thenReturn(User.withUsername("arpit").password("x").roles("USER").build());
        return jwtUtil.generateToken("arpit");
    }

    @MockitoBean DatabaseHealth databaseHealth;

    @Test
    void healthIsPublic() throws Exception {
        mvc.perform(get("/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.db").doesNotExist());
    }

    @Test
    void healthWithDbPingsMongo() throws Exception {
        when(databaseHealth.isUp()).thenReturn(true);
        mvc.perform(get("/health").param("db", "true"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.db").value("ok"));

        when(databaseHealth.isUp()).thenReturn(false);
        mvc.perform(get("/health").param("db", "true"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.db").value("down"));
    }

    @Test
    void missingOrBadTokenGets401() throws Exception {
        mvc.perform(get("/api/expense")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/expense").header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void wrongPasswordGets401WithMessage() throws Exception {
        when(userServicies.login(anyString(), anyString(), any())).thenThrow(ApiException.unauthorized("Wrong username or password"));
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"a\",\"password\":\"b\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Wrong username or password"));
    }

    @Test
    void invalidExpenseGets400() throws Exception {
        String token = login();
        mvc.perform(post("/api/expense").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"\",\"amount\":-5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.title").exists())
                .andExpect(jsonPath("$.errors.amount").exists());
    }

    @Test
    void validExpenseIsCreatedAndCategoryReturned() throws Exception {
        String token = login();
        Expence saved = new Expence();
        saved.setId("1");
        saved.setTitle("Lunch");
        saved.setAmount(120);
        saved.setCategory("Food");
        when(expenceServicies.addExpense(any())).thenReturn(saved);
        mvc.perform(post("/api/expense").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Lunch\",\"amount\":120,\"category\":\"Food\",\"date\":\"2026-09-26T13:00:00\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category").value("Food"));
    }

    @Test
    void missingExpenseGets404() throws Exception {
        String token = login();
        when(expenceServicies.getExpenseById(eq("nope"))).thenThrow(ApiException.notFound("Expense not found"));
        mvc.perform(get("/api/expense/nope").header("Authorization", token))
                .andExpect(status().isNotFound());
    }

    @Test
    void signupRejectsWeakInput() throws Exception {
        mvc.perform(post("/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"a\",\"password\":\"short\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void badQueryParameterGets400Json() throws Exception {
        String token = login();
        mvc.perform(get("/api/expense").param("page", "abc").header("Authorization", token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid value for page"));
        mvc.perform(get("/api/budget/status").param("today", "not-a-date").header("Authorization", token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void databaseOutageGets503WithoutInternals() throws Exception {
        String token = login();
        when(expenceServicies.getExpenseSummary())
                .thenThrow(new DataAccessResourceFailureException("Timed out connecting to cluster0.secret-host"));
        mvc.perform(get("/api/expense/summary").header("Authorization", token))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value(not(containsString("secret-host"))));
    }

    @Test
    void unexpectedErrorGets500WithoutInternals() throws Exception {
        String token = login();
        when(expenceServicies.getExpenseById("boom")).thenThrow(new IllegalStateException("internal detail"));
        mvc.perform(get("/api/expense/boom").header("Authorization", token))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("Something went wrong. Please try again."));
    }

    @Test
    void wrongMethodKeepsItsStatus() throws Exception {
        String token = login();
        mvc.perform(patch("/api/expense/1").header("Authorization", token))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void loginPassesClientAddressToTheLimiter() throws Exception {
        when(userServicies.login("arpit", "pw", "10.1.2.3")).thenReturn("Bearer t");
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"arpit\",\"password\":\"pw\"}")
                        .with(r -> { r.setRemoteAddr("10.1.2.3"); return r; }))
                .andExpect(status().isOk())
                .andExpect(content().string("Bearer t"));
    }

    @Test
    void lockedOutLoginGets429() throws Exception {
        when(userServicies.login(anyString(), anyString(), any()))
                .thenThrow(ApiException.tooManyRequests("Too many failed attempts. Try again in 15 minute(s)."));
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"a\",\"password\":\"b\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void tooLargeAmountGets400() throws Exception {
        String token = login();
        mvc.perform(post("/api/expense").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"x\",\"amount\":1e12}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.amount").value("Amount is too large"));
    }
}
