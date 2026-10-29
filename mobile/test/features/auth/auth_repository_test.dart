import 'dart:async';

import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http_mock_adapter/http_mock_adapter.dart';

import 'package:aptech4_mobile/core/errors/api_exception.dart';
import 'package:aptech4_mobile/core/network/dio_client.dart';
import 'package:aptech4_mobile/core/storage/secure_token_store.dart';
import 'package:aptech4_mobile/features/auth/data/auth_api.dart';
import 'package:aptech4_mobile/features/auth/data/auth_repository.dart';
import 'package:aptech4_mobile/features/auth/data/session_coordinator.dart';
import 'package:aptech4_mobile/features/auth/domain/auth_session.dart';
import 'package:aptech4_mobile/core/config/api_config.dart';
import 'package:aptech4_mobile/core/network/auth_interceptor.dart';

Map<String, dynamic> authResponseJson({
  String access = 'access-1',
  String refresh = 'refresh-1',
  String email = 'customer@aptech4.local',
}) {
  return {
    'accessToken': access,
    'refreshToken': refresh,
    'tokenType': 'Bearer',
    'expiresIn': 900,
    'user': {
      'id': '11111111-1111-1111-1111-111111111111',
      'email': email,
      'fullName': 'Demo Customer',
      'roles': ['CUSTOMER'],
    },
  };
}

void main() {
  late InMemoryTokenStore store;
  late Dio bareDio;
  late DioAdapter bareAdapter;
  late AuthApi bareApi;
  late SessionCoordinator session;
  late Dio authedDio;
  late DioAdapter authedAdapter;
  late AuthRepository repository;

  setUp(() {
    store = InMemoryTokenStore();
    final config = ApiConfig(baseUrl: 'http://localhost/api/v1');

    bareDio = DioClientFactory.create(config: config);
    bareAdapter = DioAdapter(dio: bareDio);
    bareDio.httpClientAdapter = bareAdapter;
    bareApi = AuthApi(bareDio);
    session = SessionCoordinator(authApi: bareApi, tokenStore: store);

    authedDio = DioClientFactory.create(config: config, session: session);
    authedAdapter = DioAdapter(dio: authedDio);
    // Replace adapter after interceptor attached.
    authedDio.httpClientAdapter = authedAdapter;
    repository = AuthRepository(authApi: AuthApi(authedDio), session: session);
  });

  group('login/register', () {
    test('login success stores refresh securely and access in memory', () async {
      bareAdapter.onPost(
        '/auth/login',
        (server) => server.reply(200, authResponseJson()),
        data: Matchers.any,
      );
      // login goes through authed Dio
      authedAdapter.onPost(
        '/auth/login',
        (server) => server.reply(200, authResponseJson()),
        data: Matchers.any,
      );

      final sessionResult = await repository.login(
        const LoginPayload(email: 'customer@aptech4.local', password: 'secret123'),
      );

      expect(sessionResult.accessToken, 'access-1');
      expect(session.accessToken, 'access-1');
      expect(await store.readRefreshToken(), 'refresh-1');
      expect(session.user?.email, 'customer@aptech4.local');
    });

    test('login failure maps ApiException and does not persist tokens', () async {
      authedAdapter.onPost(
        '/auth/login',
        (server) => server.reply(401, {
          'timestamp': '2026-10-28T00:00:00Z',
          'status': 401,
          'error': 'Unauthorized',
          'message': 'Invalid email or password',
          'path': '/api/v1/auth/login',
          'details': <String>[],
        }),
        data: Matchers.any,
      );

      await expectLater(
        repository.login(const LoginPayload(email: 'a@b.co', password: 'x')),
        throwsA(
          isA<ApiException>()
              .having((e) => e.status, 'status', 401)
              .having((e) => e.message, 'message', 'Invalid email or password'),
        ),
      );
      expect(session.accessToken, isNull);
      expect(await store.readRefreshToken(), isNull);
    });

    test('register success (201) applies session', () async {
      authedAdapter.onPost(
        '/auth/register',
        (server) => server.reply(201, authResponseJson(access: 'a2', refresh: 'r2')),
        data: Matchers.any,
      );

      final result = await repository.register(
        const RegisterPayload(
          email: 'new@aptech4.local',
          password: 'password1',
          fullName: 'New User',
        ),
      );
      expect(result.refreshToken, 'r2');
      expect(await store.readRefreshToken(), 'r2');
    });
  });

  group('token store', () {
    test('read write delete refresh and user snapshot', () async {
      const user = AuthUser(
        id: '1',
        email: 'a@b.co',
        fullName: 'A',
        roles: ['CUSTOMER'],
      );
      await store.writeRefreshToken('rt');
      await store.writeUserSnapshot(user);
      expect(await store.readRefreshToken(), 'rt');
      expect((await store.readUserSnapshot())?.email, 'a@b.co');
      await store.clearAll();
      expect(await store.readRefreshToken(), isNull);
      expect(await store.readUserSnapshot(), isNull);
    });
  });

  group('refresh', () {
    test('refresh success rotates refresh token', () async {
      await store.writeRefreshToken('old-refresh');
      bareAdapter.onPost(
        '/auth/refresh',
        (server) => server.reply(
          200,
          authResponseJson(access: 'new-access', refresh: 'new-refresh'),
        ),
        data: Matchers.any,
      );

      final token = await session.refreshAccessTokenSingleFlight();
      expect(token, 'new-access');
      expect(session.accessToken, 'new-access');
      expect(await store.readRefreshToken(), 'new-refresh');
    });

    test('refresh failure clears session', () async {
      await store.writeRefreshToken('bad');
      bareAdapter.onPost(
        '/auth/refresh',
        (server) => server.reply(401, {
          'status': 401,
          'error': 'Unauthorized',
          'message': 'Invalid refresh token',
          'path': '/api/v1/auth/refresh',
          'details': <String>[],
        }),
        data: Matchers.any,
      );

      final token = await session.refreshAccessTokenSingleFlight();
      expect(token, isNull);
      expect(session.accessToken, isNull);
      expect(await store.readRefreshToken(), isNull);
    });

    test('concurrent refresh shares one in-flight call', () async {
      await store.writeRefreshToken('old');
      var refreshCalls = 0;
      final gate = Completer<void>();

      bareAdapter.onPost(
        '/auth/refresh',
        (server) async {
          refreshCalls++;
          await gate.future;
          return server.reply(
            200,
            authResponseJson(access: 'shared-access', refresh: 'shared-refresh'),
          );
        },
        data: Matchers.any,
      );

      final a = session.refreshAccessTokenSingleFlight();
      final b = session.refreshAccessTokenSingleFlight();
      gate.complete();
      final results = await Future.wait([a, b]);

      expect(results, ['shared-access', 'shared-access']);
      expect(refreshCalls, 1);
      expect(await store.readRefreshToken(), 'shared-refresh');
    });
  });

  group('logout', () {
    test('clears local session even when server logout fails', () async {
      await session.applySession(
        AuthSession.fromJson(authResponseJson()),
      );
      authedAdapter.onPost(
        '/auth/logout',
        (server) => server.reply(500, {
          'status': 500,
          'error': 'Error',
          'message': 'boom',
          'path': '/api/v1/auth/logout',
          'details': <String>[],
        }),
        data: Matchers.any,
      );

      await repository.logout();
      expect(session.accessToken, isNull);
      expect(await store.readRefreshToken(), isNull);
    });
  });

  group('error mapping', () {
    test('timeout maps to ApiException.timeout', () {
      final mapped = mapDioException(
        DioException(
          requestOptions: RequestOptions(path: '/x'),
          type: DioExceptionType.receiveTimeout,
        ),
      );
      expect(mapped.error, 'Timeout');
    });

    test('connection error maps to network', () {
      final mapped = mapDioException(
        DioException(
          requestOptions: RequestOptions(path: '/x'),
          type: DioExceptionType.connectionError,
        ),
      );
      expect(mapped.error, 'Network Error');
    });
  });

  group('auth interceptor guards', () {
    test('skips refresh for auth endpoints and skip flag', () {
      expect(authedDio.interceptors.whereType<AuthInterceptor>().length, 1);

      final login = RequestOptions(
        path: AuthApi.loginPath,
        extra: {AuthRequestFlags.skipAuthRefresh: true},
      );
      final orders = RequestOptions(path: '/orders');
      final refresh = RequestOptions(path: AuthApi.refreshPath);

      expect(AuthInterceptor.shouldSkipRefresh(login), isTrue);
      expect(AuthInterceptor.shouldSkipRefresh(refresh), isTrue);
      expect(AuthInterceptor.shouldSkipRefresh(orders), isFalse);
    });

    test('login 401 does not clear a pre-existing refresh via recursion', () async {
      await store.writeRefreshToken('keep-me');
      authedAdapter.onPost(
        '/auth/login',
        (server) => server.reply(401, {
          'status': 401,
          'error': 'Unauthorized',
          'message': 'Invalid email or password',
          'path': '/api/v1/auth/login',
          'details': <String>[],
        }),
        data: Matchers.any,
      );

      await expectLater(
        repository.login(const LoginPayload(email: 'a@b.co', password: 'bad')),
        throwsA(isA<ApiException>()),
      );
      expect(await store.readRefreshToken(), 'keep-me');
      expect(session.accessToken, isNull);
    });
  });
}
