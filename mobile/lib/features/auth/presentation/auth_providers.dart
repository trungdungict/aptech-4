import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:aptech4_mobile/core/config/api_config.dart';
import 'package:aptech4_mobile/core/network/dio_client.dart';
import 'package:aptech4_mobile/core/storage/secure_token_store.dart';
import 'package:aptech4_mobile/core/storage/token_store.dart';
import 'package:aptech4_mobile/features/auth/data/auth_api.dart';
import 'package:aptech4_mobile/features/auth/data/auth_repository.dart';
import 'package:aptech4_mobile/features/auth/data/session_coordinator.dart';
import 'package:aptech4_mobile/features/auth/domain/auth_session.dart';

final apiConfigProvider = Provider<ApiConfig>((ref) => ApiConfig.fromEnvironment());

final tokenStoreProvider = Provider<TokenStore>((ref) => SecureTokenStore());

/// Late-bound wiring: AuthApi → Session → Dio(with interceptor) → AuthApi refresh path.
final authBootstrapProvider = Provider<_AuthBootstrap>((ref) {
  final config = ref.watch(apiConfigProvider);
  final store = ref.watch(tokenStoreProvider);

  // Temporary Dio without auth interceptor for AuthApi used by SessionCoordinator.
  final bareDio = DioClientFactory.create(config: config);
  final authApi = AuthApi(bareDio);
  final session = SessionCoordinator(authApi: authApi, tokenStore: store);

  // Protected client with interceptor (uses same AuthApi/session for refresh).
  final authedDio = DioClientFactory.create(config: config, session: session);
  // Point AuthApi used by repository at authed Dio for non-auth calls later;
  // auth endpoints always skip refresh.
  final repoApi = AuthApi(authedDio);
  // Refresh must use bare Dio to avoid interceptor recursion on /auth/refresh.
  // Session already holds AuthApi(bareDio).
  final repository = AuthRepository(authApi: repoApi, session: session);

  return _AuthBootstrap(
    dio: authedDio,
    session: session,
    repository: repository,
  );
});

final dioProvider = Provider<Dio>((ref) => ref.watch(authBootstrapProvider).dio);

final sessionCoordinatorProvider =
    Provider<SessionCoordinator>((ref) => ref.watch(authBootstrapProvider).session);

final authRepositoryProvider =
    Provider<AuthRepository>((ref) => ref.watch(authBootstrapProvider).repository);

final authUserProvider = Provider<AuthUser?>((ref) {
  return ref.watch(sessionCoordinatorProvider).user;
});

class _AuthBootstrap {
  const _AuthBootstrap({
    required this.dio,
    required this.session,
    required this.repository,
  });

  final Dio dio;
  final SessionCoordinator session;
  final AuthRepository repository;
}
