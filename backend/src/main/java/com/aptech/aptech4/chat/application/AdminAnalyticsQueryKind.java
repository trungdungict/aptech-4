package com.aptech.aptech4.chat.application;

/**
 * High-confidence Admin operational intents answered deterministically (no free-form LLM numbers).
 * {@link #NONE} leaves the turn on the general LLM path.
 */
public enum AdminAnalyticsQueryKind {
	/** Alias: ORDER_ATTENTION_COUNT — PAID + PROCESSING queue size. */
	ORDERS_NEEDING_ATTENTION,
	/** Alias: ORDER_COUNT_BY_STATUS. */
	ORDER_COUNTS_BY_STATUS,
	/** List orders filtered by status (e.g. paid). */
	ORDER_LIST_BY_STATUS,
	/** List orders filtered by total price comparison. */
	ORDER_LIST_BY_PRICE,
	SALES_SUMMARY,
	INVENTORY_LOW,
	INVENTORY_OUT,
	INVENTORY_RISK,
	/** Ambiguous currency/number — ask the user; do not query. */
	CLARIFY,
	/** GENERAL_CHAT — LLM + tools. */
	NONE
}
