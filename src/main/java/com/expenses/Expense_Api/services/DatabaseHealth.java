package com.expenses.Expense_Api.services;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

/** A tiny MongoDB round trip, used by /health?db=true so the free Atlas cluster sees regular activity. */
@Service
public class DatabaseHealth {
    private final MongoTemplate mongoTemplate;

    public DatabaseHealth(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public boolean isUp() {
        try {
            mongoTemplate.executeCommand("{ ping: 1 }");
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }
}
