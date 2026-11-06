package com.aptech.aptech4.promotion.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.aptech.aptech4.promotion.domain.PromotionStrategyTranslation;

public interface PromotionStrategyTranslationRepository extends JpaRepository<PromotionStrategyTranslation, UUID> {

	Optional<PromotionStrategyTranslation> findByStrategyIdAndLocale(UUID strategyId, String locale);

	List<PromotionStrategyTranslation> findByStrategyId(UUID strategyId);

	List<PromotionStrategyTranslation> findByStrategyIdIn(Collection<UUID> strategyIds);
}
