class KycResponseModel {
  final String id;
  final String userId;
  final String status;
  final String documentType;
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
  final String? createdAt;
  final String? updatedAt;

  const KycResponseModel({
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

  factory KycResponseModel.fromJson(Map<String, dynamic> json) {
    return KycResponseModel(
      id: json['id']?.toString() ?? '',
      userId: json['userId']?.toString() ?? '',
      status: json['status']?.toString() ?? 'PENDING',
      documentType: json['documentType']?.toString() ?? 'ID_CARD',
      livenessScore: (json['livenessScore'] as num?)?.toDouble(),
      faceMatchScore: (json['faceMatchScore'] as num?)?.toDouble(),
      mrzValid: json['mrzValid'] as bool?,
      firstName: json['firstName']?.toString(),
      lastName: json['lastName']?.toString(),
      documentNumber: json['documentNumber']?.toString(),
      dateOfBirth: json['dateOfBirth']?.toString(),
      expiryDate: json['expiryDate']?.toString(),
      nationality: json['nationality']?.toString(),
      rejectionReason: json['rejectionReason']?.toString(),
      createdAt: json['createdAt']?.toString(),
      updatedAt: json['updatedAt']?.toString(),
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'id': id,
      'userId': userId,
      'status': status,
      'documentType': documentType,
      'livenessScore': livenessScore,
      'faceMatchScore': faceMatchScore,
      'mrzValid': mrzValid,
      'firstName': firstName,
      'lastName': lastName,
      'documentNumber': documentNumber,
      'dateOfBirth': dateOfBirth,
      'expiryDate': expiryDate,
      'nationality': nationality,
      'rejectionReason': rejectionReason,
      'createdAt': createdAt,
      'updatedAt': updatedAt,
    };
  }
}
