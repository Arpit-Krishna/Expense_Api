package com.expenses.Expense_Api;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

// The Mongo client connects lazily, so the context starts without a running database.
@SpringBootTest(properties = {
		"spring.data.mongodb.uri=mongodb://localhost:27017/test",
		"spring.data.mongodb.auto-index-creation=false"
})
class ExpenseApiApplicationTests {

	@Test
	void contextLoads() {
	}

}
