package com.aptech.aptech4.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "roles")
public class Role {

	@Id
	@Column(name = "id", nullable = false)
	private Short id;

	@Enumerated(EnumType.STRING)
	@Column(name = "code", nullable = false, unique = true, length = 32)
	private RoleCode code;

	@Column(name = "description", nullable = false, length = 255)
	private String description;

	protected Role() {
	}

	public Short getId() {
		return id;
	}

	public void setId(Short id) {
		this.id = id;
	}

	public RoleCode getCode() {
		return code;
	}

	public void setCode(RoleCode code) {
		this.code = code;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (other == null || getClass() != other.getClass()) {
			return false;
		}
		Short otherId = ((Role) other).id;
		return id != null && id.equals(otherId);
	}

	@Override
	public int hashCode() {
		return getClass().hashCode();
	}

	@Override
	public String toString() {
		return "Role{id=" + id + ", code=" + code + '}';
	}
}
