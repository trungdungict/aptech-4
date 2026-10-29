package com.aptech.aptech4.common.domain;

import java.util.UUID;

/**
 * Identity-based equality for UUID entities without traversing associations.
 */
public abstract class AbstractUuidEntity {

	public abstract UUID getId();

	@Override
	public final boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (other == null || getClass() != other.getClass()) {
			return false;
		}
		UUID id = getId();
		return id != null && id.equals(((AbstractUuidEntity) other).getId());
	}

	@Override
	public final int hashCode() {
		return getClass().hashCode();
	}
}
