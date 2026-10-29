import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import 'package:aptech4_mobile/core/util/money.dart';
import 'package:aptech4_mobile/core/widgets/async_body.dart';
import 'package:aptech4_mobile/features/auth/presentation/auth_controller.dart';
import 'package:aptech4_mobile/features/catalog/presentation/catalog_providers.dart';

class ProductListScreen extends ConsumerStatefulWidget {
  const ProductListScreen({super.key});

  @override
  ConsumerState<ProductListScreen> createState() => _ProductListScreenState();
}

class _ProductListScreenState extends ConsumerState<ProductListScreen> {
  final _search = TextEditingController();

  @override
  void initState() {
    super.initState();
    Future.microtask(() => ref.read(catalogListControllerProvider.notifier).refresh());
  }

  @override
  void dispose() {
    _search.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final state = ref.watch(catalogListControllerProvider);
    final auth = ref.watch(authControllerProvider);

    return Scaffold(
      appBar: AppBar(
        title: const Text('Catalog'),
        actions: [
          IconButton(
            tooltip: 'Cart',
            onPressed: () {
              if (!auth.isAuthenticated) {
                context.go('/login?from=/cart');
              } else {
                context.go('/cart');
              }
            },
            icon: const Icon(Icons.shopping_cart_outlined),
          ),
          if (auth.isAuthenticated)
            IconButton(
              tooltip: 'Sign out',
              onPressed: () => ref.read(authControllerProvider.notifier).logout(),
              icon: const Icon(Icons.logout),
            )
          else
            TextButton(
              onPressed: () => context.go('/login'),
              child: const Text('Sign in'),
            ),
        ],
      ),
      body: Column(
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 8, 16, 0),
            child: TextField(
              controller: _search,
              decoration: InputDecoration(
                hintText: 'Search products',
                suffixIcon: IconButton(
                  icon: const Icon(Icons.search),
                  onPressed: () {
                    ref
                        .read(catalogListControllerProvider.notifier)
                        .setQuery(_search.text);
                  },
                ),
              ),
              textInputAction: TextInputAction.search,
              onSubmitted: (v) =>
                  ref.read(catalogListControllerProvider.notifier).setQuery(v),
            ),
          ),
          if (state.categories.isNotEmpty)
            SizedBox(
              height: 48,
              child: ListView(
                scrollDirection: Axis.horizontal,
                padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
                children: [
                  Padding(
                    padding: const EdgeInsets.symmetric(horizontal: 4),
                    child: FilterChip(
                      label: const Text('All'),
                      selected: state.params.categoryId == null,
                      onSelected: (_) => ref
                          .read(catalogListControllerProvider.notifier)
                          .setCategory(null),
                    ),
                  ),
                  ...state.categories.map(
                    (c) => Padding(
                      padding: const EdgeInsets.symmetric(horizontal: 4),
                      child: FilterChip(
                        label: Text(c.name),
                        selected: state.params.categoryId == c.id,
                        onSelected: (_) => ref
                            .read(catalogListControllerProvider.notifier)
                            .setCategory(c.id),
                      ),
                    ),
                  ),
                ],
              ),
            ),
          Expanded(
            child: state.loading
                ? const LoadingBody(message: 'Loading products…')
                : state.errorMessage != null && state.items.isEmpty
                    ? ErrorBody(
                        message: state.errorMessage!,
                        onRetry: () =>
                            ref.read(catalogListControllerProvider.notifier).refresh(),
                      )
                    : state.isEmpty
                        ? EmptyBody(
                            message: 'No products found.',
                            actionLabel: 'Refresh',
                            onAction: () => ref
                                .read(catalogListControllerProvider.notifier)
                                .refresh(),
                          )
                        : RefreshIndicator(
                            onRefresh: () =>
                                ref.read(catalogListControllerProvider.notifier).refresh(),
                            child: ListView.builder(
                              itemCount: state.items.length + (state.hasMore ? 1 : 0),
                              itemBuilder: (context, index) {
                                if (index >= state.items.length) {
                                  if (!state.loadingMore) {
                                    WidgetsBinding.instance.addPostFrameCallback((_) {
                                      ref
                                          .read(catalogListControllerProvider.notifier)
                                          .loadMore();
                                    });
                                  }
                                  return const Padding(
                                    padding: EdgeInsets.all(16),
                                    child: Center(child: CircularProgressIndicator()),
                                  );
                                }
                                final product = state.items[index];
                                return ListTile(
                                  title: Text(product.name),
                                  subtitle: Text(
                                    [
                                      product.categoryName,
                                      if (product.inStock == false) 'Out of stock',
                                    ].where((e) => e.isNotEmpty).join(' · '),
                                  ),
                                  trailing: Text(
                                    formatMoney(product.unitPrice, currency: product.currency),
                                    style: Theme.of(context).textTheme.titleSmall,
                                  ),
                                  onTap: () => context.go('/products/${product.id}'),
                                );
                              },
                            ),
                          ),
          ),
        ],
      ),
    );
  }
}
