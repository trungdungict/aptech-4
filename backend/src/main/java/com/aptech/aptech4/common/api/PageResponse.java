package com.aptech.aptech4.common.api;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

import org.springframework.data.domain.Page;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Zero-based page of items with pagination metadata")
public record PageResponse<T>(
		@Schema(description = "Page items") List<T> items,
		@Schema(description = "Zero-based page index", example = "0") int page,
		@Schema(description = "Page size", example = "20") int size,
		@Schema(description = "Total matching elements", example = "42") long totalElements,
		@Schema(description = "Total pages", example = "3") int totalPages,
		@Schema(description = "Whether this is the first page") boolean first,
		@Schema(description = "Whether this is the last page") boolean last
) implements Serializable {
	@Serial
	private static final long serialVersionUID = 1L;
	public static <T> PageResponse<T> from(Page<T> page) {
		return new PageResponse<>(
				page.getContent(),
				page.getNumber(),
				page.getSize(),
				page.getTotalElements(),
				page.getTotalPages(),
				page.isFirst(),
				page.isLast()
		);
	}

	public static <T> PageResponse<T> of(List<T> items, int page, int size, long totalElements) {
		int totalPages = size <= 0 ? 0 : (int) Math.ceil((double) totalElements / (double) size);
		boolean first = page <= 0;
		boolean last = totalPages == 0 || page >= totalPages - 1;
		return new PageResponse<>(items, page, size, totalElements, totalPages, first, last);
	}
}
