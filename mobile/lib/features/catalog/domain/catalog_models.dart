class Product {
  const Product({
    required this.id,
    required this.sku,
    required this.name,
    this.description,
    required this.unitPrice,
    required this.currency,
    required this.categoryId,
    required this.categoryName,
    required this.active,
    this.imageUrl,
    this.inStock,
  });

  final String id;
  final String sku;
  final String name;
  final String? description;
  final dynamic unitPrice;
  final String currency;
  final String categoryId;
  final String categoryName;
  final bool active;
  final String? imageUrl;
  final bool? inStock;

  factory Product.fromJson(Map<String, dynamic> json) {
    return Product(
      id: json['id'].toString(),
      sku: json['sku'] as String,
      name: json['name'] as String,
      description: json['description'] as String?,
      unitPrice: json['unitPrice'],
      currency: json['currency'] as String? ?? 'VND',
      categoryId: json['categoryId'].toString(),
      categoryName: json['categoryName'] as String? ?? '',
      active: json['active'] as bool? ?? true,
      imageUrl: json['imageUrl'] as String?,
      inStock: json['inStock'] as bool?,
    );
  }
}

class Category {
  const Category({
    required this.id,
    required this.name,
    required this.slug,
    this.description,
    this.parentId,
    required this.active,
  });

  final String id;
  final String name;
  final String slug;
  final String? description;
  final String? parentId;
  final bool active;

  factory Category.fromJson(Map<String, dynamic> json) {
    return Category(
      id: json['id'].toString(),
      name: json['name'] as String,
      slug: json['slug'] as String,
      description: json['description'] as String?,
      parentId: json['parentId']?.toString(),
      active: json['active'] as bool? ?? true,
    );
  }
}

class PageResponse<T> {
  const PageResponse({
    required this.items,
    required this.page,
    required this.size,
    required this.totalElements,
    required this.totalPages,
    required this.first,
    required this.last,
  });

  final List<T> items;
  final int page;
  final int size;
  final int totalElements;
  final int totalPages;
  final bool first;
  final bool last;

  factory PageResponse.fromJson(
    Map<String, dynamic> json,
    T Function(Map<String, dynamic>) itemParser,
  ) {
    final rawItems = json['items'];
    final List<T> items = rawItems is List
        ? rawItems
            .whereType<Map<String, dynamic>>()
            .map(itemParser)
            .toList(growable: false)
        : <T>[];
    return PageResponse(
      items: items,
      page: (json['page'] as num?)?.toInt() ?? 0,
      size: (json['size'] as num?)?.toInt() ?? 0,
      totalElements: (json['totalElements'] as num?)?.toInt() ?? 0,
      totalPages: (json['totalPages'] as num?)?.toInt() ?? 0,
      first: json['first'] as bool? ?? true,
      last: json['last'] as bool? ?? true,
    );
  }
}

class ProductListParams {
  const ProductListParams({
    this.q,
    this.categoryId,
    this.inStock,
    this.page = 0,
    this.size = 20,
    this.sort = 'name,asc',
  });

  final String? q;
  final String? categoryId;
  final bool? inStock;
  final int page;
  final int size;
  final String sort;

  Map<String, dynamic> toQuery() {
    return {
      if (q != null && q!.trim().isNotEmpty) 'q': q!.trim(),
      if (categoryId != null && categoryId!.isNotEmpty) 'categoryId': categoryId,
      if (inStock != null) 'inStock': inStock,
      'page': page,
      'size': size,
      'sort': sort,
    };
  }

  ProductListParams copyWith({
    String? q,
    String? categoryId,
    bool? inStock,
    int? page,
    int? size,
    String? sort,
    bool clearCategory = false,
  }) {
    return ProductListParams(
      q: q ?? this.q,
      categoryId: clearCategory ? null : (categoryId ?? this.categoryId),
      inStock: inStock ?? this.inStock,
      page: page ?? this.page,
      size: size ?? this.size,
      sort: sort ?? this.sort,
    );
  }
}
