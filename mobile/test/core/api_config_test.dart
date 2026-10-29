import 'package:flutter_test/flutter_test.dart';

import 'package:aptech4_mobile/core/config/api_config.dart';

void main() {
  test('normalizeBaseUrl strips trailing slashes', () {
    expect(
      ApiConfig.normalizeBaseUrl('http://10.0.2.2:8080/api/v1/'),
      'http://10.0.2.2:8080/api/v1',
    );
  });

  test('normalizeBaseUrl falls back when empty', () {
    expect(ApiConfig.normalizeBaseUrl('  '), ApiConfig.defaultBaseUrl);
  });

  test('default is emulator-safe HTTP, not production', () {
    expect(ApiConfig.defaultBaseUrl, 'http://10.0.2.2:8080/api/v1');
    expect(ApiConfig.defaultBaseUrl.contains('://localhost'), isFalse);
  });

  test('usesCleartextHttp detects scheme', () {
    expect(
      const ApiConfig(baseUrl: 'http://10.0.2.2:8080/api/v1').usesCleartextHttp,
      isTrue,
    );
    expect(
      const ApiConfig(baseUrl: 'https://api.example.com/api/v1').usesCleartextHttp,
      isFalse,
    );
  });

  test('looksLikeLoopbackHost flags localhost and 127.0.0.1', () {
    expect(ApiConfig.looksLikeLoopbackHost('http://localhost:8080/api/v1'), isTrue);
    expect(ApiConfig.looksLikeLoopbackHost('http://127.0.0.1:8080/api/v1'), isTrue);
    expect(ApiConfig.looksLikeLoopbackHost('http://10.0.2.2:8080/api/v1'), isFalse);
    expect(ApiConfig.looksLikeLoopbackHost('http://192.168.1.10:8080/api/v1'), isFalse);
  });

  test('fromEnvironment uses default when dart-define unset', () {
    // Test binary is built without API_BASE_URL define → default applies.
    final config = ApiConfig.fromEnvironment();
    expect(config.baseUrl, ApiConfig.defaultBaseUrl);
  });
}
