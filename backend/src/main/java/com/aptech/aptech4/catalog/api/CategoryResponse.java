package com.aptech.aptech4.catalog.api;

import java.io.Serial;
import java.io.Serializable;
import java.util.UUID;

public record CategoryResponse(
		UUID id,
		String name,
		String slug,
		String description,
		UUID parentId,
		boolean active
) implements Serializable {
	@Serial
	private static final long serialVersionUID = 1L;
}
