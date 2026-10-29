import 'package:aptech4_mobile/core/storage/token_store.dart';
import 'package:aptech4_mobile/features/auth/data/auth_api.dart';
import 'package:aptech4_mobile/features/auth/domain/auth_session.dart';

/// Access token in memory; refresh token in [TokenStore].
/// Single-flight refresh matches frontend `session-coordinator.ts`.
class SessionCoordinator {
  SessionCoordinator({
    required AuthApi authApi,
    required TokenStore tokenStore,
  })  : _authApi = authApi,
        _tokenStore = tokenStore;

  final AuthApi _authApi;
  final TokenStore _tokenStore;

  String? _accessToken;
  AuthUser? _user;
  Future<String?>? _refreshInFlight;

  String? get accessToken => _accessToken;

  AuthUser? get user => _user;

  bool get isAuthenticated => _accessToken != null && _user != null;

  Future<String?> readRefreshToken() => _tokenStore.readRefreshToken();

  Future<void> applySession(AuthSession session) async {
    _accessToken = session.accessToken;
    _user = session.user;
    await _tokenStore.writeRefreshToken(session.refreshToken);
    await _tokenStore.writeUserSnapshot(session.user);
  }

  Future<void> clearSession() async {
    _accessToken = null;
    _user = null;
    await _tokenStore.clearAll();
  }

  /// Concurrent callers share one refresh; failure clears the session.
  Future<String?> refreshAccessTokenSingleFlight() {
    final existing = _refreshInFlight;
    if (existing != null) {
      return existing;
    }

    final future = () async {
      final refreshToken = await _tokenStore.readRefreshToken();
      if (refreshToken == null || refreshToken.isEmpty) {
        await clearSession();
        return null;
      }
      try {
        final session = await _authApi.refresh(refreshToken);
        await applySession(session);
        return session.accessToken;
      } catch (_) {
        await clearSession();
        return null;
      }
    }();

    _refreshInFlight = future.whenComplete(() {
      _refreshInFlight = null;
    });
    return _refreshInFlight!;
  }

  Future<AuthUser?> restoreSessionFromStorage() async {
    final refreshToken = await _tokenStore.readRefreshToken();
    if (refreshToken == null || refreshToken.isEmpty) {
      await clearSession();
      return null;
    }
    final access = await refreshAccessTokenSingleFlight();
    if (access == null) {
      return null;
    }
    return _user;
  }
}
