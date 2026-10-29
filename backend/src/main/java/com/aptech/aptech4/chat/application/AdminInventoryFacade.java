package com.aptech.aptech4.chat.application;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import com.aptech.aptech4.common.api.ApiException;

/**
 * Executes inventory analytics with timing + envelope status. Used by deterministic
 * admin inventory replies and by {@link com.aptech.aptech4.chat.tools.AdminChatTools}.
 */
@Service
public class AdminInventoryFacade {

	private static final Logger log = LoggerFactory.getLogger(AdminInventoryFacade.class);

	private final AdminAnalyticsService adminAnalyticsService;

	public AdminInventoryFacade(AdminAnalyticsService adminAnalyticsService) {
		this.adminAnalyticsService = adminAnalyticsService;
	}

	public Map<String, Object> lowStock(Integer threshold, Integer limit) {
		return invoke("getLowStockProducts", () -> {
			Map<String, Object> result = adminAnalyticsService.lowStockProducts(
					threshold, limit == null ? 10 : limit
			);
			return envelopeFromListResult(result);
		});
	}

	public Map<String, Object> outOfStock(Integer limit) {
		return invoke("getOutOfStockProducts", () -> {
			Map<String, Object> result = adminAnalyticsService.outOfStockProducts(limit == null ? 10 : limit);
			return envelopeFromListResult(result);
		});
	}

	public Map<String, Object> riskSummary(Integer threshold, Integer limit) {
		return invoke("getInventoryRiskSummary", () -> {
			Map<String, Object> result = adminAnalyticsService.inventoryRiskSummary(
					threshold, limit == null ? 10 : limit
			);
			int low = sizeOf(result.get("lowStock"));
			int out = sizeOf(result.get("outOfStock"));
			return new TimedResult(result, low + out);
		});
	}

	public Map<String, Object> execute(AdminInventoryQueryKind kind) {
		return switch (kind) {
			case LOW_STOCK -> lowStock(null, 20);
			case OUT_OF_STOCK -> outOfStock(20);
			case RISK_SUMMARY -> riskSummary(null, 20);
			case NONE -> ChatToolEnvelope.failure(
					"inventory",
					ChatToolEnvelope.ERROR_VALIDATION,
					"No inventory intent",
					0L
			);
		};
	}

	private Map<String, Object> invoke(String tool, ThrowingSupplier<TimedResult> supplier) {
		long started = System.nanoTime();
		try {
			TimedResult timed = supplier.get();
			long ms = elapsedMs(started);
			Map<String, Object> envelope = ChatToolEnvelope.success(tool, timed.result(), timed.resultSize(), ms);
			log.info(
					"chat_tool tool={} status={} resultSize={} durationMs={}",
					tool,
					envelope.get("status"),
					timed.resultSize(),
					ms
			);
			return envelope;
		}
		catch (AccessDeniedException ex) {
			long ms = elapsedMs(started);
			log.warn("chat_tool tool={} status=FAILURE category=AUTHORIZATION durationMs={}", tool, ms);
			return ChatToolEnvelope.failure(tool, ChatToolEnvelope.ERROR_AUTHORIZATION, "ADMIN role required", ms);
		}
		catch (ApiException ex) {
			long ms = elapsedMs(started);
			String category = ex.getStatus() == HttpStatus.BAD_REQUEST
					? ChatToolEnvelope.ERROR_VALIDATION
					: ChatToolEnvelope.ERROR_DATA_ACCESS;
			log.warn("chat_tool tool={} status=FAILURE category={} durationMs={} detail={}", tool, category, ms, ex.getMessage());
			return ChatToolEnvelope.failure(tool, category, ex.getMessage(), ms);
		}
		catch (RuntimeException ex) {
			long ms = elapsedMs(started);
			log.error("chat_tool tool={} status=FAILURE category=INTERNAL durationMs={}", tool, ms, ex);
			return ChatToolEnvelope.failure(
					tool,
					ChatToolEnvelope.ERROR_INTERNAL,
					"Inventory query failed",
					ms
			);
		}
	}

	@SuppressWarnings("unchecked")
	private static TimedResult envelopeFromListResult(Map<String, Object> result) {
		Object items = result.get("items");
		int size = items instanceof List<?> list ? list.size() : 0;
		return new TimedResult(result, size);
	}

	private static int sizeOf(Object raw) {
		return raw instanceof List<?> list ? list.size() : 0;
	}

	private static long elapsedMs(long startedNanos) {
		return Math.max(0L, (System.nanoTime() - startedNanos) / 1_000_000L);
	}

	@FunctionalInterface
	private interface ThrowingSupplier<T> {
		T get();
	}

	private record TimedResult(Map<String, Object> result, int resultSize) {
	}
}
