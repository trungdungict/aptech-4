package com.aptech.aptech4.catalog.cache;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.aptech.aptech4.catalog.application.ProductSortSupport;

class CatalogCacheKeysTest {

	@Test
	void productListKeysDifferByParams() {
		UUID cat = UUID.fromString("11111111-1111-1111-1111-111111111111");
		String base = CatalogCacheKeys.productList(null, null, null, null, null, 0, 20, null);
		String withQ = CatalogCacheKeys.productList("tea", null, null, null, null, 0, 20, null);
		String withCat = CatalogCacheKeys.productList(null, cat, null, null, null, 0, 20, null);
		String withPage = CatalogCacheKeys.productList(null, null, null, null, null, 1, 20, null);
		String withSort = CatalogCacheKeys.productList(null, null, null, null, null, 0, 20, "price,desc");

		assertThat(base).isNotEqualTo(withQ);
		assertThat(base).isNotEqualTo(withCat);
		assertThat(base).isNotEqualTo(withPage);
		assertThat(base).isNotEqualTo(withSort);
	}

	@Test
	void blankKeywordAndNullKeywordShareDashToken() {
		String a = CatalogCacheKeys.productList("  ", null, null, null, null, 0, 20, null);
		String b = CatalogCacheKeys.productList(null, null, null, null, null, 0, 20, null);
		assertThat(a).isEqualTo(b);
		assertThat(a).startsWith("-|");
	}

	@Test
	void defaultSortNormalizedLikeService() {
		String a = CatalogCacheKeys.productList(null, null, null, null, null, 0, 20, null);
		String b = CatalogCacheKeys.productList(null, null, null, null, null, 0, 20, "  ");
		String c = CatalogCacheKeys.productList(null, null, null, null, null, 0, 20, ProductSortSupport.DEFAULT_SORT);
		assertThat(a).isEqualTo(b).isEqualTo(c);
		assertThat(a).endsWith("|" + ProductSortSupport.DEFAULT_SORT.toLowerCase());
	}

	@Test
	void decimalKeysStripTrailingZeros() {
		String a = CatalogCacheKeys.productList(
				null, null, new BigDecimal("10.50"), new BigDecimal("20.0"), true, 0, 20, null
		);
		String b = CatalogCacheKeys.productList(
				null, null, new BigDecimal("10.5"), new BigDecimal("20"), true, 0, 20, null
		);
		assertThat(a).isEqualTo(b);
	}
}
