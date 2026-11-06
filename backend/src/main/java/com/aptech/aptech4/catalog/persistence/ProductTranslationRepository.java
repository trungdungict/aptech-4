package com.aptech.aptech4.catalog.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.aptech.aptech4.catalog.domain.ProductTranslation;

public interface ProductTranslationRepository extends JpaRepository<ProductTranslation, UUID> {

	Optional<ProductTranslation> findByProductIdAndLocale(UUID productId, String locale);

	List<ProductTranslation> findByProductId(UUID productId);

	List<ProductTranslation> findByProductIdInAndLocale(Collection<UUID> productIds, String locale);

	List<ProductTranslation> findByProductIdIn(Collection<UUID> productIds);

	void deleteByProductIdAndLocale(UUID productId, String locale);
}
