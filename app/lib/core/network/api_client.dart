import 'dart:io';
import 'package:dio/dio.dart';
import '../constants/api_constants.dart';
import '../storage/token_storage.dart';
import 'api_exceptions.dart';
import 'auth_interceptor.dart';

class ApiClient {
  final Dio dio;

  ApiClient({
    required ITokenStorage tokenStorage,
    String? baseUrl,
    VoidCallback? onSessionExpired,
  }) : dio = Dio(
          BaseOptions(
            baseUrl: baseUrl ?? ApiConstants.defaultBaseUrl,
            connectTimeout: ApiConstants.connectTimeout,
            receiveTimeout: ApiConstants.receiveTimeout,
            headers: {
              'Content-Type': 'application/json',
              'Accept': 'application/json',
              'ngrok-skip-browser-warning': 'true',
            },
          ),
        ) {
    dio.interceptors.add(
      AuthInterceptor(
        tokenStorage: tokenStorage,
        dio: dio,
        onSessionExpired: onSessionExpired,
      ),
    );
  }

  /// Converts Dio errors to domain ApiExceptions
  static ApiException handleError(dynamic error) {
    if (error is DioException) {
      switch (error.type) {
        case DioExceptionType.connectionTimeout:
        case DioExceptionType.sendTimeout:
        case DioExceptionType.receiveTimeout:
          return const NetworkException(message: 'Connection timed out. Please try again.');
        case DioExceptionType.badResponse:
          final statusCode = error.response?.statusCode;
          final data = error.response?.data;
          String message = 'An unexpected server error occurred.';
          if (data is Map<String, dynamic>) {
            message = data['message'] ?? data['error'] ?? message;
          }
          if (statusCode == 401) {
            return UnauthorizedException(message: message);
          }
          return ServerException(message: message, statusCode: statusCode);
        case DioExceptionType.connectionError:
          if (error.error is SocketException) {
            return const NetworkException(message: 'Cannot reach the server. Please check your connection.');
          }
          return const NetworkException();
        default:
          return ApiException(message: error.message ?? 'Unknown network error');
      }
    }
    if (error is ApiException) return error;
    return ApiException(message: error.toString());
  }
}
