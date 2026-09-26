package com.expenses.Expense_Api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

import java.util.TimeZone;

@SpringBootApplication
@EnableMongoRepositories("com.expenses.Expense_Api.repository")
public class ExpenseApiApplication {

	public static void main(String[] args) {
		// Dates are stored as LocalDateTime, which MongoDB converts using the JVM zone. Render runs in
		// UTC, so without this an expense logged after 18:30 IST was saved under the next day's UTC
		// date. Using the users' zone makes "now", month boundaries and stored instants line up.
		String zone = System.getenv().getOrDefault("APP_TIMEZONE", "Asia/Kolkata");
		TimeZone.setDefault(TimeZone.getTimeZone(zone));
		SpringApplication.run(ExpenseApiApplication.class, args);
	}

}
