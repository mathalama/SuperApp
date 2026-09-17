import '../../core/security/security_service.dart';
import '../../domain/entities/document_type.dart';
import '../../domain/entities/kyc_application_entity.dart';
import '../../domain/repositories/i_kyc_repository.dart';
import '../datasources/kyc_remote_datasource.dart';
import '../mappers/kyc_mapper.dart';

class KycRepositoryImpl implements IKycRepository {
  final IKycRemoteDataSource _remoteDataSource;

  KycRepositoryImpl({required IKycRemoteDataSource remoteDataSource})
      : _remoteDataSource = remoteDataSource;

  @override
  Future<KycApplicationEntity> submitKyc({
    required DocumentType documentType,
    required String frontImagePath,
    required String selfieImagePath,
    String? backImagePath,
  }) async {
    try {
      final model = await _remoteDataSource.submitKyc(
        documentType: documentType.value,
        frontImagePath: frontImagePath,
        selfieImagePath: selfieImagePath,
        backImagePath: backImagePath,
      );
      return model.toDomain();
    } finally {
      // Clean up sensitive biometric & passport temp files from app disk
      await SecurityService.sanitizeTempFiles([
        frontImagePath,
        selfieImagePath,
        backImagePath,
      ]);
    }
  }

  @override
  Future<KycApplicationEntity> getMyKycStatus() async {
    final model = await _remoteDataSource.getMyKyc();
    return model.toDomain();
  }
}
