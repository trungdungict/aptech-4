package com.aptech.aptech4.promotion.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.aptech.aptech4.promotion.domain.PromotionPlacementTranslation;

public interface PromotionPlacementTranslationRepository extends JpaRepository<PromotionPlacementTranslation, UUID> {

	Optional<PromotionPlacementTranslation> findByPlacementIdAndLocale(UUID placementId, String locale);

	List<PromotionPlacementTranslation> findByPlacementId(UUID placementId);

	List<PromotionPlacementTranslation> findByPlacementIdInAndLocale(Collection<UUID> placementIds, String locale);

	List<PromotionPlacementTranslation> findByPlacementIdIn(Collection<UUID> placementIds);
}
