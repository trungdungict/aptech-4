package com.aptech.aptech4.promotion.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

import com.aptech.aptech4.catalog.domain.Category;
import com.aptech.aptech4.catalog.domain.Product;
import com.aptech.aptech4.catalog.persistence.CategoryRepository;
import com.aptech.aptech4.catalog.persistence.ProductRepository;
import com.aptech.aptech4.identity.domain.Address;
import com.aptech.aptech4.identity.domain.UserAccount;
import com.aptech.aptech4.identity.persistence.AddressRepository;
import com.aptech.aptech4.identity.persistence.UserAccountRepository;
import com.aptech.aptech4.order.domain.OrderPromotionSnapshot;
import com.aptech.aptech4.order.domain.OrderStatus;
import com.aptech.aptech4.order.domain.ShopOrder;
import com.aptech.aptech4.order.persistence.OrderPromotionSnapshotRepository;
import com.aptech.aptech4.order.persistence.ShopOrderRepository;
import com.aptech.aptech4.promotion.domain.PromotionCampaign;
import com.aptech.aptech4.promotion.domain.PromotionCampaignStatus;
import com.aptech.aptech4.promotion.domain.PromotionCtaType;
import com.aptech.aptech4.promotion.domain.PromotionPlacement;
import com.aptech.aptech4.promotion.domain.PromotionPlacementKey;
import com.aptech.aptech4.promotion.domain.PromotionStrategy;
import com.aptech.aptech4.promotion.domain.PromotionStrategyType;
import com.aptech.aptech4.promotion.persistence.PromotionCampaignRepository;
import com.aptech.aptech4.promotion.persistence.PromotionPlacementRepository;
import com.aptech.aptech4.promotion.persistence.PromotionStrategyRepository;

@SpringBootTest
class PromotionDomainIntegrationTest {

	@DynamicPropertySource
	static void registerProperties(DynamicPropertyRegistry registry) {
		String pgHost = env("IT_POSTGRES_HOST", "localhost");
		String pgPort = env("IT_POSTGRES_PORT", "5432");
		String pgDb = env("IT_POSTGRES_DB", "aptech4_catalog_it");
		String pgUser = env("IT_POSTGRES_USER", "aptech");
		String pgPassword = env("IT_POSTGRES_PASSWORD", "aptech_dev_change_me");
		String redisHost = env("IT_REDIS_HOST", "localhost");
		String redisPort = env("IT_REDIS_PORT", "6379");

		registry.add("spring.datasource.url", () -> "jdbc:postgresql://" + pgHost + ":" + pgPort + "/" + pgDb);
		registry.add("spring.datasource.username", () -> pgUser);
		registry.add("spring.datasource.password", () -> pgPassword);
		registry.add("spring.data.redis.host", () -> redisHost);
		registry.add("spring.data.redis.port", () -> redisPort);
		registry.add("aptech.security.jwt.access-secret", () -> "access-secret-value-32chars-minimum!!");
		registry.add("aptech.security.jwt.refresh-secret", () -> "refresh-secret-value-32chars-minimum!");
		registry.add("aptech.security.jwt.issuer", () -> "aptech4-api");
		registry.add("aptech.security.cors.allowed-origins", () -> "http://localhost:5173");
	}

	private static String env(String key, String defaultValue) {
		String value = System.getenv(key);
		return (value == null || value.isBlank()) ? defaultValue : value;
	}

	@Autowired
	PromotionStrategyRepository strategyRepository;

	@Autowired
	PromotionCampaignRepository campaignRepository;

	@Autowired
	PromotionPlacementRepository placementRepository;

	@Autowired
	CategoryRepository categoryRepository;

	@Autowired
	ProductRepository productRepository;

	@Autowired
	UserAccountRepository userAccountRepository;

	@Autowired
	AddressRepository addressRepository;

	@Autowired
	ShopOrderRepository shopOrderRepository;

	@Autowired
	OrderPromotionSnapshotRepository snapshotRepository;

	@Autowired
	TransactionTemplate transactionTemplate;

	@Test
	void strategyCampaignPlacementRelationshipsAndOrderDiscountDefault() {
		String suffix = UUID.randomUUID().toString().substring(0, 8);

		UUID strategyId = transactionTemplate.execute(status -> {
			PromotionStrategy strategy = strategyRepository.saveAndFlush(PromotionStrategy.createNew(
					"Strat " + suffix,
					"CODE-" + suffix,
					PromotionStrategyType.PERCENTAGE_DISCOUNT,
					new BigDecimal("15"),
					null,
					null,
					null,
					null,
					false,
					180,
					true
			));
			return strategy.getId();
		});

		assertThat(campaignRepository.countByStrategyId(strategyId)).isZero();

		UUID campaignId = transactionTemplate.execute(status -> {
			PromotionStrategy strategy = strategyRepository.findById(strategyId).orElseThrow();
			PromotionCampaign campaign = PromotionCampaign.createNew(
					strategy,
					"Campaign " + suffix,
					"campaign-" + suffix,
					Instant.parse("2026-11-01T00:00:00Z"),
					Instant.parse("2026-12-01T00:00:00Z"),
					null
			);
			assertThat(campaign.getPriority()).isEqualTo(180);
			campaign.setStatus(PromotionCampaignStatus.ACTIVE);
			campaign = campaignRepository.saveAndFlush(campaign);

			UUID productId = seedProduct(suffix);
			campaign.replaceProductIds(Set.of(productId));
			campaignRepository.saveAndFlush(campaign);

			PromotionPlacement.createNew(
					campaign,
					PromotionPlacementKey.HOME_HERO,
					"20% Off",
					"Fresh picks",
					"Shop",
					PromotionCtaType.NONE,
					null,
					null,
					null,
					null,
					"/uploads/promotions/9d1f7e4b-1111-4111-8111-111111111111.png",
					null,
					0,
					null,
					null,
					true
			);
			campaignRepository.saveAndFlush(campaign);
			return campaign.getId();
		});

		assertThat(placementRepository.findByCampaignIdOrderByDisplayOrderAscIdAsc(campaignId)).hasSize(1);
		assertThat(campaignRepository.countByStrategyId(strategyId)).isEqualTo(1);

		assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status ->
				strategyRepository.deleteById(strategyId)
		)).isInstanceOf(DataIntegrityViolationException.class);

		transactionTemplate.executeWithoutResult(status -> {
			campaignRepository.deleteById(campaignId);
			campaignRepository.flush();
		});
		assertThat(placementRepository.findByCampaignIdOrderByDisplayOrderAscIdAsc(campaignId)).isEmpty();
		assertThat(campaignRepository.countByStrategyId(strategyId)).isZero();

		transactionTemplate.executeWithoutResult(status -> strategyRepository.deleteById(strategyId));

		UUID orderId = transactionTemplate.execute(status -> {
			UserAccount user = userAccountRepository.saveAndFlush(UserAccount.createNew(
					"promo.order." + suffix + "@example.com",
					"{noop}Password1!",
					"Promo Order"
			));
			Address address = addressRepository.saveAndFlush(Address.createNew(
					user, "A", "1 Main", null, "City", null, null, "VN", null, true
			));
			ShopOrder order = ShopOrder.createPending(user, address);
			order.setStatus(OrderStatus.PAID);
			order.setCurrency("VND");
			order.setSubtotalAmount(new BigDecimal("100.00"));
			order.setShippingAmount(BigDecimal.ZERO);
			order.setTotalAmount(new BigDecimal("100.00"));
			order = shopOrderRepository.saveAndFlush(order);
			assertThat(order.getDiscountAmount()).isEqualByComparingTo("0");
			assertThat(order.getPromotionSnapshots()).isEmpty();

			OrderPromotionSnapshot snap = OrderPromotionSnapshot.capture(
					null,
					null,
					"Historic",
					"historic",
					"Strat",
					"PERCENTAGE_DISCOUNT",
					null,
					100,
					false,
					new BigDecimal("10"),
					null,
					null,
					new BigDecimal("100.00"),
					new BigDecimal("10.00"),
					"VND"
			);
			order.addPromotionSnapshot(snap);
			order.setDiscountAmount(new BigDecimal("10.00"));
			order.setTotalAmount(new BigDecimal("90.00"));
			shopOrderRepository.saveAndFlush(order);
			return order.getId();
		});

		assertThat(snapshotRepository.findByOrderIdOrderByAppliedAtAscIdAsc(orderId)).hasSize(1);
		ShopOrder reloaded = shopOrderRepository.findById(orderId).orElseThrow();
		assertThat(reloaded.getDiscountAmount()).isEqualByComparingTo("10.00");
	}

	private UUID seedProduct(String suffix) {
		Category category = categoryRepository.saveAndFlush(Category.createNew(
				"Promo Cat " + suffix, "promo-cat-" + suffix, null, null
		));
		Product product = productRepository.saveAndFlush(Product.createNew(
				category, "PROMO-" + suffix, "Promo " + suffix, null, new BigDecimal("10.00"), "VND", true
		));
		return product.getId();
	}
}
