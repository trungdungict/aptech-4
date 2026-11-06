package com.aptech.aptech4.promotion.application;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aptech.aptech4.common.api.ApiException;
import com.aptech.aptech4.promotion.api.PromotionStrategyResponse;
import com.aptech.aptech4.promotion.api.PromotionStrategyStatusRequest;
import com.aptech.aptech4.promotion.api.PromotionStrategyWriteRequest;
import com.aptech.aptech4.promotion.cache.PromotionCacheInvalidator;
import com.aptech.aptech4.promotion.domain.PromotionStrategy;
import com.aptech.aptech4.promotion.persistence.PromotionStrategyRepository;

@Service
public class PromotionStrategyService {

	private final PromotionStrategyRepository strategyRepository;
	private final PromotionCacheInvalidator promotionCacheInvalidator;
	private final PromotionLocalizationService promotionLocalizationService;

	public PromotionStrategyService(
			PromotionStrategyRepository strategyRepository,
			PromotionCacheInvalidator promotionCacheInvalidator,
			PromotionLocalizationService promotionLocalizationService
	) {
		this.strategyRepository = strategyRepository;
		this.promotionCacheInvalidator = promotionCacheInvalidator;
		this.promotionLocalizationService = promotionLocalizationService;
	}

	@Transactional(readOnly = true)
	public List<PromotionStrategyResponse> list() {
		return strategyRepository.findAll().stream()
				.sorted((a, b) -> a.getName().compareToIgnoreCase(b.getName()))
				.map(PromotionStrategyResponse::from)
				.toList();
	}

	@Transactional(readOnly = true)
	public PromotionStrategyResponse get(UUID id) {
		return PromotionStrategyResponse.from(require(id));
	}

	@Transactional
	public PromotionStrategyResponse create(PromotionStrategyWriteRequest request) {
		assertUniqueName(request.name(), null);
		PromotionStrategy strategy = PromotionStrategy.createNew(
				request.name().trim(),
				blankToNull(request.code()),
				request.type(),
				request.discountPercent(),
				request.discountAmount(),
				request.maxDiscountAmount(),
				request.minOrderAmount(),
				request.minQuantity(),
				Boolean.TRUE.equals(request.stackable()),
				request.priority() == null ? 100 : request.priority(),
				request.active() == null || request.active()
		);
		strategy = strategyRepository.saveAndFlush(strategy);
		promotionLocalizationService.syncCanonicalViStrategy(strategy);
		promotionCacheInvalidator.onPromotionMutation();
		return PromotionStrategyResponse.from(strategy);
	}

	@Transactional
	public PromotionStrategyResponse update(UUID id, PromotionStrategyWriteRequest request) {
		PromotionStrategy strategy = require(id);
		assertUniqueName(request.name(), id);
		strategy.applyUpdate(
				request.name().trim(),
				blankToNull(request.code()),
				request.type(),
				request.discountPercent(),
				request.discountAmount(),
				request.maxDiscountAmount(),
				request.minOrderAmount(),
				request.minQuantity(),
				request.stackable() == null ? strategy.isStackable() : request.stackable(),
				request.priority() == null ? strategy.getPriority() : request.priority(),
				request.active() == null ? strategy.isActive() : request.active()
		);
		strategy = strategyRepository.saveAndFlush(strategy);
		promotionLocalizationService.syncCanonicalViStrategy(strategy);
		promotionCacheInvalidator.onPromotionMutation();
		return PromotionStrategyResponse.from(strategy);
	}

	@Transactional
	public PromotionStrategyResponse updateStatus(UUID id, PromotionStrategyStatusRequest request) {
		PromotionStrategy strategy = require(id);
		strategy.setActive(Boolean.TRUE.equals(request.active()));
		strategy = strategyRepository.saveAndFlush(strategy);
		promotionCacheInvalidator.onPromotionMutation();
		return PromotionStrategyResponse.from(strategy);
	}

	private PromotionStrategy require(UUID id) {
		return strategyRepository.findById(id).orElseThrow(() ->
				ApiException.keyed(HttpStatus.NOT_FOUND, "Not Found", "api.error.not_found.strategy")
		);
	}

	private void assertUniqueName(String name, UUID excludeId) {
		boolean exists = excludeId == null
				? strategyRepository.existsByNameIgnoreCase(name.trim())
				: strategyRepository.existsByNameIgnoreCaseAndIdNot(name.trim(), excludeId);
		if (exists) {
			throw new ApiException(HttpStatus.CONFLICT, "Conflict", "Promotion strategy name already exists");
		}
	}

	private static String blankToNull(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		return value.trim();
	}
}
