import '../../domain/entities/kyc_status.dart';
import '../../domain/entities/user_entity.dart';
import '../models/user_model.dart';

extension UserModelMapper on UserModel {
  UserEntity toDomain() {
    return UserEntity(
      id: id,
      username: username,
      email: email,
      roles: roles,
      kycStatus: KycStatus.fromString(kycStatus),
    );
  }
}

extension UserEntityMapper on UserEntity {
  UserModel toModel() {
    return UserModel(
      id: id,
      username: username,
      email: email,
      roles: roles,
      kycStatus: kycStatus.value,
    );
  }
}
