package com.aptech.aptech4.chat.application;

import java.math.BigDecimal;

import com.aptech.aptech4.order.domain.OrderStatus;

/**
 * Result of {@link AdminIntentRouter#route(String)}.
 */
public record AdminIntentMatch(
		AdminAnalyticsQueryKind kind,
		OrderStatus status,
		BigDecimal minTotal,
		BigDecimal maxTotal,
		boolean minInclusive,
		boolean maxInclusive,
		String clarifyMessageEn,
		String clarifyMessageVi
) {
	public static AdminIntentMatch of(AdminAnalyticsQueryKind kind) {
		return new AdminIntentMatch(kind, null, null, null, true, true, null, null);
	}

	public static AdminIntentMatch listByStatus(OrderStatus status) {
		return new AdminIntentMatch(
				AdminAnalyticsQueryKind.ORDER_LIST_BY_STATUS, status, null, null, true, true, null, null
		);
	}

	public static AdminIntentMatch listByPrice(
			BigDecimal minTotal,
			BigDecimal maxTotal,
			boolean minInclusive,
			boolean maxInclusive
	) {
		return new AdminIntentMatch(
				AdminAnalyticsQueryKind.ORDER_LIST_BY_PRICE,
				null,
				minTotal,
				maxTotal,
				minInclusive,
				maxInclusive,
				null,
				null
		);
	}

	public static AdminIntentMatch clarify(String en, String vi) {
		return new AdminIntentMatch(AdminAnalyticsQueryKind.CLARIFY, null, null, null, true, true, en, vi);
	}

	public static AdminIntentMatch none() {
		return of(AdminAnalyticsQueryKind.NONE);
	}
}
