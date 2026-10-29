package com.aptech.aptech4.catalog.api;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CategoryUpdateRequest(
		@NotBlank
		@Size(max = 120)
		String name,

		@Size(max = 4000)
		String description,

		UUID parentId
) {
}
