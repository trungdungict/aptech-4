package com.aptech.aptech4.cart.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.aptech.aptech4.cart.domain.CartItem;

public interface CartItemRepository extends JpaRepository<CartItem, UUID> {

	@Query("""
			select i from CartItem i
			join fetch i.cart c
			join fetch i.product p
			left join fetch p.category
			where i.id = :itemId and c.user.id = :userId
			""")
	Optional<CartItem> findByIdAndCartUserId(@Param("itemId") UUID itemId, @Param("userId") UUID userId);

	@Query("""
			select i from CartItem i
			join fetch i.product
			where i.cart.id = :cartId and i.product.id = :productId
			""")
	Optional<CartItem> findByCartIdAndProductId(@Param("cartId") UUID cartId, @Param("productId") UUID productId);
}
