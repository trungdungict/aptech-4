import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:aptech4_mobile/app/app_router.dart';
import 'package:aptech4_mobile/core/config/api_config.dart';
import 'package:aptech4_mobile/core/network/dio_client.dart';
import 'package:aptech4_mobile/core/storage/secure_token_store.dart';
import 'package:aptech4_mobile/features/auth/data/auth_api.dart';
import 'package:aptech4_mobile/features/auth/data/auth_repository.dart';
import 'package:aptech4_mobile/features/auth/data/session_coordinator.dart';
import 'package:aptech4_mobile/features/auth/domain/auth_session.dart';
import 'package:aptech4_mobile/features/auth/presentation/auth_controller.dart';
import 'package:aptech4_mobile/features/auth/presentation/auth_providers.dart';
import 'package:aptech4_mobile/features/catalog/data/catalog_api.dart';
import 'package:aptech4_mobile/features/catalog/domain/catalog_models.dart';
import 'package:aptech4_mobile/features/catalog/presentation/catalog_providers.dart';

class _FakeAuthController extends AuthController {
  _FakeAuthController(super.ref, AuthState initial) {
    state = initial;
  }

  @override
  Future<void> restore() async {}
}

class _FakeCatalogApi implements CatalogApi {
  @override
  dynamic noSuchMethod(Invocation invocation) => throw UnimplementedError();

  @override
  Future<List<Category>> listCategories() async => const [];

  @override
  Future<PageResponse<Product>> listProducts(ProductListParams params) async {
    return const PageResponse(
      items: [],
      page: 0,
      size: 20,
      totalElements: 0,
      totalPages: 0,
      first: true,
      last: true,
    );
  }

  @override
  Future<Product> getProduct(String id) async {
    throw UnimplementedError();
  }
}

void main() {
  testWidgets('unauthenticated /cart redirects to login', (tester) async {
    final store = InMemoryTokenStore();
    final dio = DioClientFactory.create(
      config: ApiConfig(baseUrl: 'http://localhost/api/v1'),
    );
    final session = SessionCoordinator(authApi: AuthApi(dio), tokenStore: store);
    final repository = AuthRepository(authApi: AuthApi(dio), session: session);

    final container = ProviderContainer(
      overrides: [
        dioProvider.overrideWithValue(dio),
        tokenStoreProvider.overrideWithValue(store),
        sessionCoordinatorProvider.overrideWithValue(session),
        authRepositoryProvider.overrideWithValue(repository),
        catalogApiProvider.overrideWithValue(_FakeCatalogApi()),
        authControllerProvider.overrideWith(
          (ref) => _FakeAuthController(ref, const AuthState(booting: false)),
        ),
      ],
    );
    addTearDown(container.dispose);

    final router = container.read(goRouterProvider);
    await tester.pumpWidget(
      UncontrolledProviderScope(
        container: container,
        child: MaterialApp.router(routerConfig: router),
      ),
    );
    await tester.pump();

    router.go('/cart');
    await tester.pumpAndSettle();

    expect(router.routeInformationProvider.value.uri.path, '/login');
  });

  testWidgets('authenticated user leaves login to products', (tester) async {
    final store = InMemoryTokenStore();
    final dio = DioClientFactory.create(
      config: ApiConfig(baseUrl: 'http://localhost/api/v1'),
    );
    final session = SessionCoordinator(authApi: AuthApi(dio), tokenStore: store);
    await session.applySession(
      AuthSession(
        accessToken: 'a',
        refreshToken: 'r',
        tokenType: 'Bearer',
        expiresIn: 900,
        user: const AuthUser(
          id: 'u1',
          email: 'c@test.local',
          fullName: 'C',
          roles: ['CUSTOMER'],
        ),
      ),
    );
    final repository = AuthRepository(authApi: AuthApi(dio), session: session);

    final container = ProviderContainer(
      overrides: [
        dioProvider.overrideWithValue(dio),
        tokenStoreProvider.overrideWithValue(store),
        sessionCoordinatorProvider.overrideWithValue(session),
        authRepositoryProvider.overrideWithValue(repository),
        catalogApiProvider.overrideWithValue(_FakeCatalogApi()),
        authControllerProvider.overrideWith(
          (ref) => _FakeAuthController(
            ref,
            AuthState(booting: false, user: session.user),
          ),
        ),
      ],
    );
    addTearDown(container.dispose);

    final router = container.read(goRouterProvider);
    await tester.pumpWidget(
      UncontrolledProviderScope(
        container: container,
        child: MaterialApp.router(routerConfig: router),
      ),
    );
    router.go('/login');
    await tester.pumpAndSettle();
    expect(router.routeInformationProvider.value.uri.path, '/products');
  });
}
