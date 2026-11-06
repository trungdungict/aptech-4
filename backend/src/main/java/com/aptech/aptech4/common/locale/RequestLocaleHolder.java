package com.aptech.aptech4.common.locale;

/**
 * Request-scoped {@link AppLocale} storage. Bound by {@link RequestLocaleFilter}; never set from
 * controllers. Cleared in a {@code finally} block so values cannot leak across threads.
 */
public final class RequestLocaleHolder {

	private static final ThreadLocal<AppLocale> CURRENT = new ThreadLocal<>();

	private RequestLocaleHolder() {
	}

	public static void set(AppLocale locale) {
		if (locale == null) {
			CURRENT.remove();
			return;
		}
		CURRENT.set(locale);
	}

	/**
	 * @return locale for this request, or {@link AppLocale#DEFAULT} when unset (outside a request)
	 */
	public static AppLocale get() {
		AppLocale locale = CURRENT.get();
		return locale == null ? AppLocale.DEFAULT : locale;
	}

	/** Visible for tests: {@code null} when no request locale is bound. */
	static AppLocale getOrNull() {
		return CURRENT.get();
	}

	public static void clear() {
		CURRENT.remove();
	}
}
