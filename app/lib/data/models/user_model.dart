class UserModel {
  final String id;
  final String username;
  final String email;
  final List<String> roles;
  final String? kycStatus;

  const UserModel({
    required this.id,
    required this.username,
    required this.email,
    this.roles = const [],
    this.kycStatus,
  });

  factory UserModel.fromJson(Map<String, dynamic> json) {
    return UserModel(
      id: json['id']?.toString() ?? '',
      username: json['username']?.toString() ?? '',
      email: json['email']?.toString() ?? '',
      roles: (json['roles'] as List<dynamic>?)?.map((e) => e.toString()).toList() ?? [],
      kycStatus: json['kycStatus']?.toString(),
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'id': id,
      'username': username,
      'email': email,
      'roles': roles,
      'kycStatus': kycStatus,
    };
  }
}
