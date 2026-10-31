package com.aptech.aptech4.catalog.application;

import java.time.Instant;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aptech.aptech4.catalog.api.AdminProductInventoryResponse;
import com.aptech.aptech4.catalog.api.AdminProductInventoryUpdateRequest;
import com.aptech.aptech4.catalog.cache.CatalogCacheInvalidator;
import com.aptech.aptech4.catalog.domain.Inventory;
import com.aptech.aptech4.catalog.domain.Product;
import com.aptech.aptech4.catalog.persistence.InventoryRepository;
import com.aptech.aptech4.catalog.persistence.ProductRepository;
import com.aptech.aptech4.common.api.ApiException;

/**
 * Admin absolute stock updates against the existing per-product {@code inventory} row (ADR-006).
 * Does not touch reservation/confirm paths used by checkout.
 */
@Service
public class AdminInventoryService {

	private final ProductRepository productRepository;
	private final InventoryRepository inventoryRepository;
	private final CatalogCacheInvalidator catalogCacheInvalidator;

	public AdminInventoryService(
			ProductRepository productRepository,
			InventoryRepository inventoryRepository,
			CatalogCacheInvalidator catalogCacheInvalidator
	) {
		this.productRepository = productRepository;
		this.inventoryRepository = inventoryRepository;
		this.catalogCacheInvalidator = catalogCacheInvalidator;
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
}
