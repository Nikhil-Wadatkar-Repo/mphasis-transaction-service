package com.mphasis;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
		"app.security.api-key=test-api-key",
		"app.security.read-only-api-key=test-readonly-key"
})
@AutoConfigureMockMvc
class TransactionServiceApplicationTests {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void contextLoads() {
	}

	@Test
	void healthEndpointDoesNotRequireApiKey() throws Exception {
		mockMvc.perform(get("/api/health"))
				.andExpect(status().isOk());
	}

	@Test
	void transactionEndpointsRequireApiKey() throws Exception {
		mockMvc.perform(get("/api/transactions"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void validApiKeyGrantsAccess() throws Exception {
		mockMvc.perform(get("/api/transactions")
						.header("Authorization", "Bearer test-api-key"))
				.andExpect(status().isOk());
	}

	@Test
	void readOnlyApiKeyAllowsReadsButNotWrites() throws Exception {
		mockMvc.perform(get("/api/transactions")
						.header("Authorization", "Bearer test-readonly-key"))
				.andExpect(status().isOk());
		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
						.post("/api/transactions/batch")
						.header("Authorization", "Bearer test-readonly-key")
						.contentType("application/json")
						.content("[]"))
				.andExpect(status().isForbidden());
	}
}
