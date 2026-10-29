package com.aptech.aptech4.order.payment;

import java.util.UUID;

import org.springframework.stereotype.Component;

/**
 * Deterministic in-process mock. Never trusts client {@code paymentStatus}.
 * No card data is accepted or stored.
 */
@Component
public class MockPaymentGateway implements PaymentGateway {

	private final MockPaymentProperties properties;

	public MockPaymentGateway(MockPaymentProperties properties) {
		this.properties = properties;
	}

	@Override
	public PaymentChargeResult charge(PaymentChargeCommand command) {
		boolean forceFailure = properties.isAllowClientForceFailure() && command.simulateFailure();
		boolean succeed = !forceFailure && properties.defaultSucceeded();
		if (succeed) {
			return PaymentChargeResult.success("MOCK-" + UUID.randomUUID());
		}
		return PaymentChargeResult.failure("Mock payment declined");
	}
}
