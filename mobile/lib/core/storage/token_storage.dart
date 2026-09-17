import 'package:flutter_secure_storage/flutter_secure_storage.dart';

abstract class ITokenStorage {
  Future<void> saveTokens({required String accessToken, required String refreshToken});
  Future<String?> getAccessToken();
  Future<String?> getRefreshToken();
  Future<void> clearTokens();
  Future<void> saveUserId(String userId);
  Future<String?> getUserId();
}

class TokenStorageImpl implements ITokenStorage {
  final FlutterSecureStorage _storage;

  static const String _keyAccessToken = 'app_access_token';
  static const String _keyRefreshToken = 'app_refresh_token';
  static const String _keyUserId = 'app_user_id';

  TokenStorageImpl({FlutterSecureStorage? storage})
      : _storage = storage ??
            const FlutterSecureStorage(
              aOptions: AndroidOptions(
                encryptedSharedPreferences: true,
              ),
            );

  @override
  Future<void> saveTokens({
    required String accessToken,
    required String refreshToken,
  }) async {
    await _storage.write(key: _keyAccessToken, value: accessToken);
    await _storage.write(key: _keyRefreshToken, value: refreshToken);
  }

  @override
  Future<String?> getAccessToken() async {
    return await _storage.read(key: _keyAccessToken);
  }

  @override
  Future<String?> getRefreshToken() async {
    return await _storage.read(key: _keyRefreshToken);
  }

  @override
  Future<void> clearTokens() async {
    await _storage.delete(key: _keyAccessToken);
    await _storage.delete(key: _keyRefreshToken);
    await _storage.delete(key: _keyUserId);
  }

  @override
  Future<void> saveUserId(String userId) async {
    await _storage.write(key: _keyUserId, value: userId);
  }

  @override
  Future<String?> getUserId() async {
    return await _storage.read(key: _keyUserId);
  }
}
