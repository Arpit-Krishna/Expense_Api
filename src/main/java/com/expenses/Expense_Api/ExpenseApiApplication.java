package com.expenses.Expense_Api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

@SpringBootApplication
@EnableMongoRepositories("com.expenses.Expense_Api.repository")
public class ExpenseApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(ExpenseApiApplication.class, args);
	}

}
