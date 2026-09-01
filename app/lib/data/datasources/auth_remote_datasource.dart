import '../../core/constants/api_constants.dart';
import '../../core/network/api_client.dart';
import '../models/auth_response_model.dart';

abstract class IAuthRemoteDataSource {
  Future<Map<String, dynamic>> register({
    required String username,
    required String email,
    required String password,
  });

  Future<Map<String, dynamic>> verifyEmail({
    required String email,
    required String code,
  });

  Future<Map<String, dynamic>> resendVerification({
    required String email,
  });

  Future<Map<String, dynamic>> forgotPassword({
    required String email,
  });

  Future<Map<String, dynamic>> resetPassword({
    required String token,
    required String newPassword,
  });

  Future<AuthResponseModel> login({
    required String login,
    required String password,
  });
}

class AuthRemoteDataSourceImpl implements IAuthRemoteDataSource {
  final ApiClient _apiClient;

  AuthRemoteDataSourceImpl({required ApiClient apiClient}) : _apiClient = apiClient;

  @override
  Future<Map<String, dynamic>> register({
    required String username,
    required String email,
    required String password,
  }) async {
    try {
      final res = await _apiClient.dio.post(
        ApiConstants.register,
        data: {
          'username': username,
          'email': email,
          'password': password,
        },
      );
      return res.data as Map<String, dynamic>;
    } catch (e) {
      throw ApiClient.handleError(e);
    }
  }

  @override
  Future<Map<String, dynamic>> verifyEmail({
    required String email,
    required String code,
  }) async {
    try {
      final res = await _apiClient.dio.post(
        ApiConstants.verifyEmail,
        data: {
          'email': email,
          'code': code,
        },
      );
      return res.data as Map<String, dynamic>;
    } catch (e) {
      throw ApiClient.handleError(e);
    }
  }

  @override
  Future<Map<String, dynamic>> resendVerification({
    required String email,
  }) async {
    try {
      final res = await _apiClient.dio.post(
        ApiConstants.resendVerification,
        data: {'email': email},
      );
      return res.data as Map<String, dynamic>;
    } catch (e) {
      throw ApiClient.handleError(e);
    }
  }

  @override
  Future<Map<String, dynamic>> forgotPassword({
    required String email,
  }) async {
    try {
      final res = await _apiClient.dio.post(
        ApiConstants.forgotPassword,
        data: {'email': email},
      );
      return res.data as Map<String, dynamic>;
    } catch (e) {
      throw ApiClient.handleError(e);
    }
  }

  @override
  Future<Map<String, dynamic>> resetPassword({
    required String token,
    required String newPassword,
  }) async {
    try {
      final res = await _apiClient.dio.post(
        ApiConstants.resetPassword,
        data: {
          'token': token,
          'newPassword': newPassword,
        },
      );
      return res.data as Map<String, dynamic>;
    } catch (e) {
      throw ApiClient.handleError(e);
    }
  }

  @override
  Future<AuthResponseModel> login({
    required String login,
    required String password,
  }) async {
    try {
      final res = await _apiClient.dio.post(
        ApiConstants.authenticate,
        data: {
          'login': login,
          'password': password,
        },
      );
      return AuthResponseModel.fromJson(res.data as Map<String, dynamic>);
    } catch (e) {
      throw ApiClient.handleError(e);
    }
  }
}
