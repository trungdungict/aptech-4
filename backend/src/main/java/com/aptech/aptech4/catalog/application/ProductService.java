package com.aptech.aptech4.catalog.application;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aptech.aptech4.catalog.api.ProductCreateRequest;
import com.aptech.aptech4.catalog.api.ProductResponse;
import com.aptech.aptech4.catalog.api.ProductStatusRequest;
import com.aptech.aptech4.catalog.api.ProductUpdateRequest;
import com.aptech.aptech4.catalog.cache.CatalogCacheInvalidator;
import com.aptech.aptech4.catalog.cache.CatalogCacheNames;
import com.aptech.aptech4.catalog.domain.Category;
import com.aptech.aptech4.catalog.domain.Inventory;
import com.aptech.aptech4.catalog.domain.Product;
import com.aptech.aptech4.catalog.persistence.CategoryRepository;
import com.aptech.aptech4.catalog.persistence.ProductRepository;
import com.aptech.aptech4.common.api.ApiException;
import com.aptech.aptech4.common.api.PageResponse;

@Service
public class ProductService {

	public static final int DEFAULT_PAGE_SIZE = 20;
	public static final int MAX_PAGE_SIZE = 100;

	private final ProductRepository productRepository;
	private final CategoryRepository categoryRepository;
	private final CatalogCacheInvalidator catalogCacheInvalidator;

	public ProductService(
			ProductRepository productRepository,
			CategoryRepository categoryRepository,
			CatalogCacheInvalidator catalogCacheInvalidator
	) {
		this.productRepository = productRepository;
		this.categoryRepository = categoryRepository;
		this.catalogCacheInvalidator = catalogCacheInvalidator;
	}

	@Transactional(readOnly = true)
	@Cacheable(
			cacheNames = CatalogCacheNames.PRODUCTS_LIST,
			key = "T(com.aptech.aptech4.catalog.cache.CatalogCacheKeys).productList(#q,#categoryId,#minPrice,#maxPrice,#inStock,#page,#size,#sort)"
	)
	public PageResponse<ProductResponse> listPublic(
			String q,
			UUID categoryId,
			BigDecimal minPrice,
			BigDecimal maxPrice,
			Boolean inStock,
			int page,
			int size,
			String sort
	) {
		int safePage = validatePage(page);
		int safeSize = validateSize(size);
		validatePriceRange(minPrice, maxPrice);
		assertPublicCategoryFilter(categoryId);

		String keyword = normalizeKeyword(q);
		Specification<Product> spec = ProductSpecifications.publicDiscovery(
				keyword, categoryId, minPrice, maxPrice, inStock
		);
		return pageProducts(spec, safePage, safeSize, sort);
	}

	@Transactional(readOnly = true)
	@Cacheable(cacheNames = CatalogCacheNames.PRODUCTS_DETAIL, key = "T(com.aptech.aptech4.catalog.cache.CatalogCacheKeys).productDetail(#id)")
	public ProductResponse getPublic(UUID id) {
		Product product = productRepository.findPublicById(id).orElseThrow(this::notFound);
		return toResponse(product);
	}

	@Transactional(readOnly = true)
	public PageResponse<ProductResponse> listAdmin(String q, UUID categoryId, Boolean active, int page, int size, String sort) {
		int safePage = validatePage(page);
		int safeSize = validateSize(size);
		assertAdminCategoryFilter(categoryId);
		String keyword = normalizeKeyword(q);
		Specification<Product> spec = ProductSpecifications.adminList(keyword, categoryId, active);
		return pageProducts(spec, safePage, safeSize, sort);
	}

	@Transactional(readOnly = true)
	public ProductResponse getAdmin(UUID id) {
		Product product = productRepository.findAdminById(id).orElseThrow(this::notFound);
		return toResponse(product);
	}

	@Transactional
	public ProductResponse create(ProductCreateRequest request) {
		String sku = normalizeSku(request.sku());
		assertSkuAvailable(sku, null);
		Category category = requireActiveCategory(request.categoryId());
		String name = normalizeName(request.name());
		String currency = normalizeCurrency(request.currency());
		Product product = Product.createNew(
				category,
				sku,
				name,
				blankToNull(request.description()),
				request.unitPrice(),
				currency,
				true
		);
		product.setImageUrl(com.aptech.aptech4.catalog.upload.ProductImagePathRules.normalizeOrNull(request.imageUrl()));
		try {
			Product saved = productRepository.saveAndFlush(product);
			ProductResponse response = toResponse(productRepository.findAdminById(saved.getId()).orElse(saved));
			catalogCacheInvalidator.onProductMutation(saved.getId());
			return response;
		}
		catch (DataIntegrityViolationException ex) {
			throw skuConflict();
		}
	}

	@Transactional
	public ProductResponse update(UUID id, ProductUpdateRequest request) {
		Product product = productRepository.findAdminById(id).orElseThrow(this::notFound);
		String sku = normalizeSku(request.sku());
		assertSkuAvailable(sku, id);
		Category category = requireActiveCategory(request.categoryId());
		product.setSku(sku);
		product.setName(normalizeName(request.name()));
		product.setDescription(blankToNull(request.description()));
		product.setUnitPrice(request.unitPrice());
		product.setCurrency(normalizeCurrency(request.currency()));
		product.setCategory(category);
		product.setImageUrl(com.aptech.aptech4.catalog.upload.ProductImagePathRules.normalizeOrNull(request.imageUrl()));
		product.touch();
		try {
			productRepository.saveAndFlush(product);
			ProductResponse response = toResponse(productRepository.findAdminById(id).orElse(product));
			catalogCacheInvalidator.onProductMutation(id);
			return response;
		}
		catch (DataIntegrityViolationException ex) {
			throw skuConflict();
		}
	}

	@Transactional
	public ProductResponse updateStatus(UUID id, ProductStatusRequest request) {
		Product product = productRepository.findAdminById(id).orElseThrow(this::notFound);
		product.setActive(Boolean.TRUE.equals(request.active()));
		product.touch();
		productRepository.save(product);
		ProductResponse response = toResponse(product);
		catalogCacheInvalidator.onProductMutation(id);
		return response;
	}

	private PageResponse<ProductResponse> pageProducts(Specification<Product> spec, int page, int size, String sort) {
		Pageable pageable = PageRequest.of(page, size, ProductSortSupport.resolve(sort));
		Page<Product> idPage = productRepository.findAll(spec, pageable);
		List<UUID> ids = idPage.getContent().stream().map(Product::getId).toList();
		if (ids.isEmpty()) {
			return PageResponse.of(List.of(), page, size, idPage.getTotalElements());
		}
		Map<UUID, Product> byId = productRepository.findAllWithCatalogByIdIn(ids).stream()
			.collect(Collectors.toMap(Product::getId, Function.identity()));
		List<ProductResponse> items = ids.stream()
			.map(id -> {
				Product product = byId.get(id);
				if (product == null) {
					throw new IllegalStateException("Product missing after page query: " + id);
				}
				return toResponse(product);
			})
			.toList();
		return PageResponse.of(items, page, size, idPage.getTotalElements());
	}

	private void assertPublicCategoryFilter(UUID categoryId) {
		if (categoryId == null) {
			return;
		}
		if (categoryRepository.findByIdAndActiveTrue(categoryId).isEmpty()) {
			throw new ApiException(HttpStatus.NOT_FOUND, "Not Found", "Category not found");
		}
	}

	private void assertAdminCategoryFilter(UUID categoryId) {
		if (categoryId == null) {
			return;
		}
		if (!categoryRepository.existsById(categoryId)) {
			throw new ApiException(HttpStatus.NOT_FOUND, "Not Found", "Category not found");
		}
	}

	private Category requireActiveCategory(UUID categoryId) {
		Category category = categoryRepository.findById(categoryId)
			.orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Bad Request", "Category not found"));
		if (!category.isActive()) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "Bad Request", "Category is inactive");
		}
		return category;
	}

	private void assertSkuAvailable(String sku, UUID excludeId) {
		boolean taken = excludeId == null
				? productRepository.existsBySkuIgnoreCase(sku)
				: productRepository.existsBySkuIgnoreCaseAndIdNot(sku, excludeId);
		if (taken) {
			throw skuConflict();
		}
	}

	private static void validatePriceRange(BigDecimal minPrice, BigDecimal maxPrice) {
		if (minPrice != null && minPrice.signum() < 0) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "Bad Request", "minPrice must be >= 0");
		}
		if (maxPrice != null && maxPrice.signum() < 0) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "Bad Request", "maxPrice must be >= 0");
		}
		if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "Bad Request", "minPrice must be <= maxPrice");
		}
	}

	private static int validatePage(int page) {
		if (page < 0) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "Bad Request", "page must be >= 0");
		}
		return page;
	}

	private static int validateSize(int size) {
		if (size < 1) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "Bad Request", "size must be >= 1");
		}
		if (size > MAX_PAGE_SIZE) {
			throw new ApiException(
					HttpStatus.BAD_REQUEST,
					"Bad Request",
					"size must be <= " + MAX_PAGE_SIZE
			);
		}
		return size;
	}

	private static String normalizeKeyword(String q) {
		if (q == null) {
			return null;
		}
		String trimmed = q.trim().replaceAll("\\s+", " ");
		return trimmed.isEmpty() ? null : trimmed;
	}

	private static String normalizeName(String name) {
		return name.trim().replaceAll("\\s+", " ");
	}

	private static String normalizeSku(String sku) {
		return sku.trim();
	}

	private static String normalizeCurrency(String currency) {
		if (currency == null || currency.isBlank()) {
			return "VND";
		}
		return currency.trim().toUpperCase(Locale.ROOT);
	}

	private static String blankToNull(String value) {
		if (value == null) {
			return null;
		}
		String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}

	private ProductResponse toResponse(Product product) {
		Inventory inventory = product.getInventory();
		Boolean inStock = null;
		if (inventory != null) {
			// available = on_hand - reserved (ADR-006)
			inStock = inventory.getAvailable() > 0;
		}
		return new ProductResponse(
				product.getId(),
				product.getSku(),
				product.getName(),
				product.getDescription(),
				product.getUnitPrice(),
				product.getCurrency(),
				product.getCategory().getId(),
				product.getCategory().getName(),
				product.isActive(),
				product.getImageUrl(),
				inStock
		);
	}

	private ApiException notFound() {
		return new ApiException(HttpStatus.NOT_FOUND, "Not Found", "Product not found");
	}

	private ApiException skuConflict() {
		return new ApiException(HttpStatus.CONFLICT, "Conflict", "Product SKU already exists");
	}
}
