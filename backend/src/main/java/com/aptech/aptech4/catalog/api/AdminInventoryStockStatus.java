package com.aptech.aptech4.catalog.api;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Admin inventory list stock filter. Based on
 * {@code available = coalesce(on_hand,0) - coalesce(reserved,0)} (missing inventory row → 0).
 */
@Schema(description = "Stock filter for admin inventory list")
public enum AdminInventoryStockStatus {
	/** No stock filter. */
	ALL,
	/** available &gt; low-stock threshold */
	IN_STOCK,
	/** 0 &lt; available &lt;= low-stock threshold */
	LOW,
	/** available &lt;= 0 (includes products with no inventory row) */
	OUT
}
