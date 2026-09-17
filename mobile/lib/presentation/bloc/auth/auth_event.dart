import 'package:flutter/foundation.dart';

@immutable
abstract class AuthEvent {
  const AuthEvent();
}

class CheckAuthStatusEvent extends AuthEvent {}

class LoginSubmittedEvent extends AuthEvent {
  final String login;
  final String password;
  const LoginSubmittedEvent({required this.login, required this.password});
}

class RegisterSubmittedEvent extends AuthEvent {
  final String username;
  final String email;
  final String password;
  const RegisterSubmittedEvent({
    required this.username,
    required this.email,
    required this.password,
  });
}

class VerifyEmailSubmittedEvent extends AuthEvent {
  final String email;
  final String code;
  const VerifyEmailSubmittedEvent({required this.email, required this.code});
}

class ResendVerificationSubmittedEvent extends AuthEvent {
  final String email;
  const ResendVerificationSubmittedEvent({required this.email});
}

class ForgotPasswordSubmittedEvent extends AuthEvent {
  final String email;
  const ForgotPasswordSubmittedEvent({required this.email});
}

class ResetPasswordSubmittedEvent extends AuthEvent {
  final String token;
  final String newPassword;
  const ResetPasswordSubmittedEvent({required this.token, required this.newPassword});
}

class LogoutRequestedEvent extends AuthEvent {}
