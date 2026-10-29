package com.aptech.aptech4.seed;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Development/demo seed switches. Default {@code enabled=false} — never auto-seed on normal startup.
 */
@ConfigurationProperties(prefix = "aptech.seed")
public class DemoSeedProperties {

	/**
	 * When true, {@link DemoSeedRunner} executes once at startup.
	 * Must be set explicitly (e.g. {@code make seed-demo}); never default-on.
	 */
	private boolean enabled = false;

	/** Exit the JVM after a successful seed (one-shot Makefile runs). */
	private boolean exitAfter = true;

	/** Hard block: seed/reset refuse when the {@code prod} profile is active unless this is true. */
	private boolean allowOnProd = false;

	/**
	 * When true, {@link DemoResetRunner} plans/executes a scoped demo reset (never auto on normal startup).
	 */
	private boolean reset = false;

	/** When true with {@code reset}, only prints the plan — no deletes. */
	private boolean resetDryRun = false;

	/**
	 * Must equal {@code yes} for a destructive reset. Empty/other values refuse execute.
	 */
	private String resetConfirm = "";

	/**
	 * After a confirmed reset, run {@link DemoSeedService#seedAll()} again (Makefile {@code reset-demo} default).
	 */
	private boolean reseedAfterReset = false;

	private String adminEmail = "admin@aptech4.local";
	private String adminPassword = "";
	private String adminFullName = "Demo Admin";

	private String customerEmail = "customer@aptech4.local";
	private String customerPassword = "";
	private String customerFullName = "Demo Customer";

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public boolean isExitAfter() {
		return exitAfter;
	}

	public void setExitAfter(boolean exitAfter) {
		this.exitAfter = exitAfter;
	}

	public boolean isAllowOnProd() {
		return allowOnProd;
	}

	public void setAllowOnProd(boolean allowOnProd) {
		this.allowOnProd = allowOnProd;
	}

	public boolean isReset() {
		return reset;
	}

	public void setReset(boolean reset) {
		this.reset = reset;
	}

	public boolean isResetDryRun() {
		return resetDryRun;
	}

	public void setResetDryRun(boolean resetDryRun) {
		this.resetDryRun = resetDryRun;
	}

	public String getResetConfirm() {
		return resetConfirm;
	}

	public void setResetConfirm(String resetConfirm) {
		this.resetConfirm = resetConfirm;
	}

	public boolean isReseedAfterReset() {
		return reseedAfterReset;
	}

	public void setReseedAfterReset(boolean reseedAfterReset) {
		this.reseedAfterReset = reseedAfterReset;
	}

	public String getAdminEmail() {
		return adminEmail;
	}

	public void setAdminEmail(String adminEmail) {
		this.adminEmail = adminEmail;
	}

	public String getAdminPassword() {
		return adminPassword;
	}

	public void setAdminPassword(String adminPassword) {
		this.adminPassword = adminPassword;
	}

	public String getAdminFullName() {
		return adminFullName;
	}

	public void setAdminFullName(String adminFullName) {
		this.adminFullName = adminFullName;
	}

	public String getCustomerEmail() {
		return customerEmail;
	}

	public void setCustomerEmail(String customerEmail) {
		this.customerEmail = customerEmail;
	}

	public String getCustomerPassword() {
		return customerPassword;
	}

	public void setCustomerPassword(String customerPassword) {
		this.customerPassword = customerPassword;
	}

	public String getCustomerFullName() {
		return customerFullName;
	}

	public void setCustomerFullName(String customerFullName) {
		this.customerFullName = customerFullName;
	}
}
