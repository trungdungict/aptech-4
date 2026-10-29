package com.aptech.aptech4.seed;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.aptech.aptech4.catalog.cache.CatalogCacheInvalidator;
import com.aptech.aptech4.catalog.domain.Category;
import com.aptech.aptech4.catalog.domain.Inventory;
import com.aptech.aptech4.catalog.domain.Product;
import com.aptech.aptech4.catalog.persistence.CategoryRepository;
import com.aptech.aptech4.catalog.persistence.InventoryRepository;
import com.aptech.aptech4.catalog.persistence.ProductRepository;
import com.aptech.aptech4.identity.domain.Role;
import com.aptech.aptech4.identity.domain.RoleCode;
import com.aptech.aptech4.identity.domain.UserAccount;
import com.aptech.aptech4.identity.persistence.RoleRepository;
import com.aptech.aptech4.identity.persistence.UserAccountRepository;
import com.aptech.aptech4.identity.security.EmailNormalizer;

/**
 * Idempotent demo seed. Upserts only managed natural keys (emails / slugs / SKUs). Does not delete
 * foreign data. Does not overwrite existing user passwords (create-only for credentials).
 */
@Service
public class DemoSeedService {

	private static final Logger log = LoggerFactory.getLogger(DemoSeedService.class);

	private final DemoSeedProperties properties;
	private final Environment environment;
	private final RoleRepository roleRepository;
	private final UserAccountRepository userAccountRepository;
	private final CategoryRepository categoryRepository;
	private final ProductRepository productRepository;
	private final InventoryRepository inventoryRepository;
	private final PasswordEncoder passwordEncoder;
	private final ObjectProvider<CatalogCacheInvalidator> catalogCacheInvalidator;

	public DemoSeedService(
			DemoSeedProperties properties,
			Environment environment,
			RoleRepository roleRepository,
			UserAccountRepository userAccountRepository,
			CategoryRepository categoryRepository,
			ProductRepository productRepository,
			InventoryRepository inventoryRepository,
			PasswordEncoder passwordEncoder,
			ObjectProvider<CatalogCacheInvalidator> catalogCacheInvalidator
	) {
		this.properties = properties;
		this.environment = environment;
		this.roleRepository = roleRepository;
		this.userAccountRepository = userAccountRepository;
		this.categoryRepository = categoryRepository;
		this.productRepository = productRepository;
		this.inventoryRepository = inventoryRepository;
		this.passwordEncoder = passwordEncoder;
		this.catalogCacheInvalidator = catalogCacheInvalidator;
	}

	@Transactional
	public DemoSeedResult seedAll() {
		assertAllowedEnvironment();
		requirePassword(properties.getAdminPassword(), "DEMO_ADMIN_PASSWORD / aptech.seed.admin-password");
		requirePassword(properties.getCustomerPassword(), "DEMO_CUSTOMER_PASSWORD / aptech.seed.customer-password");

		Role customerRole = roleRepository.findByCode(RoleCode.CUSTOMER)
				.orElseThrow(() -> new IllegalStateException("CUSTOMER role missing — run Liquibase first"));
		Role adminRole = roleRepository.findByCode(RoleCode.ADMIN)
				.orElseThrow(() -> new IllegalStateException("ADMIN role missing — run Liquibase first"));

		int usersCreated = 0;
		int usersUpdated = 0;
		if (upsertUser(
				properties.getAdminEmail(),
				properties.getAdminPassword(),
				properties.getAdminFullName(),
				Set.of(adminRole)
		)) {
			usersCreated++;
		}
		else {
			usersUpdated++;
		}
		if (upsertUser(
				properties.getCustomerEmail(),
				properties.getCustomerPassword(),
				properties.getCustomerFullName(),
				Set.of(customerRole)
		)) {
			usersCreated++;
		}
		else {
			usersUpdated++;
		}

		int categoriesCreated = 0;
		int categoriesUpdated = 0;
		Map<String, Category> categoriesByKey = new HashMap<>();
		for (DemoSeedCatalog.CategoryFixture fixture : DemoSeedCatalog.CATEGORIES) {
			boolean created = upsertCategory(fixture);
			if (created) {
				categoriesCreated++;
			}
			else {
				categoriesUpdated++;
			}
			categoriesByKey.put(fixture.key(), categoryRepository.findBySlug(fixture.slug()).orElseThrow());
		}

		int productsCreated = 0;
		int productsUpdated = 0;
		int inventoryUpserts = 0;
		for (DemoSeedCatalog.ProductFixture fixture : DemoSeedCatalog.PRODUCTS) {
			Category category = categoriesByKey.get(fixture.categoryKey());
			if (category == null) {
				throw new IllegalStateException("Unknown category key: " + fixture.categoryKey());
			}
			boolean created = upsertProduct(fixture, category);
			if (created) {
				productsCreated++;
			}
			else {
				productsUpdated++;
			}
			upsertInventory(fixture);
			inventoryUpserts++;
		}

		CatalogCacheInvalidator invalidator = catalogCacheInvalidator.getIfAvailable();
		if (invalidator != null) {
			invalidator.onInventoryMutation();
			invalidator.onCategoryMutation(null);
		}

		DemoSeedResult result = new DemoSeedResult(
				usersCreated,
				usersUpdated,
				categoriesCreated,
				categoriesUpdated,
				productsCreated,
				productsUpdated,
				inventoryUpserts
		);
		log.info(
				"Demo seed complete: users +{}/~{} categories +{}/~{} products +{}/~{} inventory {}",
				result.usersCreated(),
				result.usersUpdated(),
				result.categoriesCreated(),
				result.categoriesUpdated(),
				result.productsCreated(),
				result.productsUpdated(),
				result.inventoryUpserts()
		);
		return result;
	}

	private void assertAllowedEnvironment() {
		DemoSeedGuard.assertAllowed(environment, properties.isAllowOnProd());
	}

	private static void requirePassword(String password, String label) {
		if (!StringUtils.hasText(password) || password.length() < 8) {
			throw new IllegalStateException(label + " must be set (≥8 chars) for seeding");
		}
	}

	/**
	 * @return true if created, false if updated existing
	 */
	private boolean upsertUser(
			String rawEmail,
			String rawPassword,
			String fullName,
			Set<Role> roles
	) {
		String email = EmailNormalizer.normalize(rawEmail);
		var existing = userAccountRepository.findByEmailWithRoles(email);
		if (existing.isPresent()) {
			UserAccount user = existing.get();
			user.setFullName(fullName);
			user.setEnabled(true);
			user.getRoles().clear();
			user.getRoles().addAll(new HashSet<>(roles));
			user.setUpdatedAt(Instant.now());
			userAccountRepository.save(user);
			// Do not overwrite password_hash on re-seed (P08-T02 reset may restore credentials).
			return false;
		}
		UserAccount created = UserAccount.createNew(email, passwordEncoder.encode(rawPassword), fullName);
		created.getRoles().addAll(roles);
		userAccountRepository.save(created);
		return true;
	}

	private boolean upsertCategory(DemoSeedCatalog.CategoryFixture fixture) {
		var existing = categoryRepository.findBySlug(fixture.slug());
		if (existing.isPresent()) {
			Category category = existing.get();
			category.setName(fixture.name());
			category.setDescription(fixture.description());
			category.setActive(fixture.active());
			category.touch();
			categoryRepository.save(category);
			return false;
		}
		Category created = Category.createNew(fixture.name(), fixture.slug(), fixture.description(), null);
		created.setActive(fixture.active());
		categoryRepository.save(created);
		return true;
	}

	private boolean upsertProduct(DemoSeedCatalog.ProductFixture fixture, Category category) {
		var existing = productRepository.findBySkuIgnoreCase(fixture.sku());
		if (existing.isPresent()) {
			Product product = existing.get();
			product.setCategory(category);
			product.setName(fixture.name());
			product.setDescription(fixture.description());
			product.setUnitPrice(fixture.unitPrice());
			product.setCurrency("VND");
			product.setActive(fixture.active());
			product.touch();
			productRepository.save(product);
			return false;
		}
		Product created = Product.createNew(
				category,
				fixture.sku(),
				fixture.name(),
				fixture.description(),
				fixture.unitPrice(),
				"VND",
				fixture.active()
		);
		productRepository.save(created);
		return true;
	}

	private void upsertInventory(DemoSeedCatalog.ProductFixture fixture) {
		Product product = productRepository.findBySkuIgnoreCase(fixture.sku()).orElseThrow();
		UUID productId = product.getId();
		Inventory inventory = inventoryRepository.findById(productId).orElse(null);
		int targetOnHand = Math.max(0, fixture.onHand());
		if (inventory == null) {
			Inventory created = Inventory.forProduct(product, targetOnHand);
			inventoryRepository.save(created);
			return;
		}
		int reserved = inventory.getQuantityReserved();
		// Honor fixture on-hand (incl. explicit 0 for sold-out demos). Clamp reserved so
		// available = on_hand - reserved stays non-negative and OOS fixtures stay unavailable.
		if (reserved > targetOnHand) {
			inventory.setQuantityReserved(targetOnHand);
		}
		inventory.setQuantityOnHand(targetOnHand);
		inventory.setUpdatedAt(Instant.now());
		inventoryRepository.save(inventory);
	}
}
