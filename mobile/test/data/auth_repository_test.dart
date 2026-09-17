import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:app/core/storage/token_storage.dart';
import 'package:app/data/datasources/auth_remote_datasource.dart';
import 'package:app/data/models/auth_response_model.dart';
import 'package:app/data/models/user_model.dart';
import 'package:app/data/repositories/auth_repository_impl.dart';
import 'package:app/domain/entities/kyc_status.dart';

class MockAuthRemoteDataSource extends Mock implements IAuthRemoteDataSource {}
class MockTokenStorage extends Mock implements ITokenStorage {}

void main() {
  late MockAuthRemoteDataSource mockDataSource;
  late MockTokenStorage mockTokenStorage;
  late AuthRepositoryImpl repository;

  setUp(() {
    mockDataSource = MockAuthRemoteDataSource();
    mockTokenStorage = MockTokenStorage();
    repository = AuthRepositoryImpl(
      remoteDataSource: mockDataSource,
      tokenStorage: mockTokenStorage,
    );
  });

  group('AuthRepositoryImpl Tests', () {
    test('login saves tokens and returns UserEntity on success', () async {
      const mockAuthResponse = AuthResponseModel(
        accessToken: 'access-jwt-token',
        refreshToken: 'refresh-jwt-token',
        user: UserModel(
          id: 'user-001',
          username: 'alex',
          email: 'alex@example.com',
          kycStatus: 'VERIFIED',
        ),
      );

      when(() => mockDataSource.login(login: 'alex', password: 'password123'))
          .thenAnswer((_) async => mockAuthResponse);
      when(() => mockTokenStorage.saveTokens(
            accessToken: 'access-jwt-token',
            refreshToken: 'refresh-jwt-token',
          )).thenAnswer((_) async {});
      when(() => mockTokenStorage.saveUserId('user-001'))
          .thenAnswer((_) async {});

      final result = await repository.login(login: 'alex', password: 'password123');

      expect(result.id, 'user-001');
      expect(result.username, 'alex');
      expect(result.kycStatus, KycStatus.verified);

      verify(() => mockDataSource.login(login: 'alex', password: 'password123')).called(1);
      verify(() => mockTokenStorage.saveTokens(
            accessToken: 'access-jwt-token',
            refreshToken: 'refresh-jwt-token',
          )).called(1);
      verify(() => mockTokenStorage.saveUserId('user-001')).called(1);
    });

    test('logout clears tokens from storage', () async {
      when(() => mockTokenStorage.clearTokens()).thenAnswer((_) async {});

      await repository.logout();

      verify(() => mockTokenStorage.clearTokens()).called(1);
      expect(await repository.getCurrentUser(), isNull);
    });
  });
}
