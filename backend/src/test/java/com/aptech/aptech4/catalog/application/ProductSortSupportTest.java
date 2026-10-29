package com.aptech.aptech4.catalog.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

import com.aptech.aptech4.common.api.ApiException;

class ProductSortSupportTest {

	@Test
	void defaultsToNameAscWithIdTieBreaker() {
		Sort sort = ProductSortSupport.resolve(null);
		assertThat(sort.getOrderFor("name").getDirection()).isEqualTo(Sort.Direction.ASC);
		assertThat(sort.getOrderFor("id").getDirection()).isEqualTo(Sort.Direction.ASC);
	}

	@Test
	void mapsPriceToUnitPrice() {
		Sort sort = ProductSortSupport.resolve("price,desc");
		assertThat(sort.getOrderFor("unitPrice").getDirection()).isEqualTo(Sort.Direction.DESC);
		assertThat(sort.getOrderFor("id")).isNotNull();
	}

	@Test
	void rejectsUnknownField() {
		assertThatThrownBy(() -> ProductSortSupport.resolve("sku,asc"))
			.isInstanceOf(ApiException.class)
			.hasMessageContaining("Invalid sort field");
	}
}
