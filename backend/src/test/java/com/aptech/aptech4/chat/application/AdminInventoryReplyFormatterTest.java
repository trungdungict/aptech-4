package com.aptech.aptech4.chat.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.aptech.aptech4.chat.api.ChatLanguage;

class AdminInventoryReplyFormatterTest {

	@Test
	void formatsFailureWithoutInventingRows() {
		Map<String, Object> envelope = ChatToolEnvelope.failure(
				"getLowStockProducts",
				ChatToolEnvelope.ERROR_DATA_ACCESS,
				"db down",
				1L
		);
		String reply = AdminInventoryReplyFormatter.format(
				AdminInventoryQueryKind.LOW_STOCK,
				envelope,
				ChatLanguage.EN
		);
		assertThat(reply).contains("Could not load inventory");
		assertThat(reply).contains("DATA_ACCESS");
		assertThat(reply).doesNotContain("SKU");
	}

	@Test
	void formatsVerifiedRows() {
		Map<String, Object> row = new LinkedHashMap<>();
		row.put("name", "Demo Lamp");
		row.put("sku", "DEMO-LAMP");
		row.put("quantityOnHand", 2);
		row.put("quantityReserved", 1);
		row.put("available", 1);
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("threshold", 5);
		result.put("availableDefinition", "available = quantity_on_hand - quantity_reserved");
		result.put("items", List.of(row));
		Map<String, Object> envelope = ChatToolEnvelope.success("getLowStockProducts", result, 1, 2L);

		String reply = AdminInventoryReplyFormatter.format(
				AdminInventoryQueryKind.LOW_STOCK,
				envelope,
				ChatLanguage.EN
		);
		assertThat(reply).contains("Demo Lamp");
		assertThat(reply).contains("DEMO-LAMP");
		assertThat(reply).contains("available=1");
	}
}
