import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import 'package:aptech4_mobile/core/util/money.dart';
import 'package:aptech4_mobile/core/widgets/async_body.dart';
import 'package:aptech4_mobile/features/checkout/domain/checkout_models.dart';
import 'package:aptech4_mobile/features/checkout/presentation/checkout_providers.dart';

class CheckoutScreen extends ConsumerStatefulWidget {
  const CheckoutScreen({super.key});

  @override
  ConsumerState<CheckoutScreen> createState() => _CheckoutScreenState();
}

class _CheckoutScreenState extends ConsumerState<CheckoutScreen> {
  bool _showAddressForm = false;
  final _recipient = TextEditingController();
  final _line1 = TextEditingController();
  final _city = TextEditingController();
  final _phone = TextEditingController();
  final _formKey = GlobalKey<FormState>();
  bool _creatingAddress = false;

  @override
  void initState() {
    super.initState();
    Future.microtask(() => ref.read(checkoutControllerProvider.notifier).load());
  }

  @override
  void dispose() {
    _recipient.dispose();
    _line1.dispose();
    _city.dispose();
    _phone.dispose();
    super.dispose();
  }

  Future<void> _createAddress() async {
    if (_creatingAddress) return;
    if (!(_formKey.currentState?.validate() ?? false)) return;
    setState(() => _creatingAddress = true);
    final created = await ref.read(checkoutControllerProvider.notifier).createAddress(
          AddressCreatePayload(
            recipientName: _recipient.text.trim(),
            line1: _line1.text.trim(),
            city: _city.text.trim(),
            phone: _phone.text.trim().isEmpty ? null : _phone.text.trim(),
            countryCode: 'VN',
            defaultAddress: true,
          ),
        );
    if (!mounted) return;
    setState(() {
      _creatingAddress = false;
      if (created != null) {
        _showAddressForm = false;
        _recipient.clear();
        _line1.clear();
        _city.clear();
        _phone.clear();
      }
    });
  }

  Future<void> _placeOrder() async {
    final result = await ref.read(checkoutControllerProvider.notifier).submit();
    if (!mounted) return;
    if (result != null) {
      // Only navigate after backend-confirmed checkout response.
      context.go('/orders/${result.orderId}', extra: result);
    }
  }

  @override
  Widget build(BuildContext context) {
    final state = ref.watch(checkoutControllerProvider);
    final cart = state.cart;

    return Scaffold(
      appBar: AppBar(
        title: const Text('Checkout'),
        leading: IconButton(
          icon: const Icon(Icons.arrow_back),
          onPressed: () => context.go('/cart'),
        ),
      ),
      body: state.loading
          ? const LoadingBody(message: 'Loading checkout…')
          : state.errorMessage != null
              ? ErrorBody(
                  message: state.errorMessage!,
                  onRetry: () => ref.read(checkoutControllerProvider.notifier).load(),
                )
              : cart == null || cart.items.isEmpty
                  ? EmptyBody(
                      message: 'Nothing to checkout.',
                      actionLabel: 'Back to cart',
                      onAction: () => context.go('/cart'),
                    )
                  : ListView(
                      padding: const EdgeInsets.all(16),
                      children: [
                        Text(
                          'Order review',
                          style: Theme.of(context).textTheme.titleMedium,
                        ),
                        const SizedBox(height: 8),
                        Text(
                          'Totals are informational until the server confirms checkout. '
                          'Payment is mock-only (demo).',
                          style: Theme.of(context).textTheme.bodySmall,
                        ),
                        const SizedBox(height: 12),
                        if (cart.hasUnavailableItems)
                          Padding(
                            padding: const EdgeInsets.only(bottom: 12),
                            child: Text(
                              'Remove unavailable items from the cart before placing an order.',
                              style: TextStyle(color: Theme.of(context).colorScheme.error),
                            ),
                          ),
                        ...cart.items.map(
                          (item) => ListTile(
                            contentPadding: EdgeInsets.zero,
                            title: Text('${item.productName} × ${item.quantity}'),
                            trailing: Text(
                              formatMoney(item.lineSubtotal, currency: item.currency),
                            ),
                          ),
                        ),
                        const Divider(),
                        ListTile(
                          contentPadding: EdgeInsets.zero,
                          title: const Text('Subtotal'),
                          trailing: Text(
                            formatMoney(cart.subtotal, currency: cart.currency),
                          ),
                        ),
                        const SizedBox(height: 16),
                        Text(
                          'Shipping address',
                          style: Theme.of(context).textTheme.titleMedium,
                        ),
                        const SizedBox(height: 8),
                        if (state.addresses.isEmpty && !_showAddressForm)
                          Text(
                            'Add a shipping address to continue.',
                            style: TextStyle(color: Theme.of(context).colorScheme.error),
                          ),
                        ...state.addresses.map(
                          (a) {
                            final selected = state.effectiveAddressId == a.id;
                            return ListTile(
                              selected: selected,
                              leading: Icon(
                                selected
                                    ? Icons.radio_button_checked
                                    : Icons.radio_button_off,
                              ),
                              title: Text(a.recipientName),
                              subtitle: Text(a.summaryLine),
                              onTap: state.submitting
                                  ? null
                                  : () => ref
                                      .read(checkoutControllerProvider.notifier)
                                      .selectAddress(a.id),
                            );
                          },
                        ),
                        if (_showAddressForm)
                          Form(
                            key: _formKey,
                            child: Column(
                              children: [
                                TextFormField(
                                  controller: _recipient,
                                  decoration:
                                      const InputDecoration(labelText: 'Recipient name'),
                                  validator: (v) =>
                                      (v == null || v.trim().isEmpty) ? 'Required' : null,
                                ),
                                TextFormField(
                                  controller: _line1,
                                  decoration: const InputDecoration(labelText: 'Address line'),
                                  validator: (v) =>
                                      (v == null || v.trim().isEmpty) ? 'Required' : null,
                                ),
                                TextFormField(
                                  controller: _city,
                                  decoration: const InputDecoration(labelText: 'City'),
                                  validator: (v) =>
                                      (v == null || v.trim().isEmpty) ? 'Required' : null,
                                ),
                                TextFormField(
                                  controller: _phone,
                                  decoration:
                                      const InputDecoration(labelText: 'Phone (optional)'),
                                ),
                                const SizedBox(height: 8),
                                FilledButton(
                                  onPressed: _creatingAddress ? null : _createAddress,
                                  child: Text(
                                    _creatingAddress ? 'Saving…' : 'Save address',
                                  ),
                                ),
                              ],
                            ),
                          )
                        else
                          TextButton(
                            onPressed: () => setState(() => _showAddressForm = true),
                            child: const Text('Add address'),
                          ),
                        if (state.outcomeMessage != null) ...[
                          const SizedBox(height: 12),
                          Text(
                            state.outcomeMessage!,
                            style: TextStyle(
                              color: state.unknownOutcome
                                  ? Theme.of(context).colorScheme.tertiary
                                  : Theme.of(context).colorScheme.error,
                            ),
                          ),
                          if (state.unknownOutcome)
                            TextButton(
                              onPressed: () => ref
                                  .read(checkoutControllerProvider.notifier)
                                  .startNewAttempt(),
                              child: const Text('Start new attempt (new key)'),
                            ),
                        ],
                        const SizedBox(height: 24),
                        FilledButton(
                          onPressed: state.canSubmit ? _placeOrder : null,
                          child: Text(
                            state.submitting ? 'Placing order…' : 'Place order',
                          ),
                        ),
                      ],
                    ),
    );
  }
}
