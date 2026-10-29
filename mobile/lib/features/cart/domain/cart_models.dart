class CartItem {
  const CartItem({
    required this.id,
    required this.productId,
    required this.productName,
    this.imageUrl,
    required this.quantity,
    required this.unitPrice,
    required this.lineSubtotal,
    required this.currency,
    required this.available,
    required this.productActive,
  });

  final String id;
  final String productId;
  final String productName;
  final String? imageUrl;
  final int quantity;
  final dynamic unitPrice;
  final dynamic lineSubtotal;
  final String currency;
  final bool available;
  final bool productActive;

  factory CartItem.fromJson(Map<String, dynamic> json) {
    return CartItem(
      id: json['id'].toString(),
      productId: json['productId'].toString(),
      productName: json['productName'] as String,
      imageUrl: json['imageUrl'] as String?,
      quantity: (json['quantity'] as num).toInt(),
      unitPrice: json['unitPrice'],
      lineSubtotal: json['lineSubtotal'],
      currency: json['currency'] as String? ?? 'VND',
      available: json['available'] as bool? ?? true,
      productActive: json['productActive'] as bool? ?? true,
    );
  }
}

class Cart {
  const Cart({
    required this.id,
    required this.items,
    required this.subtotal,
    required this.currency,
    required this.hasUnavailableItems,
  });

  final String id;
  final List<CartItem> items;
  final dynamic subtotal;
  final String currency;
  final bool hasUnavailableItems;

  bool get isEmpty => items.isEmpty;

  factory Cart.fromJson(Map<String, dynamic> json) {
    final rawItems = json['items'];
    final items = rawItems is List
        ? rawItems.whereType<Map<String, dynamic>>().map(CartItem.fromJson).toList()
        : <CartItem>[];
    return Cart(
      id: json['id'].toString(),
      items: items,
      subtotal: json['subtotal'],
      currency: json['currency'] as String? ?? 'VND',
      hasUnavailableItems: json['hasUnavailableItems'] as bool? ?? false,
    );
  }
}

const cartMaxQuantity = 99;
