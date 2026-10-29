package com.aptech.aptech4.cart.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aptech.aptech4.cart.api.AddCartItemRequest;
import com.aptech.aptech4.cart.api.CartConstraints;
import com.aptech.aptech4.cart.api.CartItemResponse;
import com.aptech.aptech4.cart.api.CartResponse;
import com.aptech.aptech4.cart.api.UpdateCartItemRequest;
import com.aptech.aptech4.cart.domain.Cart;
import com.aptech.aptech4.cart.domain.CartItem;
import com.aptech.aptech4.cart.persistence.CartItemRepository;
import com.aptech.aptech4.cart.persistence.CartRepository;
import com.aptech.aptech4.catalog.domain.Category;
import com.aptech.aptech4.catalog.domain.Product;
import com.aptech.aptech4.catalog.persistence.ProductRepository;
import com.aptech.aptech4.common.api.ApiException;
import com.aptech.aptech4.identity.domain.UserAccount;
import com.aptech.aptech4.identity.persistence.UserAccountRepository;
import com.aptech.aptech4.identity.security.CurrentUser;

@Service
public class CartService {

	private final CartRepository cartRepository;
	private final CartItemRepository cartItemRepository;
	private final ProductRepository productRepository;
	private final UserAccountRepository userAccountRepository;

	public CartService(
			CartRepository cartRepository,
			CartItemRepository cartItemRepository,
			ProductRepository productRepository,
			UserAccountRepository userAccountRepository
	) {
		this.cartRepository = cartRepository;
		this.cartItemRepository = cartItemRepository;
		this.productRepository = productRepository;
		this.userAccountRepository = userAccountRepository;
	}

	@Transactional
	public CartResponse getMyCart() {
		UUID userId = CurrentUser.requireUserId();
		ensureCart(userId);
		return loadCartResponse(userId);
	}

	@Transactional
	public CartResponse addItem(AddCartItemRequest request) {
		UUID userId = CurrentUser.requireUserId();
		Cart cart = lockOrCreateCart(userId);
		Product product = productRepository.findPurchasableById(request.productId())
			.orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Bad Request", "Product not available"));

		int addQty = request.quantity();
		CartItem existing = cartItemRepository.findByCartIdAndProductId(cart.getId(), product.getId()).orElse(null);
		if (existing != null) {
			int merged = existing.getQuantity() + addQty;
			if (merged > CartConstraints.MAX_QUANTITY) {
				throw new ApiException(
						HttpStatus.BAD_REQUEST,
						"Bad Request",
						"Quantity must be <= " + CartConstraints.MAX_QUANTITY
				);
			}
			existing.setQuantity(merged);
			existing.touch();
			cartItemRepository.save(existing);
		}
		else {
			cart.addItem(CartItem.createNew(cart, product, addQty));
		}
		cart.touch();
		try {
			cartRepository.saveAndFlush(cart);
		}
		catch (DataIntegrityViolationException ex) {
			throw new ApiException(HttpStatus.CONFLICT, "Conflict", "Unable to update cart item");
		}
		return loadCartResponse(userId);
	}

	@Transactional
	public CartResponse updateItem(UUID itemId, UpdateCartItemRequest request) {
		UUID userId = CurrentUser.requireUserId();
		lockOrCreateCart(userId);
		CartItem item = cartItemRepository.findByIdAndCartUserId(itemId, userId)
			.orElseThrow(this::itemNotFound);

		if (!isPurchasable(item.getProduct())) {
			throw new ApiException(
					HttpStatus.BAD_REQUEST,
					"Bad Request",
					"Product is no longer available; remove the item or wait until it is active again"
			);
		}

		item.setQuantity(request.quantity());
		item.touch();
		item.getCart().touch();
		cartItemRepository.saveAndFlush(item);
		return loadCartResponse(userId);
	}

	@Transactional
	public CartResponse removeItem(UUID itemId) {
		UUID userId = CurrentUser.requireUserId();
		Cart cart = lockOrCreateCart(userId);
		CartItem item = cartItemRepository.findByIdAndCartUserId(itemId, userId)
			.orElseThrow(this::itemNotFound);
		cartItemRepository.delete(item);
		cart.touch();
		cartRepository.saveAndFlush(cart);
		return loadCartResponse(userId);
	}

	@Transactional
	public CartResponse clearItems() {
		UUID userId = CurrentUser.requireUserId();
		Cart cart = lockOrCreateCart(userId);
		Cart managed = cartRepository.findByUserIdWithItems(userId).orElse(cart);
		managed.getItems().clear();
		managed.touch();
		cartRepository.saveAndFlush(managed);
		return loadCartResponse(userId);
	}

	private CartResponse loadCartResponse(UUID userId) {
		Cart cart = cartRepository.findByUserIdWithItems(userId)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Not Found", "Cart not found"));
		return toResponse(cart);
	}

	private Cart lockOrCreateCart(UUID userId) {
		return cartRepository.findByUserIdForUpdate(userId).orElseGet(() -> ensureCart(userId));
	}

	private Cart ensureCart(UUID userId) {
		return cartRepository.findByUserIdForUpdate(userId).orElseGet(() -> {
			if (!userAccountRepository.existsById(userId)) {
				throw new ApiException(HttpStatus.UNAUTHORIZED, "Unauthorized", "User not found");
			}
			UserAccount user = userAccountRepository.getReferenceById(userId);
			try {
				return cartRepository.saveAndFlush(Cart.createFor(user));
			}
			catch (DataIntegrityViolationException ex) {
				return cartRepository.findByUserIdForUpdate(userId)
					.orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "Conflict", "Unable to create cart"));
			}
		});
	}

	private CartResponse toResponse(Cart cart) {
		List<CartItemResponse> items = cart.getItems().stream()
			.sorted(Comparator.comparing(CartItem::getAddedAt).thenComparing(CartItem::getId))
			.map(this::toItemResponse)
			.toList();

		BigDecimal subtotal = BigDecimal.ZERO.setScale(2, RoundingMode.UNNECESSARY);
		String currency = "VND";
		boolean hasUnavailable = false;
		for (CartItemResponse item : items) {
			subtotal = subtotal.add(item.lineSubtotal());
			currency = item.currency();
			if (!item.available()) {
				hasUnavailable = true;
			}
		}
		return new CartResponse(cart.getId(), items, subtotal, currency, hasUnavailable);
	}

	private CartItemResponse toItemResponse(CartItem item) {
		Product product = item.getProduct();
		boolean available = isPurchasable(product);
		BigDecimal unitPrice = product.getUnitPrice();
		BigDecimal lineSubtotal = unitPrice.multiply(BigDecimal.valueOf(item.getQuantity()))
			.setScale(2, RoundingMode.HALF_UP);
		return new CartItemResponse(
				item.getId(),
				product.getId(),
				product.getName(),
				null,
				item.getQuantity(),
				unitPrice,
				lineSubtotal,
				product.getCurrency(),
				available,
				product.isActive()
		);
	}

	private static boolean isPurchasable(Product product) {
		if (product == null || !product.isActive()) {
			return false;
		}
		Category category = product.getCategory();
		return category != null && category.isActive();
	}

	private ApiException itemNotFound() {
		return new ApiException(HttpStatus.NOT_FOUND, "Not Found", "Cart item not found");
	}
}
