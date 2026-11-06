package com.aptech.aptech4.catalog.application;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aptech.aptech4.catalog.domain.Category;
import com.aptech.aptech4.catalog.domain.CategoryTranslation;
import com.aptech.aptech4.catalog.domain.Product;
import com.aptech.aptech4.catalog.domain.ProductTranslation;
import com.aptech.aptech4.catalog.persistence.CategoryTranslationRepository;
import com.aptech.aptech4.catalog.persistence.ProductTranslationRepository;
import com.aptech.aptech4.common.locale.AppLocale;
import com.aptech.aptech4.common.locale.ContentLocalization;
import com.aptech.aptech4.common.locale.LocalizedText;
import com.aptech.aptech4.common.locale.RequestLocaleHolder;

@Service
public class CatalogLocalizationService {

	private final ProductTranslationRepository productTranslationRepository;
	private final CategoryTranslationRepository categoryTranslationRepository;

	public CatalogLocalizationService(
			ProductTranslationRepository productTranslationRepository,
			CategoryTranslationRepository categoryTranslationRepository
	) {
		this.productTranslationRepository = productTranslationRepository;
		this.categoryTranslationRepository = categoryTranslationRepository;
	}

	@Transactional(readOnly = true)
	public LocalizedText productText(Product product) {
		return productText(product, RequestLocaleHolder.get());
	}

	@Transactional(readOnly = true)
	public LocalizedText productText(Product product, AppLocale locale) {
		ProductTranslation row = productTranslationRepository
				.findByProductIdAndLocale(product.getId(), locale.code())
				.orElse(null);
		return ContentLocalization.resolve(
				row != null,
				row == null ? null : row.getName(),
				row == null ? null : row.getDescription(),
				product.getName(),
				product.getDescription()
		);
	}

	@Transactional(readOnly = true)
	public Map<UUID, LocalizedText> productTexts(Collection<Product> products, AppLocale locale) {
		if (products == null || products.isEmpty()) {
			return Map.of();
		}
		List<UUID> ids = products.stream().map(Product::getId).toList();
		Map<UUID, ProductTranslation> byProduct = productTranslationRepository
				.findByProductIdInAndLocale(ids, locale.code())
				.stream()
				.collect(Collectors.toMap(t -> t.getProduct().getId(), Function.identity(), (a, b) -> a));
		Map<UUID, LocalizedText> out = new HashMap<>();
		for (Product product : products) {
			ProductTranslation row = byProduct.get(product.getId());
			out.put(
					product.getId(),
					ContentLocalization.resolve(
							row != null,
							row == null ? null : row.getName(),
							row == null ? null : row.getDescription(),
							product.getName(),
							product.getDescription()
					)
			);
		}
		return out;
	}

	@Transactional(readOnly = true)
	public LocalizedText categoryText(Category category) {
		return categoryText(category, RequestLocaleHolder.get());
	}

	@Transactional(readOnly = true)
	public LocalizedText categoryText(Category category, AppLocale locale) {
		CategoryTranslation row = categoryTranslationRepository
				.findByCategoryIdAndLocale(category.getId(), locale.code())
				.orElse(null);
		return ContentLocalization.resolve(
				row != null,
				row == null ? null : row.getName(),
				row == null ? null : row.getDescription(),
				category.getName(),
				category.getDescription()
		);
	}

	@Transactional(readOnly = true)
	public Map<UUID, LocalizedText> categoryTexts(Collection<Category> categories, AppLocale locale) {
		if (categories == null || categories.isEmpty()) {
			return Map.of();
		}
		List<UUID> ids = categories.stream().map(Category::getId).toList();
		Map<UUID, CategoryTranslation> byCategory = categoryTranslationRepository
				.findByCategoryIdInAndLocale(ids, locale.code())
				.stream()
				.collect(Collectors.toMap(t -> t.getCategory().getId(), Function.identity(), (a, b) -> a));
		Map<UUID, LocalizedText> out = new HashMap<>();
		for (Category category : categories) {
			CategoryTranslation row = byCategory.get(category.getId());
			out.put(
					category.getId(),
					ContentLocalization.resolve(
							row != null,
							row == null ? null : row.getName(),
							row == null ? null : row.getDescription(),
							category.getName(),
							category.getDescription()
					)
			);
		}
		return out;
	}

	/**
	 * Upserts the VI translation row and keeps it aligned with canonical base columns.
	 */
	@Transactional
	public void syncCanonicalViProduct(Product product) {
		upsertProductTranslation(product, AppLocale.VI.code(), product.getName(), product.getDescription());
	}

	@Transactional
	public void syncCanonicalViCategory(Category category) {
		upsertCategoryTranslation(category, AppLocale.VI.code(), category.getName(), category.getDescription());
	}

	@Transactional
	public void upsertProductTranslation(Product product, String locale, String name, String description) {
		ProductTranslation row = productTranslationRepository
				.findByProductIdAndLocale(product.getId(), locale)
				.orElseGet(() -> ProductTranslation.createNew(product, locale, name, description));
		row.setName(name);
		row.setDescription(description);
		productTranslationRepository.save(row);
	}

	@Transactional
	public void upsertCategoryTranslation(Category category, String locale, String name, String description) {
		CategoryTranslation row = categoryTranslationRepository
				.findByCategoryIdAndLocale(category.getId(), locale)
				.orElseGet(() -> CategoryTranslation.createNew(category, locale, name, description));
		row.setName(name);
		row.setDescription(description);
		categoryTranslationRepository.save(row);
	}

	@Transactional(readOnly = true)
	public Map<String, LocalizedText> productTranslationMap(UUID productId, Product product) {
		Map<String, LocalizedText> map = new HashMap<>();
		map.put(AppLocale.VI.code(), LocalizedText.of(product.getName(), product.getDescription()));
		map.put(AppLocale.EN.code(), LocalizedText.of(null, null));
		for (ProductTranslation row : productTranslationRepository.findByProductId(productId)) {
			map.put(row.getLocale(), LocalizedText.of(row.getName(), row.getDescription()));
		}
		// VI always reflects canonical base for editors.
		map.put(AppLocale.VI.code(), LocalizedText.of(product.getName(), product.getDescription()));
		return map;
	}

	@Transactional(readOnly = true)
	public Map<String, LocalizedText> categoryTranslationMap(UUID categoryId, Category category) {
		Map<String, LocalizedText> map = new HashMap<>();
		map.put(AppLocale.VI.code(), LocalizedText.of(category.getName(), category.getDescription()));
		map.put(AppLocale.EN.code(), LocalizedText.of(null, null));
		for (CategoryTranslation row : categoryTranslationRepository.findByCategoryId(categoryId)) {
			map.put(row.getLocale(), LocalizedText.of(row.getName(), row.getDescription()));
		}
		map.put(AppLocale.VI.code(), LocalizedText.of(category.getName(), category.getDescription()));
		return map;
	}
}
