import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:aptech4_mobile/core/errors/api_exception.dart';
import 'package:aptech4_mobile/features/auth/domain/auth_session.dart';
import 'package:aptech4_mobile/features/auth/presentation/auth_providers.dart';

class AuthState {
  const AuthState({
    required this.booting,
    this.user,
    this.errorMessage,
  });

  final bool booting;
  final AuthUser? user;
  final String? errorMessage;

  bool get isAuthenticated => user != null;

  AuthState copyWith({
    bool? booting,
    AuthUser? user,
    String? errorMessage,
    bool clearUser = false,
    bool clearError = false,
  }) {
    return AuthState(
      booting: booting ?? this.booting,
      user: clearUser ? null : (user ?? this.user),
      errorMessage: clearError ? null : (errorMessage ?? this.errorMessage),
    );
  }
}

class AuthController extends StateNotifier<AuthState> {
  AuthController(this._ref) : super(const AuthState(booting: true)) {
    Future.microtask(restore);
  }

  final Ref _ref;

  Future<void> restore() async {
    state = state.copyWith(booting: true, clearError: true);
    try {
      final user = await _ref.read(authRepositoryProvider).restore();
      state = AuthState(booting: false, user: user);
    } catch (_) {
      state = const AuthState(booting: false);
    }
  }

  Future<bool> login({required String email, required String password}) async {
    state = state.copyWith(clearError: true);
    try {
      final session = await _ref.read(authRepositoryProvider).login(
            LoginPayload(email: email, password: password),
          );
      state = AuthState(booting: false, user: session.user);
      return true;
    } on ApiException catch (e) {
      state = state.copyWith(errorMessage: e.message);
      return false;
    } catch (e) {
      state = state.copyWith(errorMessage: 'Login failed');
      return false;
    }
  }

  Future<void> logout() async {
    await _ref.read(authRepositoryProvider).logout();
    state = const AuthState(booting: false);
  }
}

final authControllerProvider =
    StateNotifierProvider<AuthController, AuthState>((ref) => AuthController(ref));

/// Bridges Riverpod auth changes into a [Listenable] for GoRouter.
class GoRouterAuthRefresh extends ChangeNotifier {
  GoRouterAuthRefresh(Ref ref) {
    ref.listen<AuthState>(authControllerProvider, (_, _) => notifyListeners());
  }
}
