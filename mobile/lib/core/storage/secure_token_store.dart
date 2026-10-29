import 'dart:convert';

import 'package:flutter_secure_storage/flutter_secure_storage.dart';

import 'package:aptech4_mobile/core/storage/token_store.dart';
import 'package:aptech4_mobile/features/auth/domain/auth_session.dart';

/// Platform secure storage for refresh token (not SharedPreferences / plain files).
class SecureTokenStore implements TokenStore {
  SecureTokenStore({FlutterSecureStorage? storage})
      : _storage = storage ??
            const FlutterSecureStorage(
              aOptions: AndroidOptions(encryptedSharedPreferences: true),
            );

  static const _refreshKey = 'aptech4.refresh_token';
  static const _userKey = 'aptech4.user_snapshot';

  final FlutterSecureStorage _storage;

  @override
  Future<String?> readRefreshToken() => _storage.read(key: _refreshKey);

  @override
  Future<void> writeRefreshToken(String token) =>
      _storage.write(key: _refreshKey, value: token);

  @override
  Future<void> deleteRefreshToken() => _storage.delete(key: _refreshKey);

  @override
  Future<AuthUser?> readUserSnapshot() async {
    final raw = await _storage.read(key: _userKey);
    if (raw == null || raw.isEmpty) {
      return null;
    }
    final map = jsonDecode(raw) as Map<String, dynamic>;
    return AuthUser.fromJson(map);
  }

  @override
  Future<void> writeUserSnapshot(AuthUser user) =>
      _storage.write(key: _userKey, value: jsonEncode(user.toJson()));

  @override
  Future<void> deleteUserSnapshot() => _storage.delete(key: _userKey);

  @override
  Future<void> clearAll() async {
    await deleteRefreshToken();
    await deleteUserSnapshot();
  }
}

/// In-memory store for unit tests.
class InMemoryTokenStore implements TokenStore {
  String? refreshToken;
  AuthUser? user;

  @override
  Future<void> clearAll() async {
    refreshToken = null;
    user = null;
  }

  @override
  Future<void> deleteRefreshToken() async => refreshToken = null;

  @override
  Future<void> deleteUserSnapshot() async => user = null;

  @override
  Future<String?> readRefreshToken() async => refreshToken;

  @override
  Future<AuthUser?> readUserSnapshot() async => user;

  @override
  Future<void> writeRefreshToken(String token) async => refreshToken = token;

  @override
  Future<void> writeUserSnapshot(AuthUser next) async => user = next;
}
