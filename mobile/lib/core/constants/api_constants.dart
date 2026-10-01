class ApiConstants {
  /// Base URL:
  /// - Uses BASE_URL dart-define if provided
  /// - Default: http://127.0.0.1:8700 (works on real phones via 'adb reverse tcp:8700 tcp:8700' and localhost)
  static String get defaultBaseUrl {
    const fromEnv = String.fromEnvironment('BASE_URL');
    if (fromEnv.isNotEmpty) return fromEnv;
    return 'http://127.0.0.1:8700';
  }

  // Auth Endpoints (matching backend Gateway / Auth service)
  static const String register = '/auth/register';
  static const String verifyEmail = '/auth/verify-email';
  static const String resendVerification = '/auth/resend-verification';
  static const String forgotPassword = '/auth/forgot-password';
  static const String resetPassword = '/auth/reset-password';
  static const String authenticate = '/auth/authenticate';
  static const String refreshToken = '/auth/refresh';

  // KYC Endpoints
  static const String kycVerify = '/api/kyc/verify';
  static const String kycMe = '/api/kyc/me';

  // Timeouts
  static const Duration connectTimeout = Duration(seconds: 15);
  static const Duration receiveTimeout = Duration(seconds: 20);
  static const Duration kycUploadTimeout = Duration(seconds: 60);
}
