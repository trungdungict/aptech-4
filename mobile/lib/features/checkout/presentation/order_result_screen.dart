import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import 'package:aptech4_mobile/core/util/money.dart';
import 'package:aptech4_mobile/features/checkout/domain/checkout_models.dart';

/// Shows backend-confirmed checkout status only (no local payment simulation).
class OrderResultScreen extends StatelessWidget {
  const OrderResultScreen({
    super.key,
    required this.orderId,
    this.result,
  });

  final String orderId;
  final CheckoutResult? result;

  @override
  Widget build(BuildContext context) {
    final status = result?.orderStatus;
    final paymentStatus = result?.payment.status;

    return Scaffold(
      appBar: AppBar(
        title: const Text('Order'),
        automaticallyImplyLeading: false,
      ),
      body: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Text(
              result == null ? 'Order $orderId' : 'Order confirmed by server',
              style: Theme.of(context).textTheme.headlineSmall,
            ),
            const SizedBox(height: 12),
            Text('Order id: $orderId'),
            if (status != null) Text('Order status: $status'),
            if (paymentStatus != null) Text('Payment status: $paymentStatus'),
            if (result != null) ...[
              const SizedBox(height: 8),
              Text(
                'Total: ${formatMoney(result!.totalAmount, currency: result!.currency)}',
              ),
              if (result!.payment.failureReason != null)
                Text(
                  result!.payment.failureReason!,
                  style: TextStyle(color: Theme.of(context).colorScheme.error),
                ),
              const SizedBox(height: 8),
              Text(
                result!.cartCleared
                    ? 'Cart was cleared after successful payment.'
                    : 'Cart was not cleared (payment may have failed).',
              ),
            ] else
              const Text(
                'Open this screen from a confirmed checkout response to see status details.',
              ),
            const Spacer(),
            FilledButton(
              onPressed: () => context.go('/products'),
              child: const Text('Back to catalog'),
            ),
            TextButton(
              onPressed: () => context.go('/cart'),
              child: const Text('View cart'),
            ),
          ],
        ),
      ),
    );
  }
}
