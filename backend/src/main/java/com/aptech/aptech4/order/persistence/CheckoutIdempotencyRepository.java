package com.aptech.aptech4.order.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.aptech.aptech4.order.domain.CheckoutIdempotency;

public interface CheckoutIdempotencyRepository extends JpaRepository<CheckoutIdempotency, UUID> {

	@Query("""
			select c from CheckoutIdempotency c
			join fetch c.order o
			where c.user.id = :userId and c.idempotencyKey = :key
			""")
	Optional<CheckoutIdempotency> findByUserIdAndKey(
			@Param("userId") UUID userId,
			@Param("key") String key
	);
}
