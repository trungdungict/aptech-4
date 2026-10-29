package com.aptech.aptech4.order.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.aptech.aptech4.order.domain.OrderStatus;
import com.aptech.aptech4.order.domain.ShopOrder;

import jakarta.persistence.LockModeType;

public interface ShopOrderRepository extends JpaRepository<ShopOrder, UUID>, JpaSpecificationExecutor<ShopOrder> {

	@Query("""
			select distinct o from ShopOrder o
			left join fetch o.items i
			left join fetch i.product
			where o.id = :id
			""")
	Optional<ShopOrder> findByIdWithItems(@Param("id") UUID id);

	@Query("""
			select distinct o from ShopOrder o
			left join fetch o.items i
			left join fetch i.product
			where o.id = :id and o.user.id = :userId
			""")
	Optional<ShopOrder> findByIdAndUserIdWithItems(@Param("id") UUID id, @Param("userId") UUID userId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select o from ShopOrder o where o.id = :id")
	Optional<ShopOrder> findByIdForUpdate(@Param("id") UUID id);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select o from ShopOrder o where o.id = :id and o.user.id = :userId")
	Optional<ShopOrder> findByIdAndUserIdForUpdate(@Param("id") UUID id, @Param("userId") UUID userId);

	Page<ShopOrder> findByUser_Id(UUID userId, Pageable pageable);

	Page<ShopOrder> findByUser_IdAndStatus(UUID userId, OrderStatus status, Pageable pageable);

	Page<ShopOrder> findByStatus(OrderStatus status, Pageable pageable);
}
