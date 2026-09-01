import '../entities/document_type.dart';
import '../entities/kyc_application_entity.dart';

abstract class IKycRepository {
  Future<KycApplicationEntity> submitKyc({
    required DocumentType documentType,
    required String frontImagePath,
    required String selfieImagePath,
    String? backImagePath,
  });

  Future<KycApplicationEntity> getMyKycStatus();
}
