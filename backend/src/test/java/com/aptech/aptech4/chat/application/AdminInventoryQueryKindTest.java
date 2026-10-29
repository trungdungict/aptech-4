package com.aptech.aptech4.chat.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AdminInventoryQueryKindTest {

	@Test
	void detectsLowAndOut() {
		assertThat(AdminInventoryQueryKind.detect("Which products are low on stock?"))
				.isEqualTo(AdminInventoryQueryKind.LOW_STOCK);
		assertThat(AdminInventoryQueryKind.detect("Show out of stock items"))
				.isEqualTo(AdminInventoryQueryKind.OUT_OF_STOCK);
	}

	@Test
	void combinedIsRisk() {
		assertThat(AdminInventoryQueryKind.detect("low stock and out of stock summary"))
				.isEqualTo(AdminInventoryQueryKind.RISK_SUMMARY);
	}
}
