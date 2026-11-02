package com.aptech.aptech4.catalog.application;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aptech.aptech4.catalog.api.AdminInventoryListItemResponse;
import com.aptech.aptech4.catalog.api.AdminInventoryStockStatus;
import com.aptech.aptech4.catalog.api.AdminInventorySummaryResponse;
import com.aptech.aptech4.catalog.api.AdminProductInventoryResponse;
import com.aptech.aptech4.catalog.api.AdminProductInventoryUpdateRequest;
import com.aptech.aptech4.catalog.cache.CatalogCacheInvalidator;
import com.aptech.aptech4.catalog.config.InventoryProperties;
import com.aptech.aptech4.catalog.domain.Inventory;
import com.aptech.aptech4.catalog.domain.Product;
import com.aptech.aptech4.catalog.persistence.InventoryRepository;
import com.aptech.aptech4.catalog.persistence.ProductRepository;
import com.aptech.aptech4.common.api.ApiException;
import com.aptech.aptech4.common.api.PageResponse;

/**
 * Admin absolute stock updates and inventory list/summary reads (ADR-006).
 * Does not touch reservation/confirm paths used by checkout.
 */
@Service
public class AdminInventoryService {

	public static final int DEFAULT_PAGE_SIZE = ProductService.DEFAULT_PAGE_SIZE;
	public static final int MAX_PAGE_SIZE = ProductService.MAX_PAGE_SIZE;

	public static final String AVAILABLE_DEFINITION =
			"available = coalesce(quantity_on_hand, 0) - coalesce(quantity_reserved, 0); "
					+ "missing inventory row ⇒ available 0; "
					+ "OUT = available <= 0; LOW = 0 < available <= threshold; IN_STOCK = available > threshold";

	private final ProductRepository productRepository;
	private final InventoryRepository inventoryRepository;
	private final CatalogCacheInvalidator catalogCacheInvalidator;
	private final InventoryProperties inventoryProperties;

	public AdminInventoryService(
			ProductRepository productRepository,
			InventoryRepository inventoryRepository,
			CatalogCacheInvalidator catalogCacheInvalidator,
			InventoryProperties inventoryProperties
	) {
		this.productRepository = productRepository;
		this.inventoryRepository = inventoryRepository;
		this.catalogCacheInvalidator = catalogCacheInvalidator;
		this.inventoryProperties = inventoryProperties;
	}

	@Transactional(readOnly = true)
	public AdminProductInventoryResponse get(UUID productId) {
		requireProduct(productId);
		return inventoryRepository.findById(productId)
				.map(this::toResponse)
				.orElseGet(() -> emptyResponse(productId));
	}

	@Transactional
	public AdminProductInventoryResponse setOnHand(UUID productId, AdminProductInventoryUpdateRequest request) {
		int onHand = request.quantityOnHand();
		Product product = requireProduct(productId);

		Inventory inventory = inventoryRepository.findByProductIdForUpdate(productId).orElse(null);
		if (inventory == null) {
			inventory = Inventory.forProduct(product, onHand);
			inventoryRepository.saveAndFlush(inventory);
		}
		else {
			if (onHand < inventory.getQuantityReserved()) {
				throw new ApiException(
						HttpStatus.BAD_REQUEST,
						"Bad Request",
						"quantityOnHand (" + onHand + ") cannot be less than quantityReserved ("
								+ inventory.getQuantityReserved() + ")"
				);
			}
			inventory.setQuantityOnHand(onHand);
			inventory.setUpdatedAt(Instant.now());
			inventoryRepository.saveAndFlush(inventory);
		}

		catalogCacheInvalidator.onInventoryMutation();
		return toResponse(inventoryRepository.findById(productId).orElse(inventory));
	}

	@Transactional(readOnly = true)
	public PageResponse<AdminInventoryListItemResponse> list(
			String q,
			Boolean active,
			AdminInventoryStockStatus stockStatus,
			int page,
			int size,
			String sort
	) {
		int safePage = validatePage(page);
		int safeSize = validateSize(size);
		int threshold = inventoryProperties.getLowStockThreshold();
		String keyword = normalizeKeyword(q);
		AdminInventoryStockStatus status = stockStatus == null ? AdminInventoryStockStatus.ALL : stockStatus;

		Specification<Product> spec = ProductSpecifications.adminInventoryList(
				keyword, active, status, threshold
		);
		Pageable pageable = PageRequest.of(safePage, safeSize, InventorySortSupport.resolve(sort));
		Page<Product> idPage = productRepository.findAll(spec, pageable);
		List<UUID> ids = idPage.getContent().stream().map(Product::getId).toList();
		if (ids.isEmpty()) {
			return PageResponse.of(List.of(), safePage, safeSize, idPage.getTotalElements());
		}

		Map<UUID, Product> byId = productRepository.findAllWithCatalogByIdIn(ids).stream()
				.collect(Collectors.toMap(Product::getId, Function.identity()));
		List<AdminInventoryListItemResponse> items = ids.stream()
				.map(id -> {
					Product product = byId.get(id);
					if (product == null) {
						throw new IllegalStateException("Product missing after inventory page query: " + id);
					}
					return toListItem(product);
				})
				.toList();
		return PageResponse.of(items, safePage, safeSize, idPage.getTotalElements());
	}

	@Transactional(readOnly = true)
	public AdminInventorySummaryResponse summary() {
		int threshold = inventoryProperties.getLowStockThreshold();
		long totalProducts = productRepository.count();
		long inStockCount = productRepository.count(
				ProductSpecifications.adminInventoryList(null, null, AdminInventoryStockStatus.IN_STOCK, threshold)
		);
		long lowStockCount = productRepository.count(
				ProductSpecifications.adminInventoryList(null, null, AdminInventoryStockStatus.LOW, threshold)
		);
		long outOfStockCount = productRepository.count(
				ProductSpecifications.adminInventoryList(null, null, AdminInventoryStockStatus.OUT, threshold)
		);
		return new AdminInventorySummaryResponse(
				totalProducts,
				inStockCount,
				lowStockCount,
				outOfStockCount,
				threshold,
				AVAILABLE_DEFINITION
		);
	}

	private AdminInventoryListItemResponse toListItem(Product product) {
		Inventory inventory = product.getInventory();
		if (inventory == null) {
			return new AdminInventoryListItemResponse(
					product.getId(),
					product.getSku(),
					product.getName(),
					product.isActive(),
					product.getCategory().getId(),
					product.getCategory().getName(),
					false,
					0,
					0,
					0,
					null
			);
		}
		return new AdminInventoryListItemResponse(
				product.getId(),
				product.getSku(),
				product.getName(),
				product.isActive(),
				product.getCategory().getId(),
				product.getCategory().getName(),
				true,
				inventory.getQuantityOnHand(),
				inventory.getQuantityReserved(),
				inventory.getAvailable(),
				inventory.getUpdatedAt()
		);
	}

	private Product requireProduct(UUID productId) {
		return productRepository.findById(productId).orElseThrow(() ->
				new ApiException(HttpStatus.NOT_FOUND, "Not Found", "Product not found")
		);
	}

	private AdminProductInventoryResponse toResponse(Inventory inventory) {
		return new AdminProductInventoryResponse(
				inventory.getProductId(),
				inventory.getQuantityOnHand(),
				inventory.getQuantityReserved(),
				inventory.getAvailable(),
				inventory.getUpdatedAt()
		);
	}

	private AdminProductInventoryResponse emptyResponse(UUID productId) {
		return new AdminProductInventoryResponse(productId, 0, 0, 0, null);
	}

	private static String normalizeKeyword(String q) {
		if (q == null || q.isBlank()) {
			return null;
		}
		return q.trim();
	}

	private static int validatePage(int page) {
		if (page < 0) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "Bad Request", "page must be >= 0");
		}
		return page;
	}

	private static int validateSize(int size) {
		if (size < 1 || size > MAX_PAGE_SIZE) {
			throw new ApiException(
					HttpStatus.BAD_REQUEST,
					"Bad Request",
					"size must be between 1 and " + MAX_PAGE_SIZE
			);
		}
		return size;
	}
}
