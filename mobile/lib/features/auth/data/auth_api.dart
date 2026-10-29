import 'package:dio/dio.dart';

import 'package:aptech4_mobile/core/errors/api_exception.dart';
import 'package:aptech4_mobile/features/auth/domain/auth_session.dart';

/// Thin Dio wrapper for `/api/v1/auth/*` (paths relative to API base URL).
class AuthApi {
  AuthApi(this._dio);

  final Dio _dio;

  static const loginPath = '/auth/login';
  static const registerPath = '/auth/register';
  static const refreshPath = '/auth/refresh';
  static const logoutPath = '/auth/logout';

  Future<AuthSession> login(LoginPayload payload) async {
    final response = await _dio.post<Map<String, dynamic>>(
      loginPath,
      data: payload.toJson(),
      options: Options(extra: const {AuthRequestFlags.skipAuthRefresh: true}),
    );
    return AuthSession.fromJson(response.data!);
  }

  Future<AuthSession> register(RegisterPayload payload) async {
    final response = await _dio.post<Map<String, dynamic>>(
      registerPath,
      data: payload.toJson(),
      options: Options(extra: const {AuthRequestFlags.skipAuthRefresh: true}),
    );
    return AuthSession.fromJson(response.data!);
  }

  Future<AuthSession> refresh(String refreshToken) async {
    final response = await _dio.post<Map<String, dynamic>>(
      refreshPath,
      data: {'refreshToken': refreshToken},
      options: Options(extra: const {AuthRequestFlags.skipAuthRefresh: true}),
    );
    return AuthSession.fromJson(response.data!);
  }

  /// Backend returns 204; idempotent for unknown tokens.
  Future<void> logout(String refreshToken) async {
    await _dio.post<void>(
      logoutPath,
      data: {'refreshToken': refreshToken},
      options: Options(extra: const {AuthRequestFlags.skipAuthRefresh: true}),
    );
  }
}

abstract final class AuthRequestFlags {
  static const skipAuthRefresh = 'skipAuthRefresh';
}

ApiException mapDioException(DioException error) {
  switch (error.type) {
    case DioExceptionType.connectionTimeout:
    case DioExceptionType.sendTimeout:
    case DioExceptionType.receiveTimeout:
    case DioExceptionType.transformTimeout:
      return ApiException.timeout('Request timed out');
    case DioExceptionType.connectionError:
      return ApiException.network('Unable to reach the API');
    case DioExceptionType.badResponse:
      final status = error.response?.statusCode ?? 0;
      final data = error.response?.data;
      Map<String, dynamic>? json;
      if (data is Map<String, dynamic>) {
        json = data;
      }
      return ApiException.fromJson(status, json);
    case DioExceptionType.cancel:
      return ApiException.network('Request cancelled');
    case DioExceptionType.badCertificate:
      return ApiException.network('TLS certificate error');
    case DioExceptionType.unknown:
      return ApiException.network(error.message ?? 'Unknown network error');
  }
}
