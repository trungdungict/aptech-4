import 'package:dio/dio.dart';

import 'package:aptech4_mobile/features/auth/data/auth_api.dart';
import 'package:aptech4_mobile/features/catalog/domain/catalog_models.dart';

class CatalogApi {
  CatalogApi(this._dio);

  final Dio _dio;

  Future<PageResponse<Product>> listProducts(ProductListParams params) async {
    try {
      final response = await _dio.get<Map<String, dynamic>>(
        '/products',
        queryParameters: params.toQuery(),
      );
      return PageResponse.fromJson(response.data!, Product.fromJson);
    } on DioException catch (e) {
      throw mapDioException(e);
    }
  }

  Future<Product> getProduct(String id) async {
    try {
      final response = await _dio.get<Map<String, dynamic>>('/products/$id');
      return Product.fromJson(response.data!);
    } on DioException catch (e) {
      throw mapDioException(e);
    }
  }

  Future<List<Category>> listCategories() async {
    try {
      final response = await _dio.get<List<dynamic>>('/categories');
      final data = response.data ?? const [];
      return data
          .whereType<Map<String, dynamic>>()
          .map(Category.fromJson)
          .toList(growable: false);
    } on DioException catch (e) {
      throw mapDioException(e);
    }
  }
}
