package com.aptech.aptech4.chat.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "aptech.ai")
public class AiProperties {

	/** Master switch — when false, chat endpoints return 503 without calling Ollama. */
	private boolean enabled = false;

	private String model = "llama3.2";

	private int requestTimeoutSeconds = 90;

	private int maxHistoryMessages = 12;

	private int maxMessageChars = 2000;

	/** Available-to-sell at or below this value is "low stock" for admin tools. */
	private int lowStockThreshold = 5;

	private int maxToolResults = 20;

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public String getModel() {
		return model;
	}

	public void setModel(String model) {
		this.model = model;
	}

	public int getRequestTimeoutSeconds() {
		return requestTimeoutSeconds;
	}

	public void setRequestTimeoutSeconds(int requestTimeoutSeconds) {
		this.requestTimeoutSeconds = requestTimeoutSeconds;
	}

	public int getMaxHistoryMessages() {
		return maxHistoryMessages;
	}

	public void setMaxHistoryMessages(int maxHistoryMessages) {
		this.maxHistoryMessages = maxHistoryMessages;
	}

	public int getMaxMessageChars() {
		return maxMessageChars;
	}

	public void setMaxMessageChars(int maxMessageChars) {
		this.maxMessageChars = maxMessageChars;
	}

	public int getLowStockThreshold() {
		return lowStockThreshold;
	}

	public void setLowStockThreshold(int lowStockThreshold) {
		this.lowStockThreshold = lowStockThreshold;
	}

	public int getMaxToolResults() {
		return maxToolResults;
	}

	public void setMaxToolResults(int maxToolResults) {
		this.maxToolResults = maxToolResults;
	}
}
