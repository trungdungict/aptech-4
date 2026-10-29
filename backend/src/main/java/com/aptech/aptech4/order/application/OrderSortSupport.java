package com.aptech.aptech4.order.application;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;

import com.aptech.aptech4.common.api.ApiException;

final class OrderSortSupport {

	static final String DEFAULT_SORT = "placedAt,desc";

	private static final Map<String, String> FIELD_MAP = Map.of(
			"placedat", "placedAt",
			"totalamount", "totalAmount",
			"status", "status"
	);

	private static final Set<String> ALLOWED = Set.of("placedat", "totalamount", "status");

	private OrderSortSupport() {
	}

	static Sort resolve(String sortParam) {
		String raw = (sortParam == null || sortParam.isBlank()) ? DEFAULT_SORT : sortParam.trim();
		String[] parts = raw.split(",", 2);
		String fieldKey = parts[0].trim().toLowerCase(Locale.ROOT);
		if (!ALLOWED.contains(fieldKey)) {
			throw new ApiException(
					HttpStatus.BAD_REQUEST,
					"Bad Request",
					"Invalid sort field. Allowed: placedAt, totalAmount, status"
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
		return Sort.by(direction, FIELD_MAP.get(fieldKey)).and(Sort.by(Sort.Direction.ASC, "id"));
	}
}
