package com.aptech.aptech4.chat.application;

/**
 * High-confidence Customer chat intents answered deterministically (JWT-scoped tools).
 */
public enum CustomerIntentKind {
	LATEST_ORDER,
	ORDER_COUNT,
	ORDER_LIST_BY_STATUS,
	ORDER_COUNT_BY_STATUS,
	PRODUCT_UNDER_PRICE,
	CHECKOUT_HELP,
	/** Authenticated Customer asked for Admin/store-wide analytics. */
	ADMIN_ANALYTICS_FORBIDDEN,
	NONE
}
