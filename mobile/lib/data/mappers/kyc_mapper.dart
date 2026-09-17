import '../../domain/entities/document_type.dart';
import '../../domain/entities/kyc_application_entity.dart';
import '../../domain/entities/kyc_status.dart';
import '../models/kyc_response_model.dart';

extension KycResponseModelMapper on KycResponseModel {
  KycApplicationEntity toDomain() {
    return KycApplicationEntity(
      id: id,
      userId: userId,
      status: KycStatus.fromString(status),
      documentType: DocumentType.fromString(documentType),
      livenessScore: livenessScore,
      faceMatchScore: faceMatchScore,
      mrzValid: mrzValid,
      firstName: firstName,
      lastName: lastName,
      documentNumber: documentNumber,
      dateOfBirth: dateOfBirth,
      expiryDate: expiryDate,
      nationality: nationality,
      rejectionReason: rejectionReason,
      createdAt: createdAt != null ? DateTime.tryParse(createdAt!) : null,
      updatedAt: updatedAt != null ? DateTime.tryParse(updatedAt!) : null,
    );
  }
}
