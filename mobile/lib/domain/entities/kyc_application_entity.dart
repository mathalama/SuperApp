import 'document_type.dart';
import 'kyc_status.dart';

class KycApplicationEntity {
  final String id;
  final String userId;
  final KycStatus status;
  final DocumentType documentType;
  final double? livenessScore;
  final double? faceMatchScore;
  final bool? mrzValid;
  final String? firstName;
  final String? lastName;
  final String? documentNumber;
  final String? dateOfBirth;
  final String? expiryDate;
  final String? nationality;
  final String? rejectionReason;
  final DateTime? createdAt;
  final DateTime? updatedAt;

  const KycApplicationEntity({
    required this.id,
    required this.userId,
    required this.status,
    required this.documentType,
    this.livenessScore,
    this.faceMatchScore,
    this.mrzValid,
    this.firstName,
    this.lastName,
    this.documentNumber,
    this.dateOfBirth,
    this.expiryDate,
    this.nationality,
    this.rejectionReason,
    this.createdAt,
    this.updatedAt,
  });
}
