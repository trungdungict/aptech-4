import 'package:aptech4_mobile/features/auth/domain/auth_session.dart';

/// Persistence for refresh token + non-sensitive user snapshot.
/// Access tokens must stay in memory only (mobile counterpart of ADR-013).
abstract class TokenStore {
  Future<String?> readRefreshToken();

  Future<void> writeRefreshToken(String token);

  Future<void> deleteRefreshToken();

  Future<AuthUser?> readUserSnapshot();

  Future<void> writeUserSnapshot(AuthUser user);

  Future<void> deleteUserSnapshot();

  Future<void> clearAll();
}
