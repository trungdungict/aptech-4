package com.aptech.aptech4.promotion.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.aptech.aptech4.promotion.domain.PromotionCampaignTranslation;

public interface PromotionCampaignTranslationRepository extends JpaRepository<PromotionCampaignTranslation, UUID> {

	Optional<PromotionCampaignTranslation> findByCampaignIdAndLocale(UUID campaignId, String locale);

	List<PromotionCampaignTranslation> findByCampaignId(UUID campaignId);

	List<PromotionCampaignTranslation> findByCampaignIdIn(Collection<UUID> campaignIds);
}
