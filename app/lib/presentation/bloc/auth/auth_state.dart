import 'package:flutter/foundation.dart';
import '../../../domain/entities/user_entity.dart';

@immutable
abstract class AuthState {
  const AuthState();
}

class AuthInitialState extends AuthState {}

class AuthLoadingState extends AuthState {}

class AuthUnauthenticatedState extends AuthState {}

class AuthVerificationRequiredState extends AuthState {
  final String email;
  final String? infoMessage;
  const AuthVerificationRequiredState({required this.email, this.infoMessage});
}

class AuthAuthenticatedState extends AuthState {
  final UserEntity user;
  const AuthAuthenticatedState({required this.user});
}

class AuthErrorState extends AuthState {
  final String message;
  const AuthErrorState({required this.message});
}

class AuthMessageState extends AuthState {
  final String message;
  const AuthMessageState({required this.message});
}
