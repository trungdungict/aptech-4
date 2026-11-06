package com.aptech.aptech4.catalog.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.aptech.aptech4.catalog.domain.CategoryTranslation;

public interface CategoryTranslationRepository extends JpaRepository<CategoryTranslation, UUID> {

	Optional<CategoryTranslation> findByCategoryIdAndLocale(UUID categoryId, String locale);

	List<CategoryTranslation> findByCategoryId(UUID categoryId);

	List<CategoryTranslation> findByCategoryIdInAndLocale(Collection<UUID> categoryIds, String locale);

	List<CategoryTranslation> findByCategoryIdIn(Collection<UUID> categoryIds);
}
