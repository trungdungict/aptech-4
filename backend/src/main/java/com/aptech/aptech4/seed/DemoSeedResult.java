package com.aptech.aptech4.seed;

public record DemoSeedResult(
		int usersCreated,
		int usersUpdated,
		int categoriesCreated,
		int categoriesUpdated,
		int productsCreated,
		int productsUpdated,
		int inventoryUpserts
) {
}
