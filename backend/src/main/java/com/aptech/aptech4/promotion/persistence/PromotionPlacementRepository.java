package com.aptech.aptech4.promotion.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.aptech.aptech4.promotion.domain.PromotionPlacement;
import com.aptech.aptech4.promotion.domain.PromotionPlacementKey;

public interface PromotionPlacementRepository extends JpaRepository<PromotionPlacement, UUID> {

	List<PromotionPlacement> findByCampaignIdOrderByDisplayOrderAscIdAsc(UUID campaignId);

	List<PromotionPlacement> findByPlacementKeyAndEnabledTrueOrderByDisplayOrderAscIdAsc(
			PromotionPlacementKey placementKey
	);
}
