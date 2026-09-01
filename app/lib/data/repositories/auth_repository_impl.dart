import '../../core/storage/token_storage.dart';
import '../../domain/entities/user_entity.dart';
import '../../domain/repositories/i_auth_repository.dart';
import '../datasources/auth_remote_datasource.dart';
import '../mappers/user_mapper.dart';

class AuthRepositoryImpl implements IAuthRepository {
  final IAuthRemoteDataSource _remoteDataSource;
  final ITokenStorage _tokenStorage;
  UserEntity? _cachedUser;

  AuthRepositoryImpl({
    required IAuthRemoteDataSource remoteDataSource,
    required ITokenStorage tokenStorage,
  })  : _remoteDataSource = remoteDataSource,
        _tokenStorage = tokenStorage;

  @override
  Future<void> register({
    required String username,
    required String email,
    required String password,
  }) async {
    await _remoteDataSource.register(
      username: username,
      email: email,
      password: password,
    );
  }

  @override
  Future<bool> verifyEmail({
    required String email,
    required String code,
  }) async {
    final res = await _remoteDataSource.verifyEmail(email: email, code: code);
    return res['verified'] == true;
  }

  @override
  Future<void> resendVerification({
    required String email,
  }) async {
    await _remoteDataSource.resendVerification(email: email);
  }

  @override
  Future<void> forgotPassword({
    required String email,
  }) async {
    await _remoteDataSource.forgotPassword(email: email);
  }

  @override
  Future<void> resetPassword({
    required String token,
    required String newPassword,
  }) async {
    await _remoteDataSource.resetPassword(token: token, newPassword: newPassword);
  }

  @override
  Future<UserEntity> login({
    required String login,
    required String password,
  }) async {
    final response = await _remoteDataSource.login(login: login, password: password);
    await _tokenStorage.saveTokens(
      accessToken: response.accessToken,
      refreshToken: response.refreshToken,
    );
    await _tokenStorage.saveUserId(response.user.id);
    _cachedUser = response.user.toDomain();
    return _cachedUser!;
  }

  @override
  Future<void> logout() async {
    await _tokenStorage.clearTokens();
    _cachedUser = null;
  }

  @override
  Future<bool> isAuthenticated() async {
    final token = await _tokenStorage.getAccessToken();
    return token != null && token.isNotEmpty;
  }

  @override
  Future<UserEntity?> getCurrentUser() async {
    return _cachedUser;
  }
}
