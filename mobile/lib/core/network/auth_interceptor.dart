import 'package:dio/dio.dart';

import 'package:aptech4_mobile/features/auth/data/auth_api.dart';
import 'package:aptech4_mobile/features/auth/data/session_coordinator.dart';

/// Attaches Bearer access tokens and performs a single refresh+retry on 401.
///
/// Retry uses a fresh [Dio] with the same [BaseOptions] and current
/// [HttpClientAdapter] (no auth interceptor) to avoid queue deadlocks and to
/// pick up test adapters assigned after construction.
class AuthInterceptor extends Interceptor {
  AuthInterceptor({
    required this.session,
    required this.dio,
  });

  final SessionCoordinator session;
  final Dio dio;

  static bool shouldSkipRefresh(RequestOptions options) {
    if (options.extra[AuthRequestFlags.skipAuthRefresh] == true) {
      return true;
    }
    final path = options.path;
    return path.contains(AuthApi.loginPath) ||
        path.contains(AuthApi.registerPath) ||
        path.contains(AuthApi.refreshPath) ||
        path.contains(AuthApi.logoutPath);
  }

  @override
  void onRequest(RequestOptions options, RequestInterceptorHandler handler) {
    final skip = shouldSkipRefresh(options);
    final token = session.accessToken;
    if (!skip && token != null && token.isNotEmpty) {
      options.headers['Authorization'] = 'Bearer $token';
    }
    handler.next(options);
  }

  @override
  Future<void> onError(DioException err, ErrorInterceptorHandler handler) async {
    final status = err.response?.statusCode;
    final skip = shouldSkipRefresh(err.requestOptions);
    final alreadyRetried = err.requestOptions.extra['authRetried'] == true;

    if (status != 401 || skip || alreadyRetried) {
      handler.next(err);
      return;
    }

    final refreshed = await session.refreshAccessTokenSingleFlight();
    if (refreshed == null || refreshed.isEmpty) {
      handler.next(err);
      return;
    }

    final request = err.requestOptions;
    request.headers['Authorization'] = 'Bearer $refreshed';
    request.extra['authRetried'] = true;

    try {
      final retryDio = Dio(dio.options)..httpClientAdapter = dio.httpClientAdapter;
      final response = await retryDio.fetch<dynamic>(request);
      handler.resolve(response);
    } on DioException catch (retryError) {
      handler.next(retryError);
    }
  }
}
