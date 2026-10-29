import 'package:dio/dio.dart';

import 'package:aptech4_mobile/features/auth/data/auth_api.dart';
import 'package:aptech4_mobile/features/checkout/domain/checkout_models.dart';

class AddressApi {
  AddressApi(this._dio);

  final Dio _dio;

  Future<List<Address>> listAddresses() async {
    try {
      final response = await _dio.get<List<dynamic>>('/addresses');
      final data = response.data ?? const [];
      return data
          .whereType<Map<String, dynamic>>()
          .map(Address.fromJson)
          .toList(growable: false);
    } on DioException catch (e) {
      throw mapDioException(e);
    }
  }

  Future<Address> createAddress(AddressCreatePayload payload) async {
    try {
      final response = await _dio.post<Map<String, dynamic>>(
        '/addresses',
        data: payload.toJson(),
      );
      return Address.fromJson(response.data!);
    } on DioException catch (e) {
      throw mapDioException(e);
    }
  }
}

class CheckoutApi {
  CheckoutApi(this._dio);

  final Dio _dio;

  Future<CheckoutResult> checkout({
    required String idempotencyKey,
    required String addressId,
  }) async {
    try {
      final response = await _dio.post<Map<String, dynamic>>(
        '/checkout',
        data: {
          'idempotencyKey': idempotencyKey,
          'addressId': addressId,
        },
      );
      return CheckoutResult.fromJson(response.data!);
    } on DioException catch (e) {
      throw mapDioException(e);
    }
  }
}
