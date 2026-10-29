import 'package:dio/dio.dart';

import 'package:aptech4_mobile/core/errors/api_exception.dart';
import 'package:aptech4_mobile/features/auth/data/auth_api.dart';
import 'package:aptech4_mobile/features/auth/data/session_coordinator.dart';
import 'package:aptech4_mobile/features/auth/domain/auth_session.dart';

class AuthRepository {
  AuthRepository({
    required AuthApi authApi,
    required SessionCoordinator session,
  })  : _authApi = authApi,
        _session = session;

  final AuthApi _authApi;
  final SessionCoordinator _session;

  SessionCoordinator get session => _session;

  Future<AuthSession> login(LoginPayload payload) async {
    try {
      final result = await _authApi.login(payload);
      await _session.applySession(result);
      return result;
    } on DioException catch (e) {
      throw mapDioException(e);
    }
  }

  Future<AuthSession> register(RegisterPayload payload) async {
    try {
      final result = await _authApi.register(payload);
      await _session.applySession(result);
      return result;
    } on DioException catch (e) {
      throw mapDioException(e);
    }
  }

  /// Always clears local credentials. Server revoke is best-effort (204 idempotent).
  Future<void> logout() async {
    final refresh = await _session.readRefreshToken();
    try {
      if (refresh != null && refresh.isNotEmpty) {
        await _authApi.logout(refresh);
      }
    } on DioException {
      // Local clear still happens; do not claim server success.
    } on ApiException {
      // ignore
    } finally {
      await _session.clearSession();
    }
  }

  Future<AuthUser?> restore() => _session.restoreSessionFromStorage();
}
