import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import 'package:aptech4_mobile/core/errors/api_exception.dart';
import 'package:aptech4_mobile/core/util/money.dart';
import 'package:aptech4_mobile/core/widgets/async_body.dart';
import 'package:aptech4_mobile/features/auth/presentation/auth_controller.dart';
import 'package:aptech4_mobile/features/cart/presentation/cart_providers.dart';
import 'package:aptech4_mobile/features/catalog/presentation/catalog_providers.dart';

class ProductDetailScreen extends ConsumerStatefulWidget {
  const ProductDetailScreen({super.key, required this.productId});

  final String productId;

  @override
  ConsumerState<ProductDetailScreen> createState() => _ProductDetailScreenState();
}

class _ProductDetailScreenState extends ConsumerState<ProductDetailScreen> {
  bool _adding = false;
  String? _addError;

  Future<void> _addToCart() async {
    final auth = ref.read(authControllerProvider);
    if (!auth.isAuthenticated) {
      context.go('/login?from=/products/${widget.productId}');
      return;
    }
    if (_adding) return;
    setState(() {
      _adding = true;
      _addError = null;
    });
    try {
      await ref.read(cartControllerProvider.notifier).addProduct(
            productId: widget.productId,
          );
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Added to cart')),
      );
    } on ApiException catch (e) {
      if (!mounted) return;
      setState(() => _addError = e.message);
    } catch (_) {
      if (!mounted) return;
      setState(() => _addError = 'Failed to add to cart');
    } finally {
      if (mounted) setState(() => _adding = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final async = ref.watch(productDetailProvider(widget.productId));

    return Scaffold(
      appBar: AppBar(
        title: const Text('Product'),
        leading: IconButton(
          icon: const Icon(Icons.arrow_back),
          onPressed: () => context.go('/products'),
        ),
        actions: [
          IconButton(
            icon: const Icon(Icons.shopping_cart_outlined),
            onPressed: () {
              final auth = ref.read(authControllerProvider);
              if (!auth.isAuthenticated) {
                context.go('/login?from=/cart');
              } else {
                context.go('/cart');
              }
            },
          ),
        ],
      ),
      body: async.when(
        loading: () => const LoadingBody(message: 'Loading product…'),
        error: (e, _) => ErrorBody(
          message: e is ApiException ? e.message : 'Unable to load product',
          onRetry: () => ref.invalidate(productDetailProvider(widget.productId)),
        ),
        data: (product) => ListView(
          padding: const EdgeInsets.all(24),
          children: [
            Text(
              product.name,
              style: Theme.of(context).textTheme.headlineSmall?.copyWith(
                    fontWeight: FontWeight.w700,
                  ),
            ),
            const SizedBox(height: 8),
            Text(
              formatMoney(product.unitPrice, currency: product.currency),
              style: Theme.of(context).textTheme.titleLarge,
            ),
            const SizedBox(height: 8),
            Text('SKU ${product.sku} · ${product.categoryName}'),
            if (product.inStock == false) ...[
              const SizedBox(height: 8),
              Text(
                'Currently out of stock (rechecked at checkout)',
                style: TextStyle(color: Theme.of(context).colorScheme.error),
              ),
            ],
            if (product.description != null && product.description!.isNotEmpty) ...[
              const SizedBox(height: 16),
              Text(product.description!),
            ],
            if (_addError != null) ...[
              const SizedBox(height: 16),
              Text(
                _addError!,
                style: TextStyle(color: Theme.of(context).colorScheme.error),
              ),
            ],
            const SizedBox(height: 24),
            FilledButton(
              onPressed: _adding ? null : _addToCart,
              child: Text(_adding ? 'Adding…' : 'Add to cart'),
            ),
          ],
        ),
      ),
    );
  }
}
