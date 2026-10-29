package com.aptech.aptech4.catalog.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.aptech.aptech4.catalog.domain.ReservationStatus;
import com.aptech.aptech4.catalog.domain.StockReservation;

import jakarta.persistence.LockModeType;

public interface StockReservationRepository extends JpaRepository<StockReservation, UUID> {

	@Query("""
			select distinct r from StockReservation r
			join fetch r.user
			left join fetch r.items i
			left join fetch i.product
			where r.id = :id
			""")
	Optional<StockReservation> findByIdWithItems(@Param("id") UUID id);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select r from StockReservation r where r.id = :id")
	Optional<StockReservation> findByIdForUpdate(@Param("id") UUID id);

	@Query("""
			select distinct r from StockReservation r
			left join fetch r.items i
			left join fetch i.product
			where r.user.id = :userId and r.idempotencyKey = :idempotencyKey
			""")
	Optional<StockReservation> findByUserIdAndIdempotencyKey(
			@Param("userId") UUID userId,
			@Param("idempotencyKey") String idempotencyKey
	);

	@Query("""
			select r.id from StockReservation r
			where r.status = :status and r.expiresAt <= :now
			""")
	List<UUID> findExpiredActiveIds(@Param("status") ReservationStatus status, @Param("now") Instant now);
}
