package com.aptech.aptech4.seed;

public record DemoResetResult(
		int usersDeleted,
		int categoriesDeleted,
		int productsDeleted,
		int ordersDeleted,
		int cartsDeleted,
		int reservationsDeleted,
		boolean reseeding
) {
}
