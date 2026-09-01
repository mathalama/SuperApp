import '../entities/user_entity.dart';

abstract class IAuthRepository {
  Future<void> register({
    required String username,
    required String email,
    required String password,
  });

  Future<bool> verifyEmail({
    required String email,
    required String code,
  });

  Future<void> resendVerification({
    required String email,
  });

  Future<void> forgotPassword({
    required String email,
  });

  Future<void> resetPassword({
    required String token,
    required String newPassword,
  });

  Future<UserEntity> login({
    required String login,
    required String password,
  });

  Future<void> logout();

  Future<bool> isAuthenticated();

  Future<UserEntity?> getCurrentUser();
}
