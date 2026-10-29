package com.aptech.aptech4.chat.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Compatibility checks — routing lives in {@link AdminIntentRouter}.
 */
class AdminAnalyticsQueryKindTest {

	@Test
	void detectsOrdersNeedingProcessing() {
		assertThat(AdminIntentRouter.route("How many orders need processing?").kind())
				.isEqualTo(AdminAnalyticsQueryKind.ORDERS_NEEDING_ATTENTION);
		assertThat(AdminIntentRouter.route("Có bao nhiêu đơn đang chờ xử lý?").kind())
				.isEqualTo(AdminAnalyticsQueryKind.ORDERS_NEEDING_ATTENTION);
	}

	@Test
	void detectsInventorySeparately() {
		assertThat(AdminIntentRouter.route("Which products are low on stock?").kind())
				.isEqualTo(AdminAnalyticsQueryKind.INVENTORY_LOW);
		assertThat(AdminIntentRouter.route("What is out of stock?").kind())
				.isEqualTo(AdminAnalyticsQueryKind.INVENTORY_OUT);
	}

	@Test
	void ignoresUnrelated() {
		assertThat(AdminIntentRouter.route("Hello").kind()).isEqualTo(AdminAnalyticsQueryKind.NONE);
	}
}
