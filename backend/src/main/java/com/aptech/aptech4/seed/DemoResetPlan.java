package com.aptech.aptech4.seed;

import java.util.List;

/**
 * Preflight snapshot of what a demo reset would touch (dry-run friendly).
 */
public record DemoResetPlan(
		String jdbcUrl,
		List<String> demoEmails,
		List<String> demoCategorySlugs,
		List<String> demoProductSkus,
		int demoUsers,
		int demoCategories,
		int demoProducts,
		int dependentOrders,
		int dependentCarts,
		int dependentReservations
) {
}
