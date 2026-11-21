package com.bankservices.bankservices_api;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Smoke test: the full application context starts with the "test" profile
 * (in-memory H2, fake JWT secret, dummy mail settings) instead of MySQL.
 */
@SpringBootTest
@ActiveProfiles("test")
class BankservicesApiApplicationTests {

	@Test
	void contextLoads() {
	}

}
