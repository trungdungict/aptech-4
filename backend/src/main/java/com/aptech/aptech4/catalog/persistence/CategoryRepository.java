package com.aptech.aptech4.catalog.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.aptech.aptech4.catalog.domain.Category;

public interface CategoryRepository extends JpaRepository<Category, UUID> {

	List<Category> findAllByActiveTrueOrderByNameAsc();

	List<Category> findAllByOrderByNameAsc();

	Optional<Category> findByIdAndActiveTrue(UUID id);

	boolean existsByNameIgnoreCase(String name);

	boolean existsByNameIgnoreCaseAndIdNot(String name, UUID id);

	boolean existsBySlug(String slug);

	boolean existsBySlugAndIdNot(String slug, UUID id);

	Optional<Category> findBySlug(String slug);
}
