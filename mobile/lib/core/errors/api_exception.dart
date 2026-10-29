/// Mirrors backend [ApiError] JSON without logging response bodies that may
/// contain credentials.
class ApiException implements Exception {
  ApiException({
    required this.status,
    required this.error,
    required this.message,
    this.path,
    this.details = const [],
  });

  final int status;
  final String error;
  final String message;
  final String? path;
  final List<String> details;

  bool get isUnauthorized => status == 401;
  bool get isConflict => status == 409;
  bool get isValidation => status == 400;

  factory ApiException.fromJson(int status, Map<String, dynamic>? json, {String? fallbackMessage}) {
    if (json == null) {
      return ApiException(
        status: status,
        error: 'Error',
        message: fallbackMessage ?? 'Request failed with status $status',
      );
    }
    final detailsRaw = json['details'];
    final details = detailsRaw is List
        ? detailsRaw.map((e) => e.toString()).toList(growable: false)
        : const <String>[];
    return ApiException(
      status: (json['status'] as num?)?.toInt() ?? status,
      error: (json['error'] as String?) ?? 'Error',
      message: (json['message'] as String?) ??
          fallbackMessage ??
          'Request failed with status $status',
      path: json['path'] as String?,
      details: details,
    );
  }

  factory ApiException.network(String message) => ApiException(
        status: 0,
        error: 'Network Error',
        message: message,
      );

  factory ApiException.timeout(String message) => ApiException(
        status: 0,
        error: 'Timeout',
        message: message,
      );

  @override
  String toString() => 'ApiException($status $error: $message)';
}
