package com.aptech.aptech4.catalog.application;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;

import com.aptech.aptech4.common.api.ApiException;

/**
 * Whitelists admin inventory list sort fields. Always appends {@code id} as a stable tie-breaker.
 */
public final class InventorySortSupport {

	public static final String DEFAULT_SORT = "name,asc";

	private static final Map<String, String> FIELD_MAP = Map.of(
			"name", "name",
			"sku", "sku",
			"active", "active",
			"createdat", "createdAt",
			"quantityonhand", "inventory.quantityOnHand",
			"quantityreserved", "inventory.quantityReserved",
			"updatedat", "inventory.updatedAt"
	);

	private static final Set<String> ALLOWED = Set.of(
			"name", "sku", "active", "createdat", "quantityonhand", "quantityreserved", "updatedat"
	);

	private InventorySortSupport() {
	}

	static Sort resolve(String sortParam) {
		String raw = (sortParam == null || sortParam.isBlank()) ? DEFAULT_SORT : sortParam.trim();
		String[] parts = raw.split(",", 2);
		String fieldKey = parts[0].trim().toLowerCase(Locale.ROOT);
		if (!ALLOWED.contains(fieldKey)) {
			throw new ApiException(
					HttpStatus.BAD_REQUEST,
					"Bad Request",
					"Invalid sort field. Allowed: name, sku, active, createdAt, quantityOnHand, quantityReserved, updatedAt"
			);
		}
		Sort.Direction direction = Sort.Direction.ASC;
		if (parts.length > 1) {
			String dir = parts[1].trim().toLowerCase(Locale.ROOT);
			if ("desc".equals(dir)) {
				direction = Sort.Direction.DESC;
			}
			else if (!"asc".equals(dir)) {
				throw new ApiException(HttpStatus.BAD_REQUEST, "Bad Request", "Invalid sort direction. Use asc or desc");
			}
		}
		String property = FIELD_MAP.get(fieldKey);
		Sort.Order primary = new Sort.Order(direction, property).nullsLast();
		return Sort.by(primary).and(Sort.by(Sort.Direction.ASC, "id"));
	}
}
