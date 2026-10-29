package com.aptech.aptech4.chat.tools;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

class AdminChatToolsParseInstantTest {

	@Test
	void blankBecomesNull() {
		assertThat(AdminChatTools.parseInstant(null)).isNull();
		assertThat(AdminChatTools.parseInstant("  ")).isNull();
	}

	@Test
	void parsesIsoInstant() {
		assertThat(AdminChatTools.parseInstant("2026-10-29T06:00:00Z"))
				.isEqualTo(Instant.parse("2026-10-29T06:00:00Z"));
	}

	@Test
	void parsesDateOnlyAsUtcMidnight() {
		assertThat(AdminChatTools.parseInstant("2026-10-01"))
				.isEqualTo(LocalDate.of(2026, 10, 1).atStartOfDay().toInstant(ZoneOffset.UTC));
	}

	@Test
	void invalidBecomesNull() {
		assertThat(AdminChatTools.parseInstant("last month")).isNull();
		assertThat(AdminChatTools.parseInstant("2026/10/01")).isNull();
	}
}
