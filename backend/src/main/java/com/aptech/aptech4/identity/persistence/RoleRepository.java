package com.aptech.aptech4.identity.persistence;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.aptech.aptech4.identity.domain.Role;
import com.aptech.aptech4.identity.domain.RoleCode;

public interface RoleRepository extends JpaRepository<Role, Short> {

	Optional<Role> findByCode(RoleCode code);
}
