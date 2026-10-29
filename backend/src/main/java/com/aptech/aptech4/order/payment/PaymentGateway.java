package com.aptech.aptech4.order.payment;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Abstraction over payment providers. Mock implementation for demo; real PSP can replace later.
 */
public interface PaymentGateway {

	PaymentChargeResult charge(PaymentChargeCommand command);

	record PaymentChargeCommand(
			UUID orderId,
			BigDecimal amount,
			String currency,
			boolean simulateFailure
	) {
	}

	record PaymentChargeResult(
			boolean succeeded,
			String providerReference,
			String failureReason
	) {
		public static PaymentChargeResult success(String reference) {
			return new PaymentChargeResult(true, reference, null);
		}

		public static PaymentChargeResult failure(String reason) {
			return new PaymentChargeResult(false, null, reason);
		}
	}
}
