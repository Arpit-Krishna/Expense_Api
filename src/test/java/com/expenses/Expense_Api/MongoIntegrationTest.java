package com.expenses.Expense_Api;

import com.expenses.Expense_Api.config.MongoIndexInitializer;
import com.expenses.Expense_Api.model.Expence;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * End-to-end run against a real MongoDB. Skipped unless MONGO_TEST_URI is set, for example:
 * docker run -d -p 27017:27017 mongo:7 && MONGO_TEST_URI=mongodb://localhost:27017/expense_it ./mvnw test
 */
@EnabledIfEnvironmentVariable(named = "MONGO_TEST_URI", matches = ".+")
@SpringBootTest(properties = {"spring.data.mongodb.uri=${MONGO_TEST_URI}", "app.mongo.ensure-indexes=false"})
@AutoConfigureMockMvc
class MongoIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired MongoTemplate mongo;
    @Autowired MongoIndexInitializer indexes;

    private String signup(String username) throws Exception {
        MvcResult r = mvc.perform(post("/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"password123\"}"))
                .andExpect(status().isOk()).andReturn();
        return r.getResponse().getContentAsString();
    }

    private void add(String token, String title, double amount, String category, String date) throws Exception {
        mvc.perform(post("/api/expense").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"" + title + "\",\"amount\":" + amount + ",\"category\":\"" + category
                                + "\",\"date\":\"" + date + "\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void fullFlowAgainstRealDatabase() throws Exception {
        mongo.getDb().drop();
        indexes.ensureIndexes();
        assertThat(mongo.indexOps(Expence.class).getIndexInfo()).anyMatch(i -> i.getName().equals("userId_date"));

        String arpit = signup("arpit");
        String other = signup("other");
        mvc.perform(post("/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"arpit\",\"password\":\"password123\"}"))
                .andExpect(status().isConflict());

        add(arpit, "Lunch", 120.5, "Food", "2026-09-10T13:00:00");
        add(arpit, "Metro", 40, "Transport", "2026-09-11T09:00:00");
        add(arpit, "Old", 999, "Food", "2026-08-31T23:00:00");
        add(other, "Not mine", 5000, "Food", "2026-09-12T10:00:00");

        mvc.perform(get("/api/expense").header("Authorization", arpit)
                        .param("from", "2026-09-01").param("to", "2026-09-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(2))
                .andExpect(jsonPath("$.totalAmount").value(160.5))
                .andExpect(jsonPath("$.expenses[0].title").value("Metro"));

        mvc.perform(get("/api/expense").header("Authorization", arpit).param("category", "Food").param("q", "lun"))
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.totalAmount").value(120.5));

        mvc.perform(get("/api/expense").header("Authorization", arpit).param("q", "nothing-matches"))
                .andExpect(jsonPath("$.totalItems").value(0))
                .andExpect(jsonPath("$.totalAmount").value(0.0));

        mvc.perform(put("/api/budget").header("Authorization", arpit).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"monthlyLimit\":150,\"categoryLimits\":{\"Food\":100}}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/budget/status").header("Authorization", arpit).param("month", "2026-09").param("today", "2026-09-26"))
                .andExpect(jsonPath("$.spent").value(160.5))
                .andExpect(jsonPath("$.level").value("over"));

        mvc.perform(get("/api/expense/summary").header("Authorization", arpit))
                .andExpect(jsonPath("$.totalExpenseCount").value(3))
                .andExpect(jsonPath("$.favoriteCategory").value("Food"));

        mvc.perform(get("/auth/me").header("Authorization", arpit))
                .andExpect(jsonPath("$.username").value("arpit"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }
}
