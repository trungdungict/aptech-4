/// Build-time API configuration (ADR-002, ADR-014).
///
/// Compiled into the binary via:
/// `--dart-define=API_BASE_URL=http://10.0.2.2:8080/api/v1`
///
/// A shell/Make variable named `API_BASE_URL` is only used by Docker/Make to
/// supply that dart-define. The running app does **not** read OS env for the
/// API base. Values are public configuration, not secrets.
class ApiConfig {
  const ApiConfig({required this.baseUrl});

  /// Must include the `/api/v1` prefix used by the Spring Boot API.
  final String baseUrl;

  static const String defineKey = 'API_BASE_URL';

  /// Android emulator → host loopback. Safe local default only — not production.
  static const String defaultBaseUrl = 'http://10.0.2.2:8080/api/v1';

  factory ApiConfig.fromEnvironment() {
    const raw = String.fromEnvironment(defineKey, defaultValue: defaultBaseUrl);
    return ApiConfig(baseUrl: normalizeBaseUrl(raw));
  }

  static String normalizeBaseUrl(String value) {
    var url = value.trim();
    if (url.isEmpty) {
      url = defaultBaseUrl;
    }
    while (url.endsWith('/')) {
      url = url.substring(0, url.length - 1);
    }
    return url;
  }

  /// True when the URL uses http:// (needs Android cleartext in debug/profile).
  bool get usesCleartextHttp => baseUrl.toLowerCase().startsWith('http://');

  /// Hostnames that are almost never reachable from an Android emulator/device
  /// as written (documentational / validation helper — not a network probe).
  static bool looksLikeLoopbackHost(String url) {
    final lower = url.toLowerCase();
    return lower.contains('://localhost') ||
        lower.contains('://127.0.0.1') ||
        lower.contains('://[::1]');
  }
}
