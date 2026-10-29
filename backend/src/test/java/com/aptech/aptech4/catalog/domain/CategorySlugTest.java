package com.aptech.aptech4.catalog.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CategorySlugTest {

	@Test
	void slugifyNormalizesName() {
		assertThat(Category.slugify("  Phones & Tablets ")).isEqualTo("phones-tablets");
		assertThat(Category.slugify("!!!")).isEqualTo("category");
	}
}
