import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http_mock_adapter/http_mock_adapter.dart';

import 'package:aptech4_mobile/core/config/api_config.dart';
import 'package:aptech4_mobile/core/network/dio_client.dart';
import 'package:aptech4_mobile/core/storage/secure_token_store.dart';
import 'package:aptech4_mobile/features/auth/data/auth_api.dart';
import 'package:aptech4_mobile/features/auth/data/auth_repository.dart';
import 'package:aptech4_mobile/features/auth/data/session_coordinator.dart';
import 'package:aptech4_mobile/features/auth/domain/auth_session.dart';
import 'package:aptech4_mobile/features/auth/presentation/auth_controller.dart';
import 'package:aptech4_mobile/features/auth/presentation/auth_providers.dart';
import 'package:aptech4_mobile/features/cart/data/cart_api.dart';
import 'package:aptech4_mobile/features/cart/presentation/cart_providers.dart';

Map<String, dynamic> cartJson({
  List<Map<String, dynamic>>? items,
  bool unavailable = false,
}) {
  return {
    'id': 'cart-1',
    'items': items ??
        [
          {
            'id': 'item-1',
            'productId': 'prod-1',
            'productName': 'Tea',
            'imageUrl': null,
            'quantity': 2,
            'unitPrice': 10000,
            'lineSubtotal': 20000,
            'currency': 'VND',
            'available': !unavailable,
            'productActive': true,
          },
        ],
    'subtotal': unavailable ? 0 : 20000,
    'currency': 'VND',
    'hasUnavailableItems': unavailable,
  };
}

void main() {
  late ProviderContainer container;
  late Dio dio;
  late DioAdapter adapter;

  setUp(() async {
    final store = InMemoryTokenStore();
    final config = ApiConfig(baseUrl: 'http://localhost/api/v1');
    dio = DioClientFactory.create(config: config);
    adapter = DioAdapter(dio: dio);
    dio.httpClientAdapter = adapter;

    final bareApi = AuthApi(dio);
    final session = SessionCoordinator(authApi: bareApi, tokenStore: store);
    await session.applySession(
      AuthSession(
        accessToken: 'access',
        refreshToken: 'refresh',
        tokenType: 'Bearer',
        expiresIn: 900,
        user: const AuthUser(
          id: 'u1',
          email: 'c@test.local',
          fullName: 'Customer',
          roles: ['CUSTOMER'],
        ),
      ),
    );
    final repository = AuthRepository(authApi: bareApi, session: session);

    container = ProviderContainer(
      overrides: [
        dioProvider.overrideWithValue(dio),
        tokenStoreProvider.overrideWithValue(store),
        sessionCoordinatorProvider.overrideWithValue(session),
        authRepositoryProvider.overrideWithValue(repository),
        cartApiProvider.overrideWithValue(CartApi(dio)),
      ],
    );
  });

  tearDown(() => container.dispose());

  test('load success and empty cart', () async {
    adapter.onGet('/cart', (server) => server.reply(200, cartJson(items: [])));
    final ctrl = container.read(cartControllerProvider.notifier);
    await ctrl.load();
    final state = container.read(cartControllerProvider);
    expect(state.isEmpty, isTrue);
    expect(state.errorMessage, isNull);
  });

  test('update quantity and remove item', () async {
    adapter
      ..onGet('/cart', (server) => server.reply(200, cartJson()))
      ..onPatch(
        '/cart/items/item-1',
        (server) => server.reply(
          200,
          cartJson(
            items: [
              {
                'id': 'item-1',
                'productId': 'prod-1',
                'productName': 'Tea',
                'imageUrl': null,
                'quantity': 3,
                'unitPrice': 10000,
                'lineSubtotal': 30000,
                'currency': 'VND',
                'available': true,
                'productActive': true,
              },
            ],
          ),
        ),
        data: Matchers.any,
      )
      ..onDelete(
        '/cart/items/item-1',
        (server) => server.reply(200, cartJson(items: [])),
      );

    final ctrl = container.read(cartControllerProvider.notifier);
    await ctrl.load();
    await ctrl.updateQuantity(itemId: 'item-1', quantity: 3);
    expect(container.read(cartControllerProvider).cart!.items.first.quantity, 3);

    await ctrl.removeItem('item-1');
    expect(container.read(cartControllerProvider).isEmpty, isTrue);
  });

  test('duplicate mutation while pending is ignored', () async {
    adapter
      ..onGet('/cart', (server) => server.reply(200, cartJson()))
      ..onPatch(
        '/cart/items/item-1',
        (server) async {
          await Future<void>.delayed(const Duration(milliseconds: 50));
          return server.reply(200, cartJson());
        },
        data: Matchers.any,
      );

    final ctrl = container.read(cartControllerProvider.notifier);
    await ctrl.load();
    final first = ctrl.updateQuantity(itemId: 'item-1', quantity: 2);
    final second = ctrl.updateQuantity(itemId: 'item-1', quantity: 4);
    await Future.wait([first, second]);
    expect(container.read(cartControllerProvider).mutating, isFalse);
  });

  test('401 clears session via auth controller', () async {
    adapter
      ..onPost(
        '/auth/refresh',
        (server) => server.reply(200, {
          'accessToken': 'access',
          'refreshToken': 'refresh',
          'tokenType': 'Bearer',
          'expiresIn': 900,
          'user': {
            'id': 'u1',
            'email': 'c@test.local',
            'fullName': 'Customer',
            'roles': ['CUSTOMER'],
          },
        }),
        data: Matchers.any,
      )
      ..onGet(
        '/cart',
        (server) => server.reply(401, {
          'status': 401,
          'error': 'Unauthorized',
          'message': 'Expired',
        }),
      )
      ..onPost(
        '/auth/logout',
        (server) => server.reply(204, null),
        data: Matchers.any,
      );

    final auth = container.read(authControllerProvider.notifier);
    await auth.restore();
    expect(container.read(authControllerProvider).isAuthenticated, isTrue);
    final ctrl = container.read(cartControllerProvider.notifier);
    await ctrl.load();
    expect(container.read(authControllerProvider).isAuthenticated, isFalse);
    expect(container.read(sessionCoordinatorProvider).accessToken, isNull);
  });
}
