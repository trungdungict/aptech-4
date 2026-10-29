import 'package:dio/dio.dart';

import 'package:aptech4_mobile/features/auth/data/auth_api.dart';
import 'package:aptech4_mobile/features/cart/domain/cart_models.dart';

class CartApi {
  CartApi(this._dio);

  final Dio _dio;

  Future<Cart> getCart() async {
    try {
      final response = await _dio.get<Map<String, dynamic>>('/cart');
      return Cart.fromJson(response.data!);
    } on DioException catch (e) {
      throw mapDioException(e);
    }
  }

  Future<Cart> addItem({required String productId, required int quantity}) async {
    try {
      final response = await _dio.post<Map<String, dynamic>>(
        '/cart/items',
        data: {'productId': productId, 'quantity': quantity},
      );
      return Cart.fromJson(response.data!);
    } on DioException catch (e) {
      throw mapDioException(e);
    }
  }

  Future<Cart> updateItem({required String itemId, required int quantity}) async {
    try {
      final response = await _dio.patch<Map<String, dynamic>>(
        '/cart/items/$itemId',
        data: {'quantity': quantity},
      );
      return Cart.fromJson(response.data!);
    } on DioException catch (e) {
      throw mapDioException(e);
    }
  }

  Future<Cart> removeItem(String itemId) async {
    try {
      final response = await _dio.delete<Map<String, dynamic>>('/cart/items/$itemId');
      return Cart.fromJson(response.data!);
    } on DioException catch (e) {
      throw mapDioException(e);
    }
  }
}
