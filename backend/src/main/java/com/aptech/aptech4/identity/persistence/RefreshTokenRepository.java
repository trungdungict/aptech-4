package com.aptech.aptech4.identity.persistence;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.aptech.aptech4.identity.domain.RefreshToken;

import jakarta.persistence.LockModeType;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

	Optional<RefreshToken> findByTokenHash(String tokenHash);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
			select t from RefreshToken t
			join fetch t.user u
			left join fetch u.roles
			where t.tokenHash = :tokenHash
			""")
	Optional<RefreshToken> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("""
			update RefreshToken t
			set t.revokedAt = :now
			where t.user.id = :userId and t.revokedAt is null
			""")
	int revokeAllActiveForUser(@Param("userId") UUID userId, @Param("now") Instant now);

	boolean existsByTokenHash(String tokenHash);
}
