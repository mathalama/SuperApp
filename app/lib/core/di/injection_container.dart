import 'package:get_it/get_it.dart';
import '../network/api_client.dart';
import '../storage/token_storage.dart';
import '../../data/datasources/auth_remote_datasource.dart';
import '../../data/datasources/kyc_remote_datasource.dart';
import '../../data/repositories/auth_repository_impl.dart';
import '../../data/repositories/kyc_repository_impl.dart';
import '../../domain/repositories/i_auth_repository.dart';
import '../../domain/repositories/i_kyc_repository.dart';

final sl = GetIt.instance;

Future<void> initDependencies({String? baseUrl, void Function()? onSessionExpired}) async {
  // Storage
  sl.registerLazySingleton<ITokenStorage>(() => TokenStorageImpl());

  // Network Client
  sl.registerLazySingleton<ApiClient>(
    () => ApiClient(
      tokenStorage: sl<ITokenStorage>(),
      baseUrl: baseUrl,
      onSessionExpired: onSessionExpired,
    ),
  );

  // Data Sources
  sl.registerLazySingleton<IAuthRemoteDataSource>(
    () => AuthRemoteDataSourceImpl(apiClient: sl<ApiClient>()),
  );
  sl.registerLazySingleton<IKycRemoteDataSource>(
    () => KycRemoteDataSourceImpl(apiClient: sl<ApiClient>()),
  );

  // Repositories
  sl.registerLazySingleton<IAuthRepository>(
    () => AuthRepositoryImpl(
      remoteDataSource: sl<IAuthRemoteDataSource>(),
      tokenStorage: sl<ITokenStorage>(),
    ),
  );
  sl.registerLazySingleton<IKycRepository>(
    () => KycRepositoryImpl(
      remoteDataSource: sl<IKycRemoteDataSource>(),
    ),
  );
}
