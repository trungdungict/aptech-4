import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http_mock_adapter/http_mock_adapter.dart';

import 'package:aptech4_mobile/core/config/api_config.dart';
import 'package:aptech4_mobile/core/network/dio_client.dart';
import 'package:aptech4_mobile/features/auth/presentation/auth_providers.dart';
import 'package:aptech4_mobile/features/catalog/data/catalog_api.dart';
import 'package:aptech4_mobile/features/catalog/presentation/catalog_providers.dart';

const _defaultProductQuery = {
  'page': 0,
  'size': 20,
  'sort': 'name,asc',
};

void main() {
  late ProviderContainer container;
  late DioAdapter adapter;

  setUp(() {
    final dio = DioClientFactory.create(
      config: ApiConfig(baseUrl: 'http://localhost/api/v1'),
    );
    adapter = DioAdapter(dio: dio);
    dio.httpClientAdapter = adapter;
    container = ProviderContainer(
      overrides: [
        dioProvider.overrideWithValue(dio),
        catalogApiProvider.overrideWithValue(CatalogApi(dio)),
      ],
    );
  });

  tearDown(() => container.dispose());

  test('loading → success', () async {
    adapter
      ..onGet('/categories', (server) => server.reply(200, []))
      ..onGet(
        '/products',
        (server) => server.reply(200, {
          'items': [
            {
              'id': 'p1',
              'sku': 'S1',
              'name': 'Tea',
              'unitPrice': 1,
              'currency': 'VND',
              'categoryId': 'c1',
              'categoryName': 'Drinks',
              'active': true,
            },
          ],
          'page': 0,
          'size': 20,
          'totalElements': 1,
          'totalPages': 1,
          'first': true,
          'last': true,
        }),
        queryParameters: _defaultProductQuery,
      );

    await container.read(catalogListControllerProvider.notifier).refresh();
    final state = container.read(catalogListControllerProvider);
    expect(state.loading, isFalse);
    expect(state.items, hasLength(1));
    expect(state.errorMessage, isNull);
  });

  test('error state', () async {
    adapter
      ..onGet('/categories', (server) => server.reply(200, []))
      ..onGet(
        '/products',
        (server) => server.reply(500, {
          'status': 500,
          'error': 'Error',
          'message': 'Boom',
        }),
        queryParameters: _defaultProductQuery,
      );

    await container.read(catalogListControllerProvider.notifier).refresh();
    final state = container.read(catalogListControllerProvider);
    expect(state.errorMessage, 'Boom');
    expect(state.items, isEmpty);
  });

  test('empty state', () async {
    adapter
      ..onGet('/categories', (server) => server.reply(200, []))
      ..onGet(
        '/products',
        (server) => server.reply(200, {
          'items': [],
          'page': 0,
          'size': 20,
          'totalElements': 0,
          'totalPages': 0,
          'first': true,
          'last': true,
        }),
        queryParameters: _defaultProductQuery,
      );

    await container.read(catalogListControllerProvider.notifier).refresh();
    final state = container.read(catalogListControllerProvider);
    expect(state.isEmpty, isTrue);
  });
}
