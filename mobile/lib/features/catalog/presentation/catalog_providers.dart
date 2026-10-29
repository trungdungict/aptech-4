import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:aptech4_mobile/core/errors/api_exception.dart';
import 'package:aptech4_mobile/features/auth/presentation/auth_providers.dart';
import 'package:aptech4_mobile/features/catalog/data/catalog_api.dart';
import 'package:aptech4_mobile/features/catalog/domain/catalog_models.dart';

final catalogApiProvider = Provider<CatalogApi>((ref) {
  return CatalogApi(ref.watch(dioProvider));
});

class CatalogListState {
  const CatalogListState({
    this.loading = false,
    this.loadingMore = false,
    this.items = const [],
    this.categories = const [],
    this.params = const ProductListParams(),
    this.page,
    this.errorMessage,
  });

  final bool loading;
  final bool loadingMore;
  final List<Product> items;
  final List<Category> categories;
  final ProductListParams params;
  final PageResponse<Product>? page;
  final String? errorMessage;

  bool get isEmpty => !loading && errorMessage == null && items.isEmpty;
  bool get hasMore => page != null && !page!.last;

  CatalogListState copyWith({
    bool? loading,
    bool? loadingMore,
    List<Product>? items,
    List<Category>? categories,
    ProductListParams? params,
    PageResponse<Product>? page,
    String? errorMessage,
    bool clearError = false,
    bool clearPage = false,
  }) {
    return CatalogListState(
      loading: loading ?? this.loading,
      loadingMore: loadingMore ?? this.loadingMore,
      items: items ?? this.items,
      categories: categories ?? this.categories,
      params: params ?? this.params,
      page: clearPage ? null : (page ?? this.page),
      errorMessage: clearError ? null : (errorMessage ?? this.errorMessage),
    );
  }
}

class CatalogListController extends StateNotifier<CatalogListState> {
  CatalogListController(this._api) : super(const CatalogListState());

  final CatalogApi _api;

  Future<void> refresh() async {
    state = state.copyWith(loading: true, clearError: true);
    try {
      final categories = await _api.listCategories();
      final page = await _api.listProducts(state.params.copyWith(page: 0));
      state = state.copyWith(
        loading: false,
        categories: categories,
        items: page.items,
        page: page,
        params: state.params.copyWith(page: page.page),
      );
    } on ApiException catch (e) {
      state = state.copyWith(loading: false, errorMessage: e.message, items: const []);
    } catch (_) {
      state = state.copyWith(
        loading: false,
        errorMessage: 'Unable to load products',
        items: const [],
      );
    }
  }

  Future<void> setQuery(String q) async {
    state = state.copyWith(params: state.params.copyWith(q: q, page: 0));
    await refresh();
  }

  Future<void> setCategory(String? categoryId) async {
    state = state.copyWith(
      params: state.params.copyWith(
        categoryId: categoryId,
        page: 0,
        clearCategory: categoryId == null,
      ),
    );
    await refresh();
  }

  Future<void> loadMore() async {
    if (!state.hasMore || state.loadingMore || state.loading) return;
    final nextPage = (state.page?.page ?? 0) + 1;
    state = state.copyWith(loadingMore: true, clearError: true);
    try {
      final page = await _api.listProducts(state.params.copyWith(page: nextPage));
      state = state.copyWith(
        loadingMore: false,
        items: [...state.items, ...page.items],
        page: page,
        params: state.params.copyWith(page: page.page),
      );
    } on ApiException catch (e) {
      state = state.copyWith(loadingMore: false, errorMessage: e.message);
    } catch (_) {
      state = state.copyWith(loadingMore: false, errorMessage: 'Unable to load more products');
    }
  }
}

final catalogListControllerProvider =
    StateNotifierProvider<CatalogListController, CatalogListState>((ref) {
  return CatalogListController(ref.watch(catalogApiProvider));
});

final productDetailProvider =
    FutureProvider.autoDispose.family<Product, String>((ref, id) async {
  return ref.watch(catalogApiProvider).getProduct(id);
});
