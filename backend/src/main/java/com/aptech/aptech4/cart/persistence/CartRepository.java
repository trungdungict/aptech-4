package com.aptech.aptech4.cart.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.aptech.aptech4.cart.domain.Cart;

import jakarta.persistence.LockModeType;

public interface CartRepository extends JpaRepository<Cart, UUID> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select c from Cart c where c.user.id = :userId")
	Optional<Cart> findByUserIdForUpdate(@Param("userId") UUID userId);

	@Query("""
			select distinct c from Cart c
			left join fetch c.items i
			left join fetch i.product p
			left join fetch p.category
			where c.user.id = :userId
			""")
	Optional<Cart> findByUserIdWithItems(@Param("userId") UUID userId);
}
