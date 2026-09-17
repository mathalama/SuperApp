import 'package:flutter_test/flutter_test.dart';
import 'package:app/data/models/user_model.dart';
import 'package:app/data/models/kyc_response_model.dart';
import 'package:app/data/mappers/user_mapper.dart';
import 'package:app/data/mappers/kyc_mapper.dart';
import 'package:app/domain/entities/document_type.dart';
import 'package:app/domain/entities/kyc_status.dart';

void main() {
  group('Mappers Unit Tests', () {
    test('UserModelMapper correctly maps from UserModel to UserEntity', () {
      const userModel = UserModel(
        id: 'usr-123',
        username: 'johndoe',
        email: 'john@example.com',
        roles: ['USER', 'VERIFIED'],
        kycStatus: 'VERIFIED',
      );

      final entity = userModel.toDomain();

      expect(entity.id, 'usr-123');
      expect(entity.username, 'johndoe');
      expect(entity.email, 'john@example.com');
      expect(entity.roles, ['USER', 'VERIFIED']);
      expect(entity.kycStatus, KycStatus.verified);
    });

    test('KycResponseModelMapper correctly maps DTO to KycApplicationEntity', () {
      final kycModel = KycResponseModel(
        id: 'kyc-999',
        userId: 'usr-123',
        status: 'VERIFIED',
        documentType: 'PASSPORT',
        livenessScore: 0.985,
        faceMatchScore: 0.942,
        mrzValid: true,
        firstName: 'JOHN',
        lastName: 'DOE',
        documentNumber: 'N12345678',
        dateOfBirth: '1990-05-15',
        expiryDate: '2030-05-15',
        nationality: 'USA',
        rejectionReason: null,
        createdAt: '2026-08-30T10:00:00.000Z',
        updatedAt: '2026-08-30T10:01:30.000Z',
      );

      final entity = kycModel.toDomain();

      expect(entity.id, 'kyc-999');
      expect(entity.userId, 'usr-123');
      expect(entity.status, KycStatus.verified);
      expect(entity.documentType, DocumentType.passport);
      expect(entity.livenessScore, 0.985);
      expect(entity.faceMatchScore, 0.942);
      expect(entity.mrzValid, true);
      expect(entity.firstName, 'JOHN');
      expect(entity.documentNumber, 'N12345678');
      expect(entity.createdAt, isNotNull);
    });
  });
}
