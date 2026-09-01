import 'package:dio/dio.dart';
import 'package:flutter/foundation.dart';
import '../constants/api_constants.dart';
import '../storage/token_storage.dart';

typedef VoidCallback = void Function();

class AuthInterceptor extends QueuedInterceptorsWrapper {
  final ITokenStorage _tokenStorage;
  final Dio _dio;
  final VoidCallback? onSessionExpired;

  AuthInterceptor({
    required ITokenStorage tokenStorage,
    required Dio dio,
    this.onSessionExpired,
  })  : _tokenStorage = tokenStorage,
        _dio = dio;

  @override
  void onRequest(RequestOptions options, RequestInterceptorHandler handler) async {
    final token = await _tokenStorage.getAccessToken();
    if (token != null && token.isNotEmpty) {
      options.headers['Authorization'] = 'Bearer $token';
    }
    return handler.next(options);
  }

  @override
  void onError(DioException err, ErrorInterceptorHandler handler) async {
    // If 401 Unauthorized and not already calling refresh token
    if (err.response?.statusCode == 401 && !err.requestOptions.path.contains(ApiConstants.refreshToken)) {
      final refreshToken = await _tokenStorage.getRefreshToken();

      if (refreshToken != null && refreshToken.isNotEmpty) {
        try {
          // Perform isolated refresh request using clean Dio instance
          final refreshDio = Dio(
            BaseOptions(
              baseUrl: _dio.options.baseUrl,
              connectTimeout: ApiConstants.connectTimeout,
              receiveTimeout: ApiConstants.receiveTimeout,
            ),
          );

          final response = await refreshDio.post(
            ApiConstants.refreshToken,
            data: {'refreshToken': refreshToken},
          );

          if (response.statusCode == 200 && response.data != null) {
            final newAccessToken = response.data['accessToken'] as String?;
            final newRefreshToken = response.data['refreshToken'] as String? ?? refreshToken;

            if (newAccessToken != null) {
              await _tokenStorage.saveTokens(
                accessToken: newAccessToken,
                refreshToken: newRefreshToken,
              );

              // Update header and retry original request
              final opts = err.requestOptions;
              opts.headers['Authorization'] = 'Bearer $newAccessToken';

              final cloneReq = await _dio.fetch(opts);
              return handler.resolve(cloneReq);
            }
          }
        } catch (refreshErr) {
          debugPrint('[AuthInterceptor] Refresh token failed: $refreshErr');
        }
      }

      // Refresh token expired or failed -> clear tokens and trigger logout
      await _tokenStorage.clearTokens();
      onSessionExpired?.call();
    }

    return handler.next(err);
  }
}
