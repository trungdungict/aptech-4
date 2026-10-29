package com.aptech.aptech4.catalog.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.aptech.aptech4.catalog.api.ReserveStockRequest;
import com.aptech.aptech4.catalog.api.StockReservationResponse;
import com.aptech.aptech4.catalog.cache.CatalogCacheInvalidator;
import com.aptech.aptech4.catalog.config.InventoryProperties;
import com.aptech.aptech4.catalog.domain.Product;
import com.aptech.aptech4.catalog.domain.ReservationStatus;
import com.aptech.aptech4.catalog.domain.StockReservation;
import com.aptech.aptech4.catalog.domain.StockReservationItem;
import com.aptech.aptech4.catalog.persistence.InventoryRepository;
import com.aptech.aptech4.catalog.persistence.ProductRepository;
import com.aptech.aptech4.catalog.persistence.StockReservationRepository;
import com.aptech.aptech4.common.api.ApiException;
import com.aptech.aptech4.identity.domain.UserAccount;
import com.aptech.aptech4.identity.persistence.UserAccountRepository;
import com.aptech.aptech4.identity.security.CurrentUser;

/**
 * Soft-hold inventory for checkout (ADR-006). Cart never calls this.
 * P06-T03 should call {@link #confirm(UUID, UUID)} after successful mock payment.
 */
@Service
public class InventoryReservationService {

	private static final Logger log = LoggerFactory.getLogger(InventoryReservationService.class);
	private static final int MAX_DEADLOCK_RETRIES = 3;

	private final StockReservationRepository reservationRepository;
	private final InventoryRepository inventoryRepository;
	private final ProductRepository productRepository;
	private final UserAccountRepository userAccountRepository;
	private final InventoryProperties inventoryProperties;
	private final TransactionTemplate transactionTemplate;
	private final CatalogCacheInvalidator catalogCacheInvalidator;

	public InventoryReservationService(
			StockReservationRepository reservationRepository,
			InventoryRepository inventoryRepository,
			ProductRepository productRepository,
			UserAccountRepository userAccountRepository,
			InventoryProperties inventoryProperties,
			TransactionTemplate transactionTemplate,
			CatalogCacheInvalidator catalogCacheInvalidator
	) {
		this.reservationRepository = reservationRepository;
		this.inventoryRepository = inventoryRepository;
		this.productRepository = productRepository;
		this.userAccountRepository = userAccountRepository;
		this.inventoryProperties = inventoryProperties;
		this.transactionTemplate = transactionTemplate;
		this.catalogCacheInvalidator = catalogCacheInvalidator;
	}

	@Transactional
	public StockReservationResponse getOwned(UUID reservationId) {
		UUID userId = CurrentUser.requireUserId();
		StockReservation reservation = reservationRepository.findByIdWithItems(reservationId)
			.orElseThrow(this::notFound);
		assertOwner(reservation, userId);
		if (reservation.getStatus() == ReservationStatus.ACTIVE && reservation.isExpired(Instant.now())) {
			return releaseInternal(reservationId, userId);
		}
		return toResponse(reservation);
	}

	public StockReservationResponse reserve(ReserveStockRequest request) {
		UUID userId = CurrentUser.requireUserId();
		String key = request.idempotencyKey().trim();
		Map<UUID, Integer> merged = mergeLines(request);

		// When already inside a caller TX (checkout), participate directly — a nested
		// TransactionTemplate would mark the outer TX rollback-only on failures/races.
		if (TransactionSynchronizationManager.isActualTransactionActive()) {
			try {
				StockReservationResponse response = doReserve(userId, key, merged);
				catalogCacheInvalidator.onInventoryMutation();
				return response;
			}
			catch (IdempotencyRaceException ex) {
				return reservationRepository.findByUserIdAndIdempotencyKey(userId, key)
					.map(r -> toResponse(loadWithItems(r.getId())))
					.orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "Conflict", "Reservation conflict"));
			}
		}

		for (int attempt = 1; attempt <= MAX_DEADLOCK_RETRIES; attempt++) {
			try {
				StockReservationResponse response = transactionTemplate.execute(status -> doReserve(userId, key, merged));
				if (response == null) {
					throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Error", "Reservation failed");
				}
				catalogCacheInvalidator.onInventoryMutation();
				return response;
			}
			catch (IdempotencyRaceException ex) {
				return lookupByIdempotency(userId, key);
			}
			catch (PessimisticLockingFailureException ex) {
				if (attempt == MAX_DEADLOCK_RETRIES) {
					throw new ApiException(HttpStatus.CONFLICT, "Conflict", "Could not acquire inventory lock; retry");
				}
				log.debug("Inventory reserve deadlock/retry attempt {}", attempt);
			}
		}
		throw new ApiException(HttpStatus.CONFLICT, "Conflict", "Could not acquire inventory lock; retry");
	}

	@Transactional
	public StockReservationResponse releaseOwned(UUID reservationId) {
		UUID userId = CurrentUser.requireUserId();
		StockReservation reservation = reservationRepository.findByIdForUpdate(reservationId)
			.orElseThrow(this::notFound);
		assertOwner(reservation, userId);
		return releaseInternal(reservationId, userId);
	}

	/**
	 * Release for checkout abort / system expiry. Idempotent.
	 */
	@Transactional
	public StockReservationResponse release(UUID reservationId, UUID actingUserId) {
		StockReservation reservation = reservationRepository.findByIdForUpdate(reservationId)
			.orElseThrow(this::notFound);
		assertOwner(reservation, actingUserId);
		return releaseInternal(reservationId, actingUserId);
	}

	/**
	 * Confirm sale after payment — used by P06-T03 checkout. Decrements on_hand and reserved once.
	 */
	@Transactional
	public StockReservationResponse confirm(UUID reservationId, UUID actingUserId) {
		StockReservation reservation = reservationRepository.findByIdForUpdate(reservationId)
			.orElseThrow(this::notFound);
		assertOwner(reservation, actingUserId);

		if (reservation.getStatus() == ReservationStatus.CONFIRMED) {
			return toResponse(loadWithItems(reservationId));
		}
		if (reservation.getStatus() == ReservationStatus.RELEASED) {
			throw new ApiException(HttpStatus.CONFLICT, "Conflict", "Reservation already released");
		}
		if (reservation.isExpired(Instant.now())) {
			releaseInternal(reservationId, actingUserId);
			throw new ApiException(HttpStatus.CONFLICT, "Conflict", "Reservation expired");
		}

		Instant now = Instant.now();
		StockReservation withItems = loadWithItems(reservationId);
		List<StockReservationItem> lines = withItems.getItems().stream()
			.sorted(Comparator.comparing(i -> i.getProduct().getId()))
			.toList();
		for (StockReservationItem line : lines) {
			int updated = inventoryRepository.tryConfirmSale(line.getProduct().getId(), line.getQuantity(), now);
			if (updated != 1) {
				throw new ApiException(HttpStatus.CONFLICT, "Conflict", "Unable to confirm inventory for product");
			}
		}
		reservation.setStatus(ReservationStatus.CONFIRMED);
		reservation.touch();
		reservationRepository.save(reservation);
		StockReservationResponse response = toResponse(loadWithItems(reservationId));
		catalogCacheInvalidator.onInventoryMutation();
		return response;
	}

	@Transactional
	public int expireDueReservations() {
		Instant now = Instant.now();
		List<UUID> ids = reservationRepository.findExpiredActiveIds(ReservationStatus.ACTIVE, now);
		int released = 0;
		for (UUID id : ids) {
			StockReservation reservation = reservationRepository.findByIdForUpdate(id).orElse(null);
			if (reservation == null || reservation.getStatus() != ReservationStatus.ACTIVE) {
				continue;
			}
			if (!reservation.isExpired(now)) {
				continue;
			}
			releaseStockFor(loadWithItems(id));
			reservation.setStatus(ReservationStatus.RELEASED);
			reservation.touch();
			reservationRepository.save(reservation);
			released++;
		}
		if (released > 0) {
			catalogCacheInvalidator.onInventoryMutation();
		}
		return released;
	}

	private StockReservationResponse doReserve(UUID userId, String idempotencyKey, Map<UUID, Integer> merged) {
		expireDueReservations();

		var existing = reservationRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey);
		if (existing.isPresent()) {
			StockReservation prior = existing.get();
			if (prior.getStatus() == ReservationStatus.ACTIVE && prior.isExpired(Instant.now())) {
				releaseInternal(prior.getId(), userId);
			}
			return toResponse(loadWithItems(prior.getId()));
		}

		if (!userAccountRepository.existsById(userId)) {
			throw new ApiException(HttpStatus.UNAUTHORIZED, "Unauthorized", "User not found");
		}
		UserAccount user = userAccountRepository.getReferenceById(userId);

		List<UUID> productIds = new ArrayList<>(merged.keySet());
		productIds.sort(Comparator.naturalOrder());

		List<Product> products = new ArrayList<>();
		for (UUID productId : productIds) {
			Product product = productRepository.findPurchasableById(productId)
				.orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Bad Request", "Product not available"));
			products.add(product);
		}

		Instant now = Instant.now();
		for (Product product : products) {
			int qty = merged.get(product.getId());
			int updated = inventoryRepository.tryReserve(product.getId(), qty, now);
			if (updated != 1) {
				throw new ApiException(HttpStatus.CONFLICT, "Conflict", "Insufficient stock");
			}
		}

		Instant expiresAt = now.plusSeconds(Math.max(1, inventoryProperties.getReservationTtlSeconds()));
		StockReservation reservation = StockReservation.createActive(user, idempotencyKey, expiresAt);
		for (Product product : products) {
			reservation.addItem(StockReservationItem.of(reservation, product, merged.get(product.getId())));
		}
		try {
			StockReservation saved = reservationRepository.saveAndFlush(reservation);
			// Use the managed graph — re join-fetching items/products in the same PC
			// (e.g. checkout already loaded Product) triggers Hibernate entityHolder NPEs.
			return toResponse(saved);
		}
		catch (DataIntegrityViolationException ex) {
			// Mark TX rollback; outer reserve() reloads winner by idempotency key.
			throw new IdempotencyRaceException(ex);
		}
	}

	private StockReservationResponse lookupByIdempotency(UUID userId, String key) {
		return transactionTemplate.execute(status -> reservationRepository.findByUserIdAndIdempotencyKey(userId, key)
			.map(r -> toResponse(loadWithItems(r.getId())))
			.orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "Conflict", "Reservation conflict")));
	}

	private StockReservationResponse releaseInternal(UUID reservationId, UUID userId) {
		StockReservation reservation = reservationRepository.findByIdForUpdate(reservationId)
			.orElseThrow(this::notFound);
		assertOwner(reservation, userId);
		if (reservation.getStatus() == ReservationStatus.RELEASED
				|| reservation.getStatus() == ReservationStatus.CONFIRMED) {
			return toResponse(loadWithItems(reservationId));
		}
		releaseStockFor(loadWithItems(reservationId));
		reservation.setStatus(ReservationStatus.RELEASED);
		reservation.touch();
		reservationRepository.save(reservation);
		StockReservationResponse response = toResponse(loadWithItems(reservationId));
		catalogCacheInvalidator.onInventoryMutation();
		return response;
	}

	private void releaseStockFor(StockReservation withItems) {
		Instant now = Instant.now();
		List<StockReservationItem> lines = withItems.getItems().stream()
			.sorted(Comparator.comparing(i -> i.getProduct().getId()))
			.toList();
		for (StockReservationItem line : lines) {
			int updated = inventoryRepository.tryReleaseReserved(line.getProduct().getId(), line.getQuantity(), now);
			if (updated != 1) {
				throw new ApiException(HttpStatus.CONFLICT, "Conflict", "Unable to release inventory for product");
			}
		}
	}

	private StockReservation loadWithItems(UUID id) {
		StockReservation reservation = reservationRepository.findById(id).orElseThrow(this::notFound);
		// Lazy-init collections/associations instead of join-fetch; safer when the same
		// Product/User rows are already managed in the caller's persistence context.
		reservation.getUser().getId();
		for (var item : reservation.getItems()) {
			item.getProduct().getId();
		}
		return reservation;
	}

	private static Map<UUID, Integer> mergeLines(ReserveStockRequest request) {
		Map<UUID, Integer> merged = new LinkedHashMap<>();
		for (ReserveStockRequest.ReserveStockLineRequest line : request.items()) {
			merged.merge(line.productId(), line.quantity(), Integer::sum);
		}
		for (Map.Entry<UUID, Integer> entry : merged.entrySet()) {
			if (entry.getValue() > 99) {
				throw new ApiException(HttpStatus.BAD_REQUEST, "Bad Request", "Quantity must be <= 99");
			}
		}
		return merged;
	}

	private static void assertOwner(StockReservation reservation, UUID userId) {
		if (!reservation.getUser().getId().equals(userId)) {
			throw new ApiException(HttpStatus.NOT_FOUND, "Not Found", "Reservation not found");
		}
	}

	private StockReservationResponse toResponse(StockReservation reservation) {
		List<StockReservationResponse.StockReservationItemResponse> items = reservation.getItems().stream()
			.sorted(Comparator.comparing(i -> i.getProduct().getId()))
			.map(i -> new StockReservationResponse.StockReservationItemResponse(
					i.getProduct().getId(),
					i.getQuantity()
			))
			.toList();
		return new StockReservationResponse(
				reservation.getId(),
				reservation.getUser().getId(),
				reservation.getIdempotencyKey(),
				reservation.getStatus().name(),
				reservation.getExpiresAt(),
				items
		);
	}

	private ApiException notFound() {
		return new ApiException(HttpStatus.NOT_FOUND, "Not Found", "Reservation not found");
	}

	static final class IdempotencyRaceException extends RuntimeException {
		IdempotencyRaceException(Throwable cause) {
			super(cause);
		}
	}
}
