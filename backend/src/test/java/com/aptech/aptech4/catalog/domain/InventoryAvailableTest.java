package com.aptech.aptech4.catalog.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

/**
 * ADR-006 invariant: available = on_hand - reserved.
 */
class InventoryAvailableTest {

	@Test
	void availableIsOnHandMinusReserved() {
		Category category = Category.createNew("Inv Cat", "inv-cat", null, null);
		Product product = Product.createNew(
				category, "SKU-INV", "Inv Product", null, new BigDecimal("10.00"), "VND", true);
		Inventory inventory = Inventory.forProduct(product, 10);

		assertThat(inventory.getAvailable()).isEqualTo(10);
		assertThat(inventory.getQuantityReserved()).isZero();

		inventory.setQuantityReserved(3);
		assertThat(inventory.getAvailable()).isEqualTo(7);

		inventory.setQuantityOnHand(3);
		inventory.setQuantityReserved(3);
		assertThat(inventory.getAvailable()).isZero();
	}
}
