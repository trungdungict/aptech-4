import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import 'package:aptech4_mobile/features/auth/presentation/auth_controller.dart';
import 'package:aptech4_mobile/features/auth/presentation/login_screen.dart';
import 'package:aptech4_mobile/features/cart/presentation/cart_screen.dart';
import 'package:aptech4_mobile/features/catalog/presentation/product_detail_screen.dart';
import 'package:aptech4_mobile/features/catalog/presentation/product_list_screen.dart';
import 'package:aptech4_mobile/features/checkout/domain/checkout_models.dart';
import 'package:aptech4_mobile/features/checkout/presentation/checkout_screen.dart';
import 'package:aptech4_mobile/features/checkout/presentation/order_result_screen.dart';

final goRouterAuthRefreshProvider = Provider<GoRouterAuthRefresh>((ref) {
  return GoRouterAuthRefresh(ref);
});

final goRouterProvider = Provider<GoRouter>((ref) {
  final refresh = ref.watch(goRouterAuthRefreshProvider);

  return GoRouter(
    initialLocation: '/products',
    refreshListenable: refresh,
    redirect: (context, state) {
      final auth = ref.read(authControllerProvider);
      if (auth.booting) return null;

      final loc = state.matchedLocation;
      final loggingIn = loc == '/login';
      final protected = loc == '/cart' || loc == '/checkout' || loc.startsWith('/orders');

      if (!auth.isAuthenticated && protected) {
        final from = Uri.encodeComponent(state.uri.toString());
        return '/login?from=$from';
      }
      if (auth.isAuthenticated && loggingIn) {
        final from = state.uri.queryParameters['from'];
        return (from != null && from.isNotEmpty) ? from : '/products';
      }
      return null;
    },
    routes: [
      GoRoute(
        path: '/',
        redirect: (_, _) => '/products',
      ),
      GoRoute(
        path: '/login',
        builder: (context, state) => LoginScreen(
          redirectTo: state.uri.queryParameters['from'],
        ),
      ),
      GoRoute(
        path: '/products',
        builder: (context, state) => const ProductListScreen(),
      ),
      GoRoute(
        path: '/products/:id',
        builder: (context, state) => ProductDetailScreen(
          productId: state.pathParameters['id']!,
        ),
      ),
      GoRoute(
        path: '/cart',
        builder: (context, state) => const CartScreen(),
      ),
      GoRoute(
        path: '/checkout',
        builder: (context, state) => const CheckoutScreen(),
      ),
      GoRoute(
        path: '/orders/:id',
        builder: (context, state) {
          final extra = state.extra;
          return OrderResultScreen(
            orderId: state.pathParameters['id']!,
            result: extra is CheckoutResult ? extra : null,
          );
        },
      ),
    ],
  );
});

class AppBootShell extends ConsumerWidget {
  const AppBootShell({super.key, required this.child});

  final Widget child;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final auth = ref.watch(authControllerProvider);
    if (auth.booting) {
      return const Scaffold(
        body: Center(child: CircularProgressIndicator()),
      );
    }
    return child;
  }
}
