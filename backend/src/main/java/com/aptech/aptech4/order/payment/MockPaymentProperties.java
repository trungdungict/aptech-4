package com.aptech.aptech4.order.payment;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "aptech.payment.mock")
public class MockPaymentProperties {

	/**
	 * Default mock outcome when client does not force failure: SUCCEEDED or FAILED.
	 */
	private String defaultOutcome = "SUCCEEDED";

	/**
	 * When true, checkout request field {@code simulateFailure} is honored (tests/demo only).
	 */
	private boolean allowClientForceFailure = false;

	public String getDefaultOutcome() {
		return defaultOutcome;
	}

	public void setDefaultOutcome(String defaultOutcome) {
		this.defaultOutcome = defaultOutcome;
	}

	public boolean isAllowClientForceFailure() {
		return allowClientForceFailure;
	}

	public void setAllowClientForceFailure(boolean allowClientForceFailure) {
		this.allowClientForceFailure = allowClientForceFailure;
	}

	public boolean defaultSucceeded() {
		return !"FAILED".equalsIgnoreCase(defaultOutcome);
	}
}
