package com.expenses.Expense_Api.config;

import com.expenses.Expense_Api.model.Budget;
import com.expenses.Expense_Api.model.Expence;
import com.expenses.Expense_Api.model.NotificationSettings;
import com.expenses.Expense_Api.model.RecurringPayment;
import com.expenses.Expense_Api.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.stereotype.Component;

/**
 * Creates the indexes the queries rely on. Without them every expense list, budget status and login
 * scans the whole collection. Runs in the background after startup and only logs on failure, so the
 * app (and /health) still starts when the database is unreachable. Creating an existing index is a no-op.
 */
@Component
public class MongoIndexInitializer {
    private static final Logger log = LoggerFactory.getLogger(MongoIndexInitializer.class);

    private final MongoTemplate mongoTemplate;
    private final boolean enabled;

    public MongoIndexInitializer(MongoTemplate mongoTemplate, @Value("${app.mongo.ensure-indexes:true}") boolean enabled) {
        this.mongoTemplate = mongoTemplate;
        this.enabled = enabled;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        if (!enabled) return;
        Thread thread = new Thread(this::ensureIndexes, "mongo-index-init");
        thread.setDaemon(true);
        thread.start();
    }

    public void ensureIndexes() {
        // Expense lists, month totals and budget status all filter by user and a date range.
        create(Expence.class, new Index().on("userId", Sort.Direction.ASC).on("date", Sort.Direction.DESC).named("userId_date"));
        create(User.class, new Index().on("username", Sort.Direction.ASC).unique().named("username_unique"));
        create(Budget.class, new Index().on("userId", Sort.Direction.ASC).unique().named("userId_unique"));
        create(RecurringPayment.class, new Index().on("userId", Sort.Direction.ASC).named("userId"));
        create(NotificationSettings.class, new Index().on("userId", Sort.Direction.ASC).unique().named("userId_unique"));
    }

    private void create(Class<?> type, Index index) {
        try {
            mongoTemplate.indexOps(type).ensureIndex(index);
        } catch (RuntimeException e) {
            // For example duplicate usernames already stored, or the database being down.
            log.warn("Could not create index {} on {}: {}", index.getIndexOptions().get("name"),
                    type.getSimpleName(), e.getMessage());
        }
    }
}
