class Address {
  const Address({
    required this.id,
    this.label,
    required this.recipientName,
    required this.line1,
    this.line2,
    required this.city,
    this.stateRegion,
    this.postalCode,
    required this.countryCode,
    this.phone,
    required this.defaultAddress,
  });

  final String id;
  final String? label;
  final String recipientName;
  final String line1;
  final String? line2;
  final String city;
  final String? stateRegion;
  final String? postalCode;
  final String countryCode;
  final String? phone;
  final bool defaultAddress;

  factory Address.fromJson(Map<String, dynamic> json) {
    return Address(
      id: json['id'].toString(),
      label: json['label'] as String?,
      recipientName: json['recipientName'] as String,
      line1: json['line1'] as String,
      line2: json['line2'] as String?,
      city: json['city'] as String,
      stateRegion: json['stateRegion'] as String?,
      postalCode: json['postalCode'] as String?,
      countryCode: json['countryCode'] as String? ?? 'VN',
      phone: json['phone'] as String?,
      defaultAddress: json['defaultAddress'] as bool? ?? false,
    );
  }

  String get summaryLine =>
      [line1, if (line2 != null && line2!.isNotEmpty) line2, city, countryCode]
          .whereType<String>()
          .join(', ');
}

class AddressCreatePayload {
  const AddressCreatePayload({
    this.label,
    required this.recipientName,
    required this.line1,
    this.line2,
    required this.city,
    this.stateRegion,
    this.postalCode,
    this.countryCode,
    this.phone,
    this.defaultAddress,
  });

  final String? label;
  final String recipientName;
  final String line1;
  final String? line2;
  final String city;
  final String? stateRegion;
  final String? postalCode;
  final String? countryCode;
  final String? phone;
  final bool? defaultAddress;

  Map<String, dynamic> toJson() => {
        if (label != null) 'label': label,
        'recipientName': recipientName,
        'line1': line1,
        if (line2 != null) 'line2': line2,
        'city': city,
        if (stateRegion != null) 'stateRegion': stateRegion,
        if (postalCode != null) 'postalCode': postalCode,
        if (countryCode != null) 'countryCode': countryCode,
        if (phone != null) 'phone': phone,
        if (defaultAddress != null) 'defaultAddress': defaultAddress,
      };
}

class CheckoutPayment {
  const CheckoutPayment({
    required this.paymentId,
    required this.provider,
    required this.status,
    required this.amount,
    required this.currency,
    this.providerReference,
    this.failureReason,
  });

  final String paymentId;
  final String provider;
  final String status;
  final dynamic amount;
  final String currency;
  final String? providerReference;
  final String? failureReason;

  factory CheckoutPayment.fromJson(Map<String, dynamic> json) {
    return CheckoutPayment(
      paymentId: json['paymentId'].toString(),
      provider: json['provider'] as String,
      status: json['status'] as String,
      amount: json['amount'],
      currency: json['currency'] as String? ?? 'VND',
      providerReference: json['providerReference'] as String?,
      failureReason: json['failureReason'] as String?,
    );
  }
}

class CheckoutOrderItem {
  const CheckoutOrderItem({
    required this.productId,
    required this.productSku,
    required this.productName,
    required this.unitPrice,
    required this.quantity,
    required this.lineTotal,
  });

  final String productId;
  final String productSku;
  final String productName;
  final dynamic unitPrice;
  final int quantity;
  final dynamic lineTotal;

  factory CheckoutOrderItem.fromJson(Map<String, dynamic> json) {
    return CheckoutOrderItem(
      productId: json['productId'].toString(),
      productSku: json['productSku'] as String,
      productName: json['productName'] as String,
      unitPrice: json['unitPrice'],
      quantity: (json['quantity'] as num).toInt(),
      lineTotal: json['lineTotal'],
    );
  }
}

class CheckoutResult {
  const CheckoutResult({
    required this.orderId,
    required this.orderStatus,
    required this.subtotalAmount,
    required this.shippingAmount,
    required this.totalAmount,
    required this.currency,
    required this.placedAt,
    required this.items,
    required this.payment,
    required this.cartCleared,
  });

  final String orderId;
  final String orderStatus;
  final dynamic subtotalAmount;
  final dynamic shippingAmount;
  final dynamic totalAmount;
  final String currency;
  final String placedAt;
  final List<CheckoutOrderItem> items;
  final CheckoutPayment payment;
  final bool cartCleared;

  factory CheckoutResult.fromJson(Map<String, dynamic> json) {
    final rawItems = json['items'];
    final items = rawItems is List
        ? rawItems
            .whereType<Map<String, dynamic>>()
            .map(CheckoutOrderItem.fromJson)
            .toList()
        : <CheckoutOrderItem>[];
    return CheckoutResult(
      orderId: json['orderId'].toString(),
      orderStatus: json['orderStatus'] as String,
      subtotalAmount: json['subtotalAmount'],
      shippingAmount: json['shippingAmount'],
      totalAmount: json['totalAmount'],
      currency: json['currency'] as String? ?? 'VND',
      placedAt: json['placedAt'].toString(),
      items: items,
      payment: CheckoutPayment.fromJson(json['payment'] as Map<String, dynamic>),
      cartCleared: json['cartCleared'] as bool? ?? false,
    );
  }
}
