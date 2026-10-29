package com.aptech.aptech4.order.persistence;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.jpa.domain.Specification;

import com.aptech.aptech4.order.domain.OrderStatus;
import com.aptech.aptech4.order.domain.ShopOrder;

import jakarta.persistence.criteria.Predicate;

/**
 * Read-only Admin order filters for chat analytics (status / total / placed window).
 */
public final class ShopOrderSpecs {

	private ShopOrderSpecs() {
	}

	public static Specification<ShopOrder> adminSearch(
			OrderStatus status,
			BigDecimal minTotal,
			BigDecimal maxTotal,
			boolean minInclusive,
			boolean maxInclusive,
			Instant from,
			Instant to
	) {
		return (root, query, cb) -> {
			List<Predicate> predicates = new ArrayList<>();
			if (status != null) {
				predicates.add(cb.equal(root.get("status"), status));
			}
			if (minTotal != null) {
				predicates.add(minInclusive
						? cb.greaterThanOrEqualTo(root.get("totalAmount"), minTotal)
						: cb.greaterThan(root.get("totalAmount"), minTotal));
			}
			if (maxTotal != null) {
				predicates.add(maxInclusive
						? cb.lessThanOrEqualTo(root.get("totalAmount"), maxTotal)
						: cb.lessThan(root.get("totalAmount"), maxTotal));
			}
			if (from != null) {
				predicates.add(cb.greaterThanOrEqualTo(root.get("placedAt"), from));
			}
			if (to != null) {
				predicates.add(cb.lessThan(root.get("placedAt"), to));
			}
			return cb.and(predicates.toArray(Predicate[]::new));
		};
	}
}
