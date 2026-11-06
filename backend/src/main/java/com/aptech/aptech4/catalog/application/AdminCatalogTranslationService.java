package com.aptech.aptech4.catalog.application;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aptech.aptech4.catalog.api.EntityTranslationsResponse;
import com.aptech.aptech4.catalog.api.EntityTranslationsUpdateRequest;
import com.aptech.aptech4.catalog.api.TranslationLocaleTextRequest;
import com.aptech.aptech4.catalog.cache.CatalogCacheInvalidator;
import com.aptech.aptech4.catalog.domain.Category;
import com.aptech.aptech4.catalog.domain.Product;
import com.aptech.aptech4.catalog.persistence.CategoryRepository;
import com.aptech.aptech4.catalog.persistence.ProductRepository;
import com.aptech.aptech4.common.api.ApiException;
import com.aptech.aptech4.common.locale.AppLocale;

@Service
public class AdminCatalogTranslationService {

	private final ProductRepository productRepository;
	private final CategoryRepository categoryRepository;
	private final CatalogLocalizationService catalogLocalizationService;
	private final CatalogCacheInvalidator catalogCacheInvalidator;

	public AdminCatalogTranslationService(
			ProductRepository productRepository,
			CategoryRepository categoryRepository,
			CatalogLocalizationService catalogLocalizationService,
			CatalogCacheInvalidator catalogCacheInvalidator
	) {
		this.productRepository = productRepository;
		this.categoryRepository = categoryRepository;
		this.catalogLocalizationService = catalogLocalizationService;
		this.catalogCacheInvalidator = catalogCacheInvalidator;
	}

	@Transactional(readOnly = true)
	public EntityTranslationsResponse getProductTranslations(UUID productId) {
		Product product = productRepository.findAdminById(productId)
				.orElseThrow(() -> ApiException.keyed(HttpStatus.NOT_FOUND, "Not Found", "api.error.not_found.product"));
		return new EntityTranslationsResponse(productId, catalogLocalizationService.productTranslationMap(productId, product));
	}

	@Transactional
	public EntityTranslationsResponse putProductTranslations(UUID productId, EntityTranslationsUpdateRequest request) {
		Product product = productRepository.findAdminById(productId)
				.orElseThrow(() -> ApiException.keyed(HttpStatus.NOT_FOUND, "Not Found", "api.error.not_found.product"));
		TranslationLocaleTextRequest vi = requireVi(request.translations());
		product.setName(normalizeName(vi.name()));
		product.setDescription(blankToNull(vi.description()));
		product.touch();
		productRepository.saveAndFlush(product);
		catalogLocalizationService.upsertProductTranslation(
				product,
				AppLocale.VI.code(),
				product.getName(),
				product.getDescription()
		);
		TranslationLocaleTextRequest en = request.translations().get(AppLocale.EN.code());
		if (en != null && en.name() != null && !en.name().isBlank()) {
			catalogLocalizationService.upsertProductTranslation(
					product,
					AppLocale.EN.code(),
					normalizeName(en.name()),
					blankToNull(en.description())
			);
		}
		catalogCacheInvalidator.onProductMutation(productId);
		return getProductTranslations(productId);
	}

	@Transactional(readOnly = true)
	public EntityTranslationsResponse getCategoryTranslations(UUID categoryId) {
		Category category = categoryRepository.findById(categoryId)
				.orElseThrow(() -> ApiException.keyed(HttpStatus.NOT_FOUND, "Not Found", "api.error.not_found.category"));
		return new EntityTranslationsResponse(categoryId, catalogLocalizationService.categoryTranslationMap(categoryId, category));
	}

	@Transactional
	public EntityTranslationsResponse putCategoryTranslations(UUID categoryId, EntityTranslationsUpdateRequest request) {
		Category category = categoryRepository.findById(categoryId)
				.orElseThrow(() -> ApiException.keyed(HttpStatus.NOT_FOUND, "Not Found", "api.error.not_found.category"));
		TranslationLocaleTextRequest vi = requireVi(request.translations());
		String name = normalizeName(vi.name());
		boolean taken = categoryRepository.existsByNameIgnoreCaseAndIdNot(name, categoryId);
		if (taken) {
			throw ApiException.keyed(HttpStatus.CONFLICT, "Conflict", "api.error.conflict.category_name");
		}
		category.setName(name);
		category.setDescription(blankToNull(vi.description()));
		category.touch();
		categoryRepository.saveAndFlush(category);
		catalogLocalizationService.upsertCategoryTranslation(
				category,
				AppLocale.VI.code(),
				category.getName(),
				category.getDescription()
		);
		TranslationLocaleTextRequest en = request.translations().get(AppLocale.EN.code());
		if (en != null && en.name() != null && !en.name().isBlank()) {
			catalogLocalizationService.upsertCategoryTranslation(
					category,
					AppLocale.EN.code(),
					normalizeName(en.name()),
					blankToNull(en.description())
			);
		}
		catalogCacheInvalidator.onCategoryMutation(categoryId);
		return getCategoryTranslations(categoryId);
	}

	private static TranslationLocaleTextRequest requireVi(Map<String, TranslationLocaleTextRequest> translations) {
		if (translations == null || !translations.containsKey(AppLocale.VI.code())) {
			throw ApiException.keyed(HttpStatus.BAD_REQUEST, "Bad Request", "api.error.bad_request.translations_vi_required");
		}
		for (String locale : translations.keySet()) {
			assertSupportedLocale(locale);
		}
		TranslationLocaleTextRequest vi = translations.get(AppLocale.VI.code());
		if (vi == null || vi.name() == null || vi.name().isBlank()) {
			throw ApiException.keyed(HttpStatus.BAD_REQUEST, "Bad Request", "api.error.bad_request.translations_vi_required");
		}
		return vi;
	}

	private static void assertSupportedLocale(String locale) {
		String normalized = locale == null ? "" : locale.trim().toLowerCase(Locale.ROOT);
		if (AppLocale.fromLanguageCode(normalized) == null) {
			throw ApiException.keyed(HttpStatus.BAD_REQUEST, "Bad Request", "api.error.bad_request.locale");
		}
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
}
