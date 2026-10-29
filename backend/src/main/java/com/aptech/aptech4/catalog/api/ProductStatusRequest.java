package com.aptech.aptech4.catalog.api;

import jakarta.validation.constraints.NotNull;

public record ProductStatusRequest(
		@NotNull
		Boolean active
) {
}
