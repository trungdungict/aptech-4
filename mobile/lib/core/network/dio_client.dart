import 'package:dio/dio.dart';

import 'package:aptech4_mobile/core/config/api_config.dart';
import 'package:aptech4_mobile/core/network/auth_interceptor.dart';
import 'package:aptech4_mobile/features/auth/data/session_coordinator.dart';

/// Central Dio factory. Does not log request/response bodies or Authorization.
class DioClientFactory {
  static const connectTimeout = Duration(seconds: 15);
  static const sendTimeout = Duration(seconds: 15);
  static const receiveTimeout = Duration(seconds: 30);

  static Dio create({
    required ApiConfig config,
    SessionCoordinator? session,
    List<Interceptor> extraInterceptors = const [],
  }) {
    final dio = Dio(
      BaseOptions(
        baseUrl: config.baseUrl,
        connectTimeout: connectTimeout,
        sendTimeout: sendTimeout,
        receiveTimeout: receiveTimeout,
        headers: const {
          'Accept': 'application/json',
          'Content-Type': 'application/json',
        },
        validateStatus: (status) => status != null && status >= 200 && status < 300,
      ),
    );

    if (session != null) {
      dio.interceptors.add(AuthInterceptor(session: session, dio: dio));
    }
    dio.interceptors.addAll(extraInterceptors);
    return dio;
  }
}
