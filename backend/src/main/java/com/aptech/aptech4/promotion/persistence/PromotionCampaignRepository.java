package com.aptech.aptech4.promotion.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.aptech.aptech4.promotion.domain.PromotionCampaign;
import com.aptech.aptech4.promotion.domain.PromotionCampaignStatus;

public interface PromotionCampaignRepository extends JpaRepository<PromotionCampaign, UUID> {

	boolean existsBySlugIgnoreCase(String slug);

	boolean existsBySlugIgnoreCaseAndIdNot(String slug, UUID id);

	Optional<PromotionCampaign> findBySlugIgnoreCase(String slug);

	@Query("""
			select distinct c from PromotionCampaign c
			join fetch c.strategy
			left join fetch c.products
			left join fetch c.categories
			left join fetch c.placements
			where lower(c.slug) = lower(:slug)
			""")
	Optional<PromotionCampaign> findBySlugFully(@Param("slug") String slug);

	@Query("""
			select c from PromotionCampaign c
			join fetch c.strategy
			where c.id = :id
			""")
	Optional<PromotionCampaign> findByIdWithStrategy(@Param("id") UUID id);

	@Query("""
			select distinct c from PromotionCampaign c
			join fetch c.strategy
			left join fetch c.products
			left join fetch c.categories
			left join fetch c.placements
			where c.id = :id
			""")
	Optional<PromotionCampaign> findByIdFully(@Param("id") UUID id);

	@Query("""
			select distinct c from PromotionCampaign c
			join fetch c.strategy
			left join fetch c.placements
			order by c.priority desc, c.startsAt asc, c.id asc
			""")
	List<PromotionCampaign> findAllWithStrategyAndPlacements();

	@Query("""
			select distinct c from PromotionCampaign c
			join fetch c.strategy s
			left join fetch c.placements p
			where c.status = :status
			  and s.active = true
			  and c.startsAt <= :now
			  and c.endsAt >= :now
			""")
	List<PromotionCampaign> findPlacementEffectiveCandidates(
			@Param("status") PromotionCampaignStatus status,
			@Param("now") Instant now
	);

	/**
	 * Pricing window candidates (ACTIVE + inclusive UTC + active strategy). Caller still applies
	 * {@code isPricingEffective} (excludes {@code BANNER_ONLY}) and eligibility/mins. Products and
	 * categories are lazy-loaded within the checkout transaction.
	 */
	@Query("""
			select distinct c from PromotionCampaign c
			join fetch c.strategy s
			where c.status = :status
			  and s.active = true
			  and c.startsAt <= :now
			  and c.endsAt >= :now
			""")
	List<PromotionCampaign> findPricingWindowCandidates(
			@Param("status") PromotionCampaignStatus status,
			@Param("now") Instant now
	);

	long countByStrategyId(UUID strategyId);

	/**
	 * Lifecycle hygiene candidates (ADR-020 R9): ACTIVE campaigns whose inclusive window has ended
	 * ({@code ends_at < now}). Exact {@code ends_at == now} remains effective and is not returned.
	 */
	@Query("""
			select c from PromotionCampaign c
			where c.status = :status
			  and c.endsAt < :now
			""")
	List<PromotionCampaign> findActivePastEnd(
			@Param("status") PromotionCampaignStatus status,
			@Param("now") Instant now
	);
}
