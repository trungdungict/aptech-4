package com.aptech.aptech4.identity.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.aptech.aptech4.identity.domain.RoleCode;
import com.aptech.aptech4.identity.domain.UserAccount;

public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {

	boolean existsByEmail(String email);

	@Query("select u from UserAccount u left join fetch u.roles where u.email = :email")
	Optional<UserAccount> findByEmailWithRoles(@Param("email") String email);

	@Query("""
			select distinct u from UserAccount u
			join u.roles r
			where r.code = :roleCode and u.enabled = true
			""")
	List<UserAccount> findEnabledByRoleCode(@Param("roleCode") RoleCode roleCode);
}
