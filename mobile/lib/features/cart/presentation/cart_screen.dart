import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import 'package:aptech4_mobile/core/util/money.dart';
import 'package:aptech4_mobile/core/widgets/async_body.dart';
import 'package:aptech4_mobile/features/cart/domain/cart_models.dart';
import 'package:aptech4_mobile/features/cart/presentation/cart_providers.dart';

class CartScreen extends ConsumerStatefulWidget {
  const CartScreen({super.key});

  @override
  ConsumerState<CartScreen> createState() => _CartScreenState();
}

class _CartScreenState extends ConsumerState<CartScreen> {
  @override
  void initState() {
    super.initState();
    Future.microtask(() => ref.read(cartControllerProvider.notifier).load());
  }

  @override
  Widget build(BuildContext context) {
    final state = ref.watch(cartControllerProvider);
    final cart = state.cart;

    return Scaffold(
      appBar: AppBar(
        title: const Text('Cart'),
        leading: IconButton(
          icon: const Icon(Icons.arrow_back),
          onPressed: () => context.go('/products'),
        ),
      ),
      body: state.loading
          ? const LoadingBody(message: 'Loading cart…')
          : state.errorMessage != null
              ? ErrorBody(
                  message: state.errorMessage!,
                  onRetry: () => ref.read(cartControllerProvider.notifier).load(),
                )
              : state.isEmpty
                  ? EmptyBody(
                      message: 'Your cart is empty.',
                      actionLabel: 'Browse catalog',
                      onAction: () => context.go('/products'),
                    )
                  : cart == null
                      ? const EmptyBody(message: 'Your cart is empty.')
                      : Column(
                          children: [
                            if (cart.hasUnavailableItems)
                              MaterialBanner(
                                content: const Text(
                                  'Some items are no longer available. Remove them before checkout.',
                                ),
                                actions: [
                                  TextButton(
                                    onPressed: () =>
                                        ScaffoldMessenger.of(context).hideCurrentMaterialBanner(),
                                    child: const Text('OK'),
                                  ),
                                ],
                              ),
                            if (state.actionError != null)
                              Padding(
                                padding: const EdgeInsets.all(12),
                                child: Text(
                                  state.actionError!,
                                  style: TextStyle(
                                    color: Theme.of(context).colorScheme.error,
                                  ),
                                ),
                              ),
                            Expanded(
                              child: ListView.separated(
                                itemCount: cart.items.length,
                                separatorBuilder: (_, _) => const Divider(height: 1),
                                itemBuilder: (context, index) {
                                  final item = cart.items[index];
                                  final busy = state.pendingItemId == item.id;
                                  return _CartLine(
                                    item: item,
                                    busy: busy || state.mutating,
                                    onDecrease: () {
                                      if (item.quantity <= 1) return;
                                      ref.read(cartControllerProvider.notifier).updateQuantity(
                                            itemId: item.id,
                                            quantity: item.quantity - 1,
                                          );
                                    },
                                    onIncrease: () {
                                      if (item.quantity >= cartMaxQuantity) return;
                                      ref.read(cartControllerProvider.notifier).updateQuantity(
                                            itemId: item.id,
                                            quantity: item.quantity + 1,
                                          );
                                    },
                                    onRemove: () => ref
                                        .read(cartControllerProvider.notifier)
                                        .removeItem(item.id),
                                  );
                                },
                              ),
                            ),
                            SafeArea(
                              child: Padding(
                                padding: const EdgeInsets.all(16),
                                child: Column(
                                  crossAxisAlignment: CrossAxisAlignment.stretch,
                                  children: [
                                    Row(
                                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                                      children: [
                                        Text(
                                          'Subtotal',
                                          style: Theme.of(context).textTheme.titleMedium,
                                        ),
                                        Text(
                                          formatMoney(cart.subtotal, currency: cart.currency),
                                          style: Theme.of(context).textTheme.titleMedium,
                                        ),
                                      ],
                                    ),
                                    const SizedBox(height: 12),
                                    FilledButton(
                                      onPressed: state.canCheckout && !state.mutating
                                          ? () => context.go('/checkout')
                                          : null,
                                      child: const Text('Checkout'),
                                    ),
                                  ],
                                ),
                              ),
                            ),
                          ],
                        ),
    );
  }
}

class _CartLine extends StatelessWidget {
  const _CartLine({
    required this.item,
    required this.busy,
    required this.onDecrease,
    required this.onIncrease,
    required this.onRemove,
  });

  final CartItem item;
  final bool busy;
  final VoidCallback onDecrease;
  final VoidCallback onIncrease;
  final VoidCallback onRemove;

  @override
  Widget build(BuildContext context) {
    return ListTile(
      title: Text(item.productName),
      subtitle: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(formatMoney(item.unitPrice, currency: item.currency)),
          if (!item.available)
            Text(
              'Unavailable',
              style: TextStyle(color: Theme.of(context).colorScheme.error),
            ),
        ],
      ),
      trailing: busy
          ? const SizedBox(
              width: 24,
              height: 24,
              child: CircularProgressIndicator(strokeWidth: 2),
            )
          : Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                IconButton(
                  onPressed: item.available ? onDecrease : null,
                  icon: const Icon(Icons.remove_circle_outline),
                ),
                Text('${item.quantity}'),
                IconButton(
                  onPressed: item.available ? onIncrease : null,
                  icon: const Icon(Icons.add_circle_outline),
                ),
                IconButton(
                  onPressed: onRemove,
                  icon: const Icon(Icons.delete_outline),
                ),
              ],
            ),
    );
  }
}
