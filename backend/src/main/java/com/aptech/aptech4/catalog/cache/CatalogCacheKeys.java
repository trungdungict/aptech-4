package com.aptech.aptech4.catalog.cache;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.UUID;

import com.aptech.aptech4.catalog.application.ProductSortSupport;
import com.aptech.aptech4.common.locale.AppLocale;
import com.aptech.aptech4.common.locale.RequestLocaleHolder;

/**
 * Deterministic cache keys for public catalog queries (ADR-015).
 * Absent optionals use {@code -} so null vs empty keyword do not collide incorrectly
 * (empty/blank keywords normalize to {@code -} same as null, matching service behavior).
 * Localized payloads include the request {@link AppLocale} code.
 */
public final class CatalogCacheKeys {

	private CatalogCacheKeys() {
	}

	/** Locale-aware “all categories” key for the current request locale. */
	public static String categoriesAll() {
		return "all|" + currentLocaleCode();
	}

	public static String productList(
			String q,
			UUID categoryId,
			BigDecimal minPrice,
			BigDecimal maxPrice,
			Boolean inStock,
			int page,
			int size,
			String sort
	) {
		return String.join(
				"|",
				nullToDash(normalizeKeyword(q)),
				nullToDash(categoryId == null ? null : categoryId.toString()),
				nullToDash(normalizeDecimal(minPrice)),
				nullToDash(normalizeDecimal(maxPrice)),
				nullToDash(inStock == null ? null : inStock.toString()),
				Integer.toString(page),
				Integer.toString(size),
				normalizeSort(sort),
				currentLocaleCode()
		);
	}

	public static String productDetail(UUID id) {
		return id.toString() + "|" + currentLocaleCode();
	}

	public static String categoryDetail(UUID id) {
		return id.toString() + "|" + currentLocaleCode();
	}

	static String currentLocaleCode() {
		AppLocale locale = RequestLocaleHolder.get();
		return locale == null ? AppLocale.DEFAULT.code() : locale.code();
	}

	static String normalizeKeyword(String q) {
		if (q == null) {
			return null;
		}
		String trimmed = q.trim().replaceAll("\\s+", " ");
		return trimmed.isEmpty() ? null : trimmed;
	}

	static String normalizeSort(String sort) {
		if (sort == null || sort.isBlank()) {
			return ProductSortSupport.DEFAULT_SORT.toLowerCase(Locale.ROOT);
		}
		return sort.trim().toLowerCase(Locale.ROOT);
	}

	static String normalizeDecimal(BigDecimal value) {
		if (value == null) {
			return null;
		}
		return value.stripTrailingZeros().toPlainString();
	}

	private static String nullToDash(String value) {
		return value == null ? "-" : value;
	}
}
