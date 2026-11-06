package com.aptech.aptech4.catalog.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionTemplate;

import com.aptech.aptech4.catalog.application.AdminCatalogTranslationService;
import com.aptech.aptech4.catalog.application.CatalogLocalizationService;
import com.aptech.aptech4.catalog.domain.Category;
import com.aptech.aptech4.catalog.domain.Product;
import com.aptech.aptech4.catalog.persistence.CategoryRepository;
import com.aptech.aptech4.catalog.persistence.ProductRepository;
import com.aptech.aptech4.common.locale.AppLocale;

@SpringBootTest
class AdminCatalogTranslationAtomicityIntegrationTest {

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
	AdminCatalogTranslationService translationService;

	@Autowired
	ProductRepository productRepository;

	@Autowired
	CategoryRepository categoryRepository;

	@Autowired
	TransactionTemplate transactionTemplate;

	@MockitoSpyBean
	CatalogLocalizationService catalogLocalizationService;

	private UUID productId;
	private String originalName;

	@BeforeEach
	void seed() {
		String suffix = UUID.randomUUID().toString().substring(0, 8);
		originalName = "Atomic VI " + suffix;
		transactionTemplate.executeWithoutResult(status -> {
			Category category = categoryRepository.saveAndFlush(Category.createNew(
					"Atomic Cat " + suffix, "atomic-cat-" + suffix, null, null
			));
			Product product = productRepository.saveAndFlush(Product.createNew(
					category, "ATM-" + suffix, originalName, "orig", new BigDecimal("10.00"), "VND", true
			));
			productId = product.getId();
		});
	}

	@Test
	void dualWriteRollsBackWhenEnglishUpsertFails() {
		doAnswer(invocation -> invocation.callRealMethod())
				.when(catalogLocalizationService)
				.upsertProductTranslation(any(), eq(AppLocale.VI.code()), any(), any());
		doThrow(new DataIntegrityViolationException("forced EN failure"))
				.when(catalogLocalizationService)
				.upsertProductTranslation(any(), eq(AppLocale.EN.code()), any(), any());

		EntityTranslationsUpdateRequest request = new EntityTranslationsUpdateRequest(java.util.Map.of(
				"vi", new TranslationLocaleTextRequest("Should Roll Back VI", "new"),
				"en", new TranslationLocaleTextRequest("Should Roll Back EN", "new en")
		));

		assertThatThrownBy(() -> translationService.putProductTranslations(productId, request))
				.isInstanceOf(DataIntegrityViolationException.class);

		Product reloaded = productRepository.findAdminById(productId).orElseThrow();
		assertThat(reloaded.getName()).isEqualTo(originalName);
		assertThat(reloaded.getDescription()).isEqualTo("orig");
	}
}
