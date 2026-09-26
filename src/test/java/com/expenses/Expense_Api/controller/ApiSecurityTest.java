package com.expenses.Expense_Api.controller;

import com.expenses.Expense_Api.exception.ApiException;
import com.expenses.Expense_Api.model.Expence;
import com.expenses.Expense_Api.services.BudgetService;
import com.expenses.Expense_Api.services.ExpenceServicies;
import com.expenses.Expense_Api.services.UserServicies;
import com.expenses.Expense_Api.util.JwTUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.User;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.data.mongodb.uri=mongodb://localhost:27017/test",
        "spring.data.mongodb.auto-index-creation=false"
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

    @Test
    void healthIsPublic() throws Exception {
        mvc.perform(get("/health")).andExpect(status().isOk());
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
        when(userServicies.login(anyString(), anyString())).thenThrow(ApiException.unauthorized("Wrong username or password"));
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
}
