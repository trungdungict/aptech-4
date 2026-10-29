package com.aptech.aptech4.catalog.persistence;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.aptech.aptech4.catalog.domain.Inventory;

import jakarta.persistence.LockModeType;

public interface InventoryRepository extends JpaRepository<Inventory, UUID> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select i from Inventory i where i.productId = :productId")
	Optional<Inventory> findByProductIdForUpdate(@Param("productId") UUID productId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select i from Inventory i where i.productId in :productIds order by i.productId asc")
	List<Inventory> findAllByProductIdInForUpdate(@Param("productIds") Collection<UUID> productIds);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query(value = """
			UPDATE inventory
			SET quantity_reserved = quantity_reserved + :qty,
			    updated_at = :now
			WHERE product_id = :productId
			  AND quantity_on_hand - quantity_reserved >= :qty
			""", nativeQuery = true)
	int tryReserve(@Param("productId") UUID productId, @Param("qty") int qty, @Param("now") Instant now);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query(value = """
			UPDATE inventory
			SET quantity_reserved = quantity_reserved - :qty,
			    updated_at = :now
			WHERE product_id = :productId
			  AND quantity_reserved >= :qty
			""", nativeQuery = true)
	int tryReleaseReserved(@Param("productId") UUID productId, @Param("qty") int qty, @Param("now") Instant now);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query(value = """
			UPDATE inventory
			SET quantity_on_hand = quantity_on_hand - :qty,
			    quantity_reserved = quantity_reserved - :qty,
			    updated_at = :now
			WHERE product_id = :productId
			  AND quantity_reserved >= :qty
			  AND quantity_on_hand >= :qty
			""", nativeQuery = true)
	int tryConfirmSale(@Param("productId") UUID productId, @Param("qty") int qty, @Param("now") Instant now);

	@Query("""
			select i from Inventory i
			join fetch i.product p
			join fetch p.category
			where p.active = true
			  and (i.quantityOnHand - i.quantityReserved) <= :threshold
			order by (i.quantityOnHand - i.quantityReserved) asc, p.name asc
			""")
	List<Inventory> findActiveLowStock(@Param("threshold") int threshold, Pageable pageable);

	@Query("""
			select i from Inventory i
			join fetch i.product p
			join fetch p.category
			where p.active = true
			  and (i.quantityOnHand - i.quantityReserved) <= 0
			order by p.name asc
			""")
	List<Inventory> findActiveOutOfStock(Pageable pageable);
}
