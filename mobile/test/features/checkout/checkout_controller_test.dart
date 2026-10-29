import 'dart:async';

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
import 'package:aptech4_mobile/features/auth/presentation/auth_providers.dart';
import 'package:aptech4_mobile/features/cart/data/cart_api.dart';
import 'package:aptech4_mobile/features/cart/presentation/cart_providers.dart';
import 'package:aptech4_mobile/features/checkout/data/checkout_api.dart';
import 'package:aptech4_mobile/features/checkout/presentation/checkout_providers.dart';

Map<String, dynamic> cartWithItem() => {
      'id': 'cart-1',
      'items': [
        {
          'id': 'item-1',
          'productId': 'prod-1',
          'productName': 'Tea',
          'imageUrl': null,
          'quantity': 1,
          'unitPrice': 10000,
          'lineSubtotal': 10000,
          'currency': 'VND',
          'available': true,
          'productActive': true,
        },
      ],
      'subtotal': 10000,
      'currency': 'VND',
      'hasUnavailableItems': false,
    };

Map<String, dynamic> addressJson() => {
      'id': 'addr-1',
      'label': 'Home',
      'recipientName': 'Martin',
      'line1': '1 Main',
      'line2': null,
      'city': 'HN',
      'stateRegion': null,
      'postalCode': null,
      'countryCode': 'VN',
      'phone': null,
      'defaultAddress': true,
    };

Map<String, dynamic> checkoutResultJson() => {
      'orderId': 'ord-1',
      'orderStatus': 'PAID',
      'subtotalAmount': 10000,
      'shippingAmount': 0,
      'totalAmount': 10000,
      'currency': 'VND',
      'placedAt': '2026-10-28T00:00:00Z',
      'items': [
        {
          'productId': 'prod-1',
          'productSku': 'SKU-1',
          'productName': 'Tea',
          'unitPrice': 10000,
          'quantity': 1,
          'lineTotal': 10000,
        },
      ],
      'payment': {
        'paymentId': 'pay-1',
        'provider': 'MOCK',
        'status': 'PAID',
        'amount': 10000,
        'currency': 'VND',
        'providerReference': 'mock-1',
        'failureReason': null,
      },
      'cartCleared': true,
    };

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

    container = ProviderContainer(
      overrides: [
        dioProvider.overrideWithValue(dio),
        tokenStoreProvider.overrideWithValue(store),
        sessionCoordinatorProvider.overrideWithValue(session),
        authRepositoryProvider.overrideWithValue(
          AuthRepository(authApi: bareApi, session: session),
        ),
        cartApiProvider.overrideWithValue(CartApi(dio)),
        addressApiProvider.overrideWithValue(AddressApi(dio)),
        checkoutApiProvider.overrideWithValue(CheckoutApi(dio)),
      ],
    );
  });

  tearDown(() => container.dispose());

  test('cannot submit without address / empty cart', () async {
    adapter
      ..onGet('/cart', (server) => server.reply(200, {
            'id': 'cart-1',
            'items': [],
            'subtotal': 0,
            'currency': 'VND',
            'hasUnavailableItems': false,
          }))
      ..onGet('/addresses', (server) => server.reply(200, []));

    final ctrl = container.read(checkoutControllerProvider.notifier);
    await ctrl.load();
    expect(container.read(checkoutControllerProvider).canSubmit, isFalse);
    expect(await ctrl.submit(), isNull);
  });

  test('submit success returns backend result only', () async {
    var cartReads = 0;
    adapter
      ..onGet('/cart', (server) {
        cartReads += 1;
        if (cartReads == 1) {
          return server.reply(200, cartWithItem());
        }
        return server.reply(200, {
          'id': 'cart-1',
          'items': [],
          'subtotal': 0,
          'currency': 'VND',
          'hasUnavailableItems': false,
        });
      })
      ..onGet('/addresses', (server) => server.reply(200, [addressJson()]))
      ..onPost(
        '/checkout',
        (server) => server.reply(201, checkoutResultJson()),
        data: Matchers.any,
      );

    final ctrl = container.read(checkoutControllerProvider.notifier);
    await ctrl.load();
    expect(container.read(checkoutControllerProvider).canSubmit, isTrue);
    final result = await ctrl.submit();
    expect(result, isNotNull);
    expect(result!.orderId, 'ord-1');
    expect(result.orderStatus, 'PAID');
    expect(result.cartCleared, isTrue);
  });

  test('duplicate submit while in flight is ignored', () async {
    final gate = Completer<void>();
    var cartReads = 0;
    adapter
      ..onGet('/cart', (server) {
        cartReads += 1;
        if (cartReads == 1) {
          return server.reply(200, cartWithItem());
        }
        return server.reply(200, {
          'id': 'cart-1',
          'items': [],
          'subtotal': 0,
          'currency': 'VND',
          'hasUnavailableItems': false,
        });
      })
      ..onGet('/addresses', (server) => server.reply(200, [addressJson()]))
      ..onPost(
        '/checkout',
        (server) async {
          await gate.future;
          return server.reply(201, checkoutResultJson());
        },
        data: Matchers.any,
      );

    final ctrl = container.read(checkoutControllerProvider.notifier);
    await ctrl.load();
    expect(container.read(checkoutControllerProvider).canSubmit, isTrue);

    final first = ctrl.submit();
    // Yield so the first call hits the in-flight lock before the second starts.
    await Future<void>.delayed(Duration.zero);
    expect(container.read(checkoutControllerProvider).submitting, isTrue);
    final second = await ctrl.submit();
    expect(second, isNull);

    gate.complete();
    final result = await first;
    expect(result, isNotNull);
    expect(result!.orderId, 'ord-1');
  });

  test('API conflict maps message without inventing success', () async {
    adapter
      ..onGet('/cart', (server) => server.reply(200, cartWithItem()))
      ..onGet('/addresses', (server) => server.reply(200, [addressJson()]))
      ..onPost(
        '/checkout',
        (server) => server.reply(409, {
          'status': 409,
          'error': 'Conflict',
          'message': 'Insufficient stock',
        }),
        data: Matchers.any,
      );

    final ctrl = container.read(checkoutControllerProvider.notifier);
    await ctrl.load();
    final result = await ctrl.submit();
    expect(result, isNull);
    expect(
      container.read(checkoutControllerProvider).outcomeMessage,
      'Insufficient stock',
    );
    expect(container.read(checkoutControllerProvider).unknownOutcome, isFalse);
  });
}
