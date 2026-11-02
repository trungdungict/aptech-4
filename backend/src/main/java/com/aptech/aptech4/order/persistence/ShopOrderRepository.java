package com.aptech.aptech4.order.persistence;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
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

	List<ShopOrder> findByStatusIn(Collection<OrderStatus> statuses, Pageable pageable);

	@Query("""
			select o.status, count(o) from ShopOrder o
			where o.placedAt >= :from and o.placedAt < :to
			group by o.status
			""")
	List<Object[]> countGroupedByStatus(@Param("from") Instant from, @Param("to") Instant to);

	@Query("""
			select coalesce(sum(o.totalAmount), 0) from ShopOrder o
			where o.status in :statuses and o.placedAt >= :from and o.placedAt < :to
			""")
	BigDecimal sumTotalsByStatuses(
			@Param("statuses") Collection<OrderStatus> statuses,
			@Param("from") Instant from,
			@Param("to") Instant to
	);

	@Query("""
			select count(o) from ShopOrder o
			where o.status in :statuses and o.placedAt >= :from and o.placedAt < :to
			""")
	long countByStatuses(
			@Param("statuses") Collection<OrderStatus> statuses,
			@Param("from") Instant from,
			@Param("to") Instant to
	);

	@Query("""
			select count(o) from ShopOrder o
			where o.placedAt >= :from and o.placedAt < :to
			""")
	long countPlacedBetween(@Param("from") Instant from, @Param("to") Instant to);

	@Query("""
			select oi.productSku, oi.productName, oi.product.id, sum(oi.quantity), sum(oi.lineTotal)
			from OrderItem oi join oi.order o
			where o.status in :statuses and o.placedAt >= :from and o.placedAt < :to
			group by oi.productSku, oi.productName, oi.product.id
			order by sum(oi.quantity) desc
			""")
	List<Object[]> topSellingProducts(
			@Param("statuses") Collection<OrderStatus> statuses,
			@Param("from") Instant from,
			@Param("to") Instant to,
			Pageable pageable
	);

	/**
	 * Daily UTC buckets: day (java.sql.Date), placed count, revenue for REVENUE statuses only.
	 * Native so date_trunc is reliable on PostgreSQL.
	 */
	@Query(value = """
			SELECT CAST((o.placed_at AT TIME ZONE 'UTC') AS date) AS day,
			       COUNT(*) AS placed_count,
			       COALESCE(SUM(CASE
			         WHEN o.status IN ('PAID', 'PROCESSING', 'SHIPPED', 'COMPLETED')
			         THEN o.total_amount ELSE 0 END), 0) AS revenue
			FROM orders o
			WHERE o.placed_at >= :from AND o.placed_at < :to
			GROUP BY 1
			ORDER BY 1
			""", nativeQuery = true)
	List<Object[]> dailyPlacedAndRevenue(@Param("from") Instant from, @Param("to") Instant to);

	@Query("""
			select c.id, c.name, sum(oi.quantity), sum(oi.lineTotal), count(oi)
			from OrderItem oi
			join oi.order o
			join oi.product p
			join p.category c
			where o.status in :statuses
			  and o.placedAt >= :from and o.placedAt < :to
			  and oi.product is not null
			group by c.id, c.name
			order by sum(oi.lineTotal) desc, sum(oi.quantity) desc
			""")
	List<Object[]> categorySales(
			@Param("statuses") Collection<OrderStatus> statuses,
			@Param("from") Instant from,
			@Param("to") Instant to,
			Pageable pageable
	);

	@Query("""
			select coalesce(sum(o.discountAmount), 0) from ShopOrder o
			where o.status in :statuses and o.placedAt >= :from and o.placedAt < :to
			""")
	BigDecimal sumDiscountByStatuses(
			@Param("statuses") Collection<OrderStatus> statuses,
			@Param("from") Instant from,
			@Param("to") Instant to
	);

	@Query("""
			select count(distinct o.id) from ShopOrder o
			where o.status in :statuses
			  and o.placedAt >= :from and o.placedAt < :to
			  and exists (select 1 from OrderPromotionSnapshot s where s.order = o)
			""")
	long countOrdersWithPromotionSnapshots(
			@Param("statuses") Collection<OrderStatus> statuses,
			@Param("from") Instant from,
			@Param("to") Instant to
	);

	@Query("""
			select s.campaignSlug, s.strategyCode,
			       max(s.campaignName), max(s.strategyName), max(s.strategyType),
			       sum(s.discountAmount), count(distinct o.id)
			from OrderPromotionSnapshot s join s.order o
			where o.status in :statuses and o.placedAt >= :from and o.placedAt < :to
			group by s.campaignSlug, s.strategyCode
			order by sum(s.discountAmount) desc
			""")
	List<Object[]> promotionBreakdown(
			@Param("statuses") Collection<OrderStatus> statuses,
			@Param("from") Instant from,
			@Param("to") Instant to
	);
}
