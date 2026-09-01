import 'kyc_status.dart';

class UserEntity {
  final String id;
  final String username;
  final String email;
  final List<String> roles;
  final KycStatus kycStatus;

  const UserEntity({
    required this.id,
    required this.username,
    required this.email,
    this.roles = const [],
    this.kycStatus = KycStatus.pending,
  });

  UserEntity copyWith({
    String? id,
    String? username,
    String? email,
    List<String>? roles,
    KycStatus? kycStatus,
  }) {
    return UserEntity(
      id: id ?? this.id,
      username: username ?? this.username,
      email: email ?? this.email,
      roles: roles ?? this.roles,
      kycStatus: kycStatus ?? this.kycStatus,
    );
  }
}
