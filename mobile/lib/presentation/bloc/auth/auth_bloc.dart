import 'package:flutter_bloc/flutter_bloc.dart';
import '../../../domain/repositories/i_auth_repository.dart';
import 'auth_event.dart';
import 'auth_state.dart';

class AuthBloc extends Bloc<AuthEvent, AuthState> {
  final IAuthRepository _authRepository;

  AuthBloc({required IAuthRepository authRepository})
      : _authRepository = authRepository,
        super(AuthInitialState()) {
    on<CheckAuthStatusEvent>(_onCheckAuthStatus);
    on<LoginSubmittedEvent>(_onLoginSubmitted);
    on<RegisterSubmittedEvent>(_onRegisterSubmitted);
    on<VerifyEmailSubmittedEvent>(_onVerifyEmailSubmitted);
    on<ResendVerificationSubmittedEvent>(_onResendVerificationSubmitted);
    on<ForgotPasswordSubmittedEvent>(_onForgotPasswordSubmitted);
    on<ResetPasswordSubmittedEvent>(_onResetPasswordSubmitted);
    on<LogoutRequestedEvent>(_onLogoutRequested);
  }

  Future<void> _onCheckAuthStatus(
    CheckAuthStatusEvent event,
    Emitter<AuthState> emit,
  ) async {
    final isAuth = await _authRepository.isAuthenticated();
    if (isAuth) {
      final user = await _authRepository.getCurrentUser();
      if (user != null) {
        emit(AuthAuthenticatedState(user: user));
        return;
      }
    }
    emit(AuthUnauthenticatedState());
  }

  Future<void> _onLoginSubmitted(
    LoginSubmittedEvent event,
    Emitter<AuthState> emit,
  ) async {
    emit(AuthLoadingState());
    try {
      final user = await _authRepository.login(
        login: event.login.trim(),
        password: event.password,
      );
      emit(AuthAuthenticatedState(user: user));
    } catch (e) {
      emit(AuthErrorState(message: e.toString()));
    }
  }

  Future<void> _onRegisterSubmitted(
    RegisterSubmittedEvent event,
    Emitter<AuthState> emit,
  ) async {
    emit(AuthLoadingState());
    try {
      await _authRepository.register(
        username: event.username.trim(),
        email: event.email.trim(),
        password: event.password,
      );
      emit(AuthVerificationRequiredState(
        email: event.email.trim(),
        infoMessage: 'Verification code sent to ${event.email.trim()}',
      ));
    } catch (e) {
      emit(AuthErrorState(message: e.toString()));
    }
  }

  Future<void> _onVerifyEmailSubmitted(
    VerifyEmailSubmittedEvent event,
    Emitter<AuthState> emit,
  ) async {
    emit(AuthLoadingState());
    try {
      final verified = await _authRepository.verifyEmail(
        email: event.email.trim(),
        code: event.code.trim(),
      );
      if (verified) {
        emit(const AuthMessageState(
          message: 'Email successfully verified! You can now log in.',
        ));
      } else {
        emit(const AuthErrorState(message: 'Invalid verification code. Please try again.'));
      }
    } catch (e) {
      emit(AuthErrorState(message: e.toString()));
    }
  }

  Future<void> _onResendVerificationSubmitted(
    ResendVerificationSubmittedEvent event,
    Emitter<AuthState> emit,
  ) async {
    try {
      await _authRepository.resendVerification(email: event.email.trim());
      emit(AuthVerificationRequiredState(
        email: event.email.trim(),
        infoMessage: 'New verification code sent!',
      ));
    } catch (e) {
      emit(AuthErrorState(message: e.toString()));
    }
  }

  Future<void> _onForgotPasswordSubmitted(
    ForgotPasswordSubmittedEvent event,
    Emitter<AuthState> emit,
  ) async {
    emit(AuthLoadingState());
    try {
      await _authRepository.forgotPassword(email: event.email.trim());
      emit(const AuthMessageState(
        message: 'Password reset link sent to your email.',
      ));
    } catch (e) {
      emit(AuthErrorState(message: e.toString()));
    }
  }

  Future<void> _onResetPasswordSubmitted(
    ResetPasswordSubmittedEvent event,
    Emitter<AuthState> emit,
  ) async {
    emit(AuthLoadingState());
    try {
      await _authRepository.resetPassword(
        token: event.token.trim(),
        newPassword: event.newPassword,
      );
      emit(const AuthMessageState(
        message: 'Password successfully reset! Please sign in.',
      ));
    } catch (e) {
      emit(AuthErrorState(message: e.toString()));
    }
  }

  Future<void> _onLogoutRequested(
    LogoutRequestedEvent event,
    Emitter<AuthState> emit,
  ) async {
    await _authRepository.logout();
    emit(AuthUnauthenticatedState());
  }
}
