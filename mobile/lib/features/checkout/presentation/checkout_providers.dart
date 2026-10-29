import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:uuid/uuid.dart';

import 'package:aptech4_mobile/core/errors/api_exception.dart';
import 'package:aptech4_mobile/features/auth/presentation/auth_controller.dart';
import 'package:aptech4_mobile/features/auth/presentation/auth_providers.dart';
import 'package:aptech4_mobile/features/cart/data/cart_api.dart';
import 'package:aptech4_mobile/features/cart/domain/cart_models.dart';
import 'package:aptech4_mobile/features/cart/presentation/cart_providers.dart';
import 'package:aptech4_mobile/features/checkout/data/checkout_api.dart';
import 'package:aptech4_mobile/features/checkout/domain/checkout_models.dart';

final addressApiProvider = Provider<AddressApi>((ref) {
  return AddressApi(ref.watch(dioProvider));
});

final checkoutApiProvider = Provider<CheckoutApi>((ref) {
  return CheckoutApi(ref.watch(dioProvider));
});

class CheckoutViewState {
  const CheckoutViewState({
    this.loading = false,
    this.submitting = false,
    this.cart,
    this.addresses = const [],
    this.selectedAddressId,
    this.errorMessage,
    this.outcomeMessage,
    this.unknownOutcome = false,
    this.result,
    this.idempotencyKey,
  });

  final bool loading;
  final bool submitting;
  final Cart? cart;
  final List<Address> addresses;
  final String? selectedAddressId;
  final String? errorMessage;
  final String? outcomeMessage;
  final bool unknownOutcome;
  final CheckoutResult? result;
  final String? idempotencyKey;

  String? get effectiveAddressId {
    if (selectedAddressId != null && selectedAddressId!.isNotEmpty) {
      return selectedAddressId;
    }
    for (final a in addresses) {
      if (a.defaultAddress) return a.id;
    }
    return addresses.isEmpty ? null : addresses.first.id;
  }

  bool get canSubmit =>
      cart != null &&
      cart!.items.isNotEmpty &&
      !cart!.hasUnavailableItems &&
      effectiveAddressId != null &&
      !submitting;

  CheckoutViewState copyWith({
    bool? loading,
    bool? submitting,
    Cart? cart,
    List<Address>? addresses,
    String? selectedAddressId,
    String? errorMessage,
    String? outcomeMessage,
    bool? unknownOutcome,
    CheckoutResult? result,
    String? idempotencyKey,
    bool clearError = false,
    bool clearOutcome = false,
    bool clearResult = false,
  }) {
    return CheckoutViewState(
      loading: loading ?? this.loading,
      submitting: submitting ?? this.submitting,
      cart: cart ?? this.cart,
      addresses: addresses ?? this.addresses,
      selectedAddressId: selectedAddressId ?? this.selectedAddressId,
      errorMessage: clearError ? null : (errorMessage ?? this.errorMessage),
      outcomeMessage: clearOutcome ? null : (outcomeMessage ?? this.outcomeMessage),
      unknownOutcome: unknownOutcome ?? this.unknownOutcome,
      result: clearResult ? null : (result ?? this.result),
      idempotencyKey: idempotencyKey ?? this.idempotencyKey,
    );
  }
}

class CheckoutController extends StateNotifier<CheckoutViewState> {
  CheckoutController(this._ref)
      : super(CheckoutViewState(idempotencyKey: const Uuid().v4()));

  final Ref _ref;
  bool _submitLock = false;

  CartApi get _cartApi => _ref.read(cartApiProvider);
  AddressApi get _addressApi => _ref.read(addressApiProvider);
  CheckoutApi get _checkoutApi => _ref.read(checkoutApiProvider);

  Future<void> load() async {
    state = state.copyWith(loading: true, clearError: true, clearOutcome: true);
    try {
      final cart = await _cartApi.getCart();
      final addresses = await _addressApi.listAddresses();
      String? pickId;
      for (final a in addresses) {
        if (a.defaultAddress) {
          pickId = a.id;
          break;
        }
      }
      pickId ??= addresses.isEmpty ? null : addresses.first.id;
      state = state.copyWith(
        loading: false,
        cart: cart,
        addresses: addresses,
        selectedAddressId: state.selectedAddressId ?? pickId,
      );
    } on ApiException catch (e) {
      await _maybeLogout(e);
      state = state.copyWith(loading: false, errorMessage: e.message);
    } catch (_) {
      state = state.copyWith(loading: false, errorMessage: 'Unable to load checkout');
    }
  }

  void selectAddress(String id) {
    state = state.copyWith(selectedAddressId: id, clearOutcome: true);
  }

  Future<Address?> createAddress(AddressCreatePayload payload) async {
    try {
      final created = await _addressApi.createAddress(payload);
      final addresses = [...state.addresses, created];
      state = state.copyWith(
        addresses: addresses,
        selectedAddressId: created.id,
        clearOutcome: true,
      );
      return created;
    } on ApiException catch (e) {
      await _maybeLogout(e);
      state = state.copyWith(outcomeMessage: e.message, unknownOutcome: false);
      return null;
    } catch (_) {
      state = state.copyWith(
        outcomeMessage: 'Failed to create address',
        unknownOutcome: false,
      );
      return null;
    }
  }

  void startNewAttempt() {
    state = state.copyWith(
      idempotencyKey: const Uuid().v4(),
      unknownOutcome: false,
      clearOutcome: true,
      clearResult: true,
      submitting: false,
    );
    _submitLock = false;
  }

  /// Returns the confirmed [CheckoutResult] only when the backend responds 2xx.
  Future<CheckoutResult?> submit() async {
    final addressId = state.effectiveAddressId;
    final key = state.idempotencyKey;
    if (!state.canSubmit || addressId == null || key == null || _submitLock) {
      return null;
    }
    _submitLock = true;
    state = state.copyWith(
      submitting: true,
      clearOutcome: true,
      unknownOutcome: false,
      clearResult: true,
    );
    try {
      final result = await _checkoutApi.checkout(
        idempotencyKey: key,
        addressId: addressId,
      );
      state = state.copyWith(submitting: false, result: result);
      // Refresh cart cache after confirmed checkout.
      await _ref.read(cartControllerProvider.notifier).load();
      return result;
    } on ApiException catch (e) {
      await _maybeLogout(e);
      state = state.copyWith(
        submitting: false,
        outcomeMessage: e.message,
        unknownOutcome: false,
      );
      return null;
    } catch (_) {
      // Network / unknown — do not invent success; keep same idempotency key.
      state = state.copyWith(
        submitting: false,
        unknownOutcome: true,
        outcomeMessage:
            'Checkout outcome is unknown (network error). Do not assume failure or success. Check Orders, or retry with the same attempt key.',
      );
      return null;
    } finally {
      _submitLock = false;
    }
  }

  Future<void> _maybeLogout(ApiException e) async {
    if (e.isUnauthorized) {
      await _ref.read(authControllerProvider.notifier).logout();
    }
  }
}

final checkoutControllerProvider =
    StateNotifierProvider<CheckoutController, CheckoutViewState>((ref) {
  return CheckoutController(ref);
});
