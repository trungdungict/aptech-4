package com.aptech.aptech4.chat.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;

import com.aptech.aptech4.common.api.ApiException;

@ExtendWith(MockitoExtension.class)
class AdminInventoryFacadeTest {

	@Mock
	AdminAnalyticsService adminAnalyticsService;

	AdminInventoryFacade facade;

	@BeforeEach
	void setUp() {
		facade = new AdminInventoryFacade(adminAnalyticsService);
	}

	@Test
	void emptySuccessIsNotFailure() {
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("items", List.of());
		when(adminAnalyticsService.lowStockProducts(isNull(), anyInt())).thenReturn(result);

		Map<String, Object> envelope = facade.lowStock(null, 10);
		assertThat(envelope.get("status")).isEqualTo(ChatToolEnvelope.SUCCESS_EMPTY);
		assertThat(envelope.get("result")).isEqualTo(result);
	}

	@Test
	void accessDeniedBecomesFailureEnvelope() {
		when(adminAnalyticsService.outOfStockProducts(anyInt()))
				.thenThrow(new AccessDeniedException("nope"));

		Map<String, Object> envelope = facade.outOfStock(5);
		assertThat(envelope.get("status")).isEqualTo(ChatToolEnvelope.FAILURE);
		assertThat(envelope.get("errorCategory")).isEqualTo(ChatToolEnvelope.ERROR_AUTHORIZATION);
		assertThat(envelope.get("result")).isNull();
	}

	@Test
	void validationErrorBecomesFailureNotEmptyList() {
		when(adminAnalyticsService.inventoryRiskSummary(any(), anyInt()))
				.thenThrow(new ApiException(HttpStatus.BAD_REQUEST, "Bad Request", "bad threshold"));

		Map<String, Object> envelope = facade.riskSummary(-1, 10);
		assertThat(envelope.get("status")).isEqualTo(ChatToolEnvelope.FAILURE);
		assertThat(envelope.get("errorCategory")).isEqualTo(ChatToolEnvelope.ERROR_VALIDATION);
		assertThat(envelope.get("result")).isNull();
	}
}
