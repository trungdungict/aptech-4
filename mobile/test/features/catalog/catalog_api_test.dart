import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http_mock_adapter/http_mock_adapter.dart';

import 'package:aptech4_mobile/core/config/api_config.dart';
import 'package:aptech4_mobile/core/errors/api_exception.dart';
import 'package:aptech4_mobile/core/network/dio_client.dart';
import 'package:aptech4_mobile/features/catalog/data/catalog_api.dart';
import 'package:aptech4_mobile/features/catalog/domain/catalog_models.dart';

const _defaultProductQuery = {
  'page': 0,
  'size': 20,
  'sort': 'name,asc',
};

void main() {
  late Dio dio;
  late DioAdapter adapter;
  late CatalogApi api;

  setUp(() {
    dio = DioClientFactory.create(config: ApiConfig(baseUrl: 'http://localhost/api/v1'));
    adapter = DioAdapter(dio: dio);
    dio.httpClientAdapter = adapter;
    api = CatalogApi(dio);
  });

  test('listProducts success parses PageResponse', () async {
    adapter.onGet(
      '/products',
      (server) => server.reply(200, {
        'items': [
          {
            'id': 'p1',
            'sku': 'SKU-1',
            'name': 'Tea',
            'description': null,
            'unitPrice': 25000,
            'currency': 'VND',
            'categoryId': 'c1',
            'categoryName': 'Drinks',
            'active': true,
            'imageUrl': null,
            'inStock': true,
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

    final page = await api.listProducts(const ProductListParams());
    expect(page.items, hasLength(1));
    expect(page.items.first.name, 'Tea');
    expect(page.totalElements, 1);
    expect(page.last, isTrue);
  });

  test('listProducts empty page', () async {
    adapter.onGet(
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

    final page = await api.listProducts(const ProductListParams());
    expect(page.items, isEmpty);
    expect(page.totalElements, 0);
  });

  test('listProducts maps API error', () async {
    adapter.onGet(
      '/products',
      (server) => server.reply(400, {
        'status': 400,
        'error': 'Bad Request',
        'message': 'Invalid sort',
        'path': '/api/v1/products',
        'details': [],
      }),
      queryParameters: const {
        'page': 0,
        'size': 20,
        'sort': 'nope',
      },
    );

    expect(
      () => api.listProducts(const ProductListParams(sort: 'nope')),
      throwsA(
        isA<ApiException>().having((e) => e.message, 'message', 'Invalid sort'),
      ),
    );
  });

  test('getProduct 404 maps to ApiException', () async {
    adapter.onGet(
      '/products/missing',
      (server) => server.reply(404, {
        'status': 404,
        'error': 'Not Found',
        'message': 'Product not found',
      }),
    );

    expect(
      () => api.getProduct('missing'),
      throwsA(isA<ApiException>().having((e) => e.status, 'status', 404)),
    );
  });

  test('malformed product list items are skipped safely', () {
    final page = PageResponse.fromJson(
      {
        'items': [
          'bad',
          {
            'id': 'p2',
            'sku': 'S2',
            'name': 'Ok',
            'unitPrice': 1,
            'currency': 'VND',
            'categoryId': 'c1',
            'categoryName': 'C',
            'active': true,
          },
        ],
        'page': 0,
        'size': 20,
        'totalElements': 1,
        'totalPages': 1,
        'first': true,
        'last': true,
      },
      Product.fromJson,
    );
    expect(page.items, hasLength(1));
    expect(page.items.first.id, 'p2');
  });
}
