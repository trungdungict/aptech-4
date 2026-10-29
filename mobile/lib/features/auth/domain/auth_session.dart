/// Matches backend `AuthResponse` / `AuthResponse.UserResponse`.
class AuthUser {
  const AuthUser({
    required this.id,
    required this.email,
    required this.fullName,
    required this.roles,
  });

  final String id;
  final String email;
  final String fullName;
  final List<String> roles;

  factory AuthUser.fromJson(Map<String, dynamic> json) {
    final rolesRaw = json['roles'];
    final roles = rolesRaw is List
        ? rolesRaw.map((e) => e.toString()).toList(growable: false)
        : const <String>[];
    return AuthUser(
      id: json['id'].toString(),
      email: json['email'] as String,
      fullName: json['fullName'] as String,
      roles: roles,
    );
  }

  Map<String, dynamic> toJson() => {
        'id': id,
        'email': email,
        'fullName': fullName,
        'roles': roles,
      };
}

class AuthSession {
  const AuthSession({
    required this.accessToken,
    required this.refreshToken,
    required this.tokenType,
    required this.expiresIn,
    required this.user,
  });

  final String accessToken;
  final String refreshToken;
  final String tokenType;
  final int expiresIn;
  final AuthUser user;

  factory AuthSession.fromJson(Map<String, dynamic> json) {
    return AuthSession(
      accessToken: json['accessToken'] as String,
      refreshToken: json['refreshToken'] as String,
      tokenType: json['tokenType'] as String? ?? 'Bearer',
      expiresIn: (json['expiresIn'] as num).toInt(),
      user: AuthUser.fromJson(json['user'] as Map<String, dynamic>),
    );
  }
}

class LoginPayload {
  const LoginPayload({required this.email, required this.password});

  final String email;
  final String password;

  Map<String, dynamic> toJson() => {
        'email': email,
        'password': password,
      };
}

class RegisterPayload {
  const RegisterPayload({
    required this.email,
    required this.password,
    required this.fullName,
  });

  final String email;
  final String password;
  final String fullName;

  Map<String, dynamic> toJson() => {
        'email': email,
        'password': password,
        'fullName': fullName,
      };
}
