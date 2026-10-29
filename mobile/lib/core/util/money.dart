/// Formats backend BigDecimal JSON (number or string) for display.
String formatMoney(dynamic value, {String currency = 'VND'}) {
  final raw = value is num ? value.toString() : (value?.toString() ?? '0');
  return '$raw $currency';
}

num? parseMoney(dynamic value) {
  if (value == null) return null;
  if (value is num) return value;
  return num.tryParse(value.toString());
}
