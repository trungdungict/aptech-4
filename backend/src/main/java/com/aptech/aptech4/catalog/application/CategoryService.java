package com.aptech.aptech4.catalog.application;

import java.util.List;
import java.util.UUID;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aptech.aptech4.catalog.api.CategoryCreateRequest;
import com.aptech.aptech4.catalog.api.CategoryResponse;
import com.aptech.aptech4.catalog.api.CategoryStatusRequest;
import com.aptech.aptech4.catalog.api.CategoryUpdateRequest;
import com.aptech.aptech4.catalog.cache.CatalogCacheInvalidator;
import com.aptech.aptech4.catalog.cache.CatalogCacheNames;
import com.aptech.aptech4.catalog.domain.Category;
import com.aptech.aptech4.catalog.persistence.CategoryRepository;
import com.aptech.aptech4.common.api.ApiException;

@Service
public class CategoryService {

	private final CategoryRepository categoryRepository;
	private final CatalogCacheInvalidator catalogCacheInvalidator;

	public CategoryService(
			CategoryRepository categoryRepository,
			CatalogCacheInvalidator catalogCacheInvalidator
	) {
		this.categoryRepository = categoryRepository;
		this.catalogCacheInvalidator = catalogCacheInvalidator;
	}

	@Transactional(readOnly = true)
	@Cacheable(cacheNames = CatalogCacheNames.CATEGORIES_LIST, key = "T(com.aptech.aptech4.catalog.cache.CatalogCacheKeys).CATEGORIES_ALL")
	public List<CategoryResponse> listPublicActive() {
		return categoryRepository.findAllByActiveTrueOrderByNameAsc().stream()
			.map(this::toResponse)
			.toList();
	}

	@Transactional(readOnly = true)
	@Cacheable(cacheNames = CatalogCacheNames.CATEGORIES_DETAIL, key = "T(com.aptech.aptech4.catalog.cache.CatalogCacheKeys).categoryDetail(#id)")
	public CategoryResponse getPublicActive(UUID id) {
		Category category = categoryRepository.findByIdAndActiveTrue(id)
			.orElseThrow(this::notFound);
		return toResponse(category);
	}

	@Transactional(readOnly = true)
	public List<CategoryResponse> listAdminAll() {
		return categoryRepository.findAllByOrderByNameAsc().stream()
			.map(this::toResponse)
			.toList();
	}

	@Transactional
	public CategoryResponse create(CategoryCreateRequest request) {
		String name = normalizeName(request.name());
		assertNameAvailable(name, null);
		Category parent = resolveParent(request.parentId());
		String slug = uniqueSlug(Category.slugify(name), null);
		Category category = Category.createNew(name, slug, blankToNull(request.description()), parent);
		try {
			CategoryResponse response = toResponse(categoryRepository.saveAndFlush(category));
			catalogCacheInvalidator.onCategoryMutation(response.id());
			return response;
		}
		catch (DataIntegrityViolationException ex) {
			throw nameConflict();
		}
	}

	@Transactional
	public CategoryResponse update(UUID id, CategoryUpdateRequest request) {
		Category category = categoryRepository.findById(id).orElseThrow(this::notFound);
		String name = normalizeName(request.name());
		assertNameAvailable(name, id);
		if (request.parentId() != null && request.parentId().equals(id)) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "Bad Request", "Category cannot be its own parent");
		}
		Category parent = resolveParent(request.parentId());
		category.setName(name);
		category.setSlug(uniqueSlug(Category.slugify(name), id));
		category.setDescription(blankToNull(request.description()));
		category.setParent(parent);
		category.touch();
		try {
			CategoryResponse response = toResponse(categoryRepository.saveAndFlush(category));
			catalogCacheInvalidator.onCategoryMutation(id);
			return response;
		}
		catch (DataIntegrityViolationException ex) {
			throw nameConflict();
		}
	}

	@Transactional
	public CategoryResponse updateStatus(UUID id, CategoryStatusRequest request) {
		Category category = categoryRepository.findById(id).orElseThrow(this::notFound);
		category.setActive(Boolean.TRUE.equals(request.active()));
		category.touch();
		CategoryResponse response = toResponse(categoryRepository.save(category));
		catalogCacheInvalidator.onCategoryMutation(id);
		return response;
	}

	private Category resolveParent(UUID parentId) {
		if (parentId == null) {
			return null;
		}
		return categoryRepository.findById(parentId).orElseThrow(
				() -> new ApiException(HttpStatus.BAD_REQUEST, "Bad Request", "Parent category not found")
		);
	}

	private void assertNameAvailable(String name, UUID excludeId) {
		boolean taken = excludeId == null
				? categoryRepository.existsByNameIgnoreCase(name)
				: categoryRepository.existsByNameIgnoreCaseAndIdNot(name, excludeId);
		if (taken) {
			throw nameConflict();
		}
	}

	private String uniqueSlug(String base, UUID excludeId) {
		String candidate = base;
		int suffix = 2;
		while (slugTaken(candidate, excludeId)) {
			String withSuffix = base + "-" + suffix;
			candidate = withSuffix.length() <= 140 ? withSuffix : base.substring(0, Math.max(1, 140 - ("-" + suffix).length())) + "-" + suffix;
			suffix++;
			if (suffix > 1000) {
				throw new ApiException(HttpStatus.CONFLICT, "Conflict", "Unable to allocate unique slug");
			}
		}
		return candidate;
	}

	private boolean slugTaken(String slug, UUID excludeId) {
		return excludeId == null
				? categoryRepository.existsBySlug(slug)
				: categoryRepository.existsBySlugAndIdNot(slug, excludeId);
	}

	private CategoryResponse toResponse(Category category) {
		UUID parentId = category.getParent() == null ? null : category.getParent().getId();
		return new CategoryResponse(
				category.getId(),
				category.getName(),
				category.getSlug(),
				category.getDescription(),
				parentId,
				category.isActive()
		);
	}

	private static String normalizeName(String name) {
		return name.trim().replaceAll("\\s+", " ");
	}

	private static String blankToNull(String value) {
		if (value == null) {
			return null;
		}
		String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}

	private ApiException notFound() {
		return new ApiException(HttpStatus.NOT_FOUND, "Not Found", "Category not found");
	}

	private ApiException nameConflict() {
		return new ApiException(HttpStatus.CONFLICT, "Conflict", "Category name already exists");
	}
}
