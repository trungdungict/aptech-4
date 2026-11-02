package com.aptech.aptech4.catalog.application;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;

import org.springframework.data.jpa.domain.Specification;

import com.aptech.aptech4.catalog.api.AdminInventoryStockStatus;
import com.aptech.aptech4.catalog.domain.Inventory;
import com.aptech.aptech4.catalog.domain.Product;

final class ProductSpecifications {

	private ProductSpecifications() {
	}

	static Specification<Product> publicDiscovery(
			String keyword,
			UUID categoryId,
			BigDecimal minPrice,
			BigDecimal maxPrice,
			Boolean inStock
	) {
		return (root, query, cb) -> {
			List<Predicate> predicates = new ArrayList<>();
			Join<?, ?> category = root.join("category", JoinType.INNER);
			predicates.add(cb.isTrue(root.get("active")));
			predicates.add(cb.isTrue(category.get("active")));

			if (categoryId != null) {
				predicates.add(cb.equal(category.get("id"), categoryId));
			}

			if (keyword != null && !keyword.isBlank()) {
				String pattern = "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
				Predicate nameMatch = cb.like(cb.lower(root.get("name")), pattern);
				Predicate descMatch = cb.like(cb.lower(cb.coalesce(root.get("description"), "")), pattern);
				predicates.add(cb.or(nameMatch, descMatch));
			}

			if (minPrice != null) {
				predicates.add(cb.greaterThanOrEqualTo(root.get("unitPrice"), minPrice));
			}
			if (maxPrice != null) {
				predicates.add(cb.lessThanOrEqualTo(root.get("unitPrice"), maxPrice));
			}

			if (inStock != null) {
				Join<Product, Inventory> inventory = root.join("inventory", JoinType.LEFT);
				// available = on_hand - reserved (ADR-006); use gt/le for Number expressions from diff
				var available = cb.diff(inventory.get("quantityOnHand"), inventory.get("quantityReserved"));
				if (Boolean.TRUE.equals(inStock)) {
					predicates.add(cb.gt(available, 0));
				}
				else {
					predicates.add(cb.or(
							cb.isNull(inventory.get("productId")),
							cb.le(available, 0)
					));
				}
			}

			return cb.and(predicates.toArray(Predicate[]::new));
		};
	}

	/**
	 * Admin catalog: all products (active/inactive), optional filters.
	 * Does not require category.active — admins may inspect orphaned relations.
	 */
	static Specification<Product> adminList(String keyword, UUID categoryId, Boolean active) {
		return (root, query, cb) -> {
			List<Predicate> predicates = new ArrayList<>();
			Join<?, ?> category = root.join("category", JoinType.INNER);

			if (categoryId != null) {
				predicates.add(cb.equal(category.get("id"), categoryId));
			}
			if (active != null) {
				predicates.add(cb.equal(root.get("active"), active));
			}
			if (keyword != null && !keyword.isBlank()) {
				String pattern = "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
				Predicate nameMatch = cb.like(cb.lower(root.get("name")), pattern);
				Predicate skuMatch = cb.like(cb.lower(root.get("sku")), pattern);
				Predicate descMatch = cb.like(cb.lower(cb.coalesce(root.get("description"), "")), pattern);
				predicates.add(cb.or(nameMatch, skuMatch, descMatch));
			}

			return predicates.isEmpty()
					? cb.conjunction()
					: cb.and(predicates.toArray(Predicate[]::new));
		};
	}

	/**
	 * Admin inventory list: all products (active/inactive) with optional stock-status filters.
	 * Missing inventory rows are treated as available = 0.
	 */
	static Specification<Product> adminInventoryList(
			String keyword,
			Boolean active,
			AdminInventoryStockStatus stockStatus,
			int lowStockThreshold
	) {
		return (root, query, cb) -> {
			List<Predicate> predicates = new ArrayList<>();
			root.join("category", JoinType.INNER);
			Join<Product, Inventory> inventory = root.join("inventory", JoinType.LEFT);

			if (active != null) {
				predicates.add(cb.equal(root.get("active"), active));
			}
			if (keyword != null && !keyword.isBlank()) {
				String pattern = "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
				Predicate nameMatch = cb.like(cb.lower(root.get("name")), pattern);
				Predicate skuMatch = cb.like(cb.lower(root.get("sku")), pattern);
				predicates.add(cb.or(nameMatch, skuMatch));
			}

			Expression<Integer> onHand = cb.coalesce(inventory.get("quantityOnHand"), 0);
			Expression<Integer> reserved = cb.coalesce(inventory.get("quantityReserved"), 0);
			Expression<Integer> available = cb.diff(onHand, reserved);

			AdminInventoryStockStatus status = stockStatus == null ? AdminInventoryStockStatus.ALL : stockStatus;
			switch (status) {
				case IN_STOCK -> predicates.add(cb.gt(available, lowStockThreshold));
				case LOW -> predicates.add(cb.and(
						cb.gt(available, 0),
						cb.le(available, lowStockThreshold)
				));
				case OUT -> predicates.add(cb.le(available, 0));
				case ALL -> {
					// no stock predicate
				}
			}

			return predicates.isEmpty()
					? cb.conjunction()
					: cb.and(predicates.toArray(Predicate[]::new));
		};
	}
}
