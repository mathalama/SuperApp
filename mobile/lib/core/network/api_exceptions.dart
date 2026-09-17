class ApiException implements Exception {
  final String message;
  final int? statusCode;
  final dynamic data;

  const ApiException({
    required this.message,
    this.statusCode,
    this.data,
  });

  @override
  String toString() => message;
}

class NetworkException extends ApiException {
  const NetworkException({super.message = 'No internet connection. Please check your network.'});
}

class UnauthorizedException extends ApiException {
  const UnauthorizedException({super.message = 'Session expired. Please sign in again.'})
      : super(statusCode: 401);
}

class ServerException extends ApiException {
  const ServerException({super.message = 'Internal server error occurred.', super.statusCode});
}
