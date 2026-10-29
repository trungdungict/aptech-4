import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:aptech4_mobile/core/errors/api_exception.dart';
import 'package:aptech4_mobile/features/auth/presentation/auth_controller.dart';
import 'package:aptech4_mobile/features/auth/presentation/auth_providers.dart';
import 'package:aptech4_mobile/features/cart/data/cart_api.dart';
import 'package:aptech4_mobile/features/cart/domain/cart_models.dart';

final cartApiProvider = Provider<CartApi>((ref) {
  return CartApi(ref.watch(dioProvider));
});

class CartViewState {
  const CartViewState({
    this.loading = false,
    this.cart,
    this.errorMessage,
    this.actionError,
    this.pendingItemId,
    this.mutating = false,
  });

  final bool loading;
  final Cart? cart;
  final String? errorMessage;
  final String? actionError;
  final String? pendingItemId;
  final bool mutating;

  bool get isEmpty => cart != null && cart!.isEmpty;
  bool get canCheckout =>
      cart != null && cart!.items.isNotEmpty && !cart!.hasUnavailableItems;

  CartViewState copyWith({
    bool? loading,
    Cart? cart,
    String? errorMessage,
    String? actionError,
    String? pendingItemId,
    bool? mutating,
    bool clearError = false,
    bool clearActionError = false,
    bool clearPending = false,
    bool clearCart = false,
  }) {
    return CartViewState(
      loading: loading ?? this.loading,
      cart: clearCart ? null : (cart ?? this.cart),
      errorMessage: clearError ? null : (errorMessage ?? this.errorMessage),
      actionError: clearActionError ? null : (actionError ?? this.actionError),
      pendingItemId: clearPending ? null : (pendingItemId ?? this.pendingItemId),
      mutating: mutating ?? this.mutating,
    );
  }
}

class CartController extends StateNotifier<CartViewState> {
  CartController(this._ref) : super(const CartViewState());

  final Ref _ref;
  CartApi get _api => _ref.read(cartApiProvider);

  Future<void> load() async {
    state = state.copyWith(loading: true, clearError: true, clearActionError: true);
    try {
      final cart = await _api.getCart();
      state = state.copyWith(loading: false, cart: cart);
    } on ApiException catch (e) {
      await _maybeLogout(e);
      state = state.copyWith(
        loading: false,
        errorMessage: e.status == 403
            ? 'Cart is available for customer accounts only.'
            : e.message,
        clearCart: true,
      );
    } catch (_) {
      state = state.copyWith(
        loading: false,
        errorMessage: 'Unable to load cart',
        clearCart: true,
      );
    }
  }

  Future<void> addProduct({required String productId, int quantity = 1}) async {
    if (state.mutating) return;
    state = state.copyWith(
      mutating: true,
      clearActionError: true,
      pendingItemId: productId,
    );
    try {
      final cart = await _api.addItem(productId: productId, quantity: quantity);
      state = state.copyWith(mutating: false, cart: cart, clearPending: true);
    } on ApiException catch (e) {
      await _maybeLogout(e);
      state = state.copyWith(
        mutating: false,
        actionError: e.message,
        clearPending: true,
      );
      rethrow;
    } catch (_) {
      state = state.copyWith(
        mutating: false,
        actionError: 'Failed to add item',
        clearPending: true,
      );
      rethrow;
    }
  }

  Future<void> updateQuantity({required String itemId, required int quantity}) async {
    if (state.mutating) return;
    if (quantity < 1 || quantity > cartMaxQuantity) {
      state = state.copyWith(actionError: 'Quantity must be between 1 and $cartMaxQuantity');
      return;
    }
    state = state.copyWith(
      mutating: true,
      pendingItemId: itemId,
      clearActionError: true,
    );
    try {
      final cart = await _api.updateItem(itemId: itemId, quantity: quantity);
      state = state.copyWith(mutating: false, cart: cart, clearPending: true);
    } on ApiException catch (e) {
      await _maybeLogout(e);
      state = state.copyWith(
        mutating: false,
        actionError: e.message,
        clearPending: true,
      );
    } catch (_) {
      state = state.copyWith(
        mutating: false,
        actionError: 'Cart update failed',
        clearPending: true,
      );
    }
  }

  Future<void> removeItem(String itemId) async {
    if (state.mutating) return;
    state = state.copyWith(
      mutating: true,
      pendingItemId: itemId,
      clearActionError: true,
    );
    try {
      final cart = await _api.removeItem(itemId);
      state = state.copyWith(mutating: false, cart: cart, clearPending: true);
    } on ApiException catch (e) {
      await _maybeLogout(e);
      state = state.copyWith(
        mutating: false,
        actionError: e.message,
        clearPending: true,
      );
    } catch (_) {
      state = state.copyWith(
        mutating: false,
        actionError: 'Failed to remove item',
        clearPending: true,
      );
    }
  }

  Future<void> _maybeLogout(ApiException e) async {
    if (e.isUnauthorized) {
      await _ref.read(authControllerProvider.notifier).logout();
    }
  }
}

final cartControllerProvider =
    StateNotifierProvider<CartController, CartViewState>((ref) {
  return CartController(ref);
});
