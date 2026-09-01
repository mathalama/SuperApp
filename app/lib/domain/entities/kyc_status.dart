enum KycStatus {
  pending('PENDING', 'Pending Verification'),
  inProgress('IN_PROGRESS', 'In Progress'),
  verified('VERIFIED', 'Verified'),
  rejected('REJECTED', 'Rejected'),
  manualReview('MANUAL_REVIEW', 'Under Manual Review');

  final String value;
  final String label;
  const KycStatus(this.value, this.label);

  static KycStatus fromString(String? val) {
    if (val == null) return KycStatus.pending;
    return KycStatus.values.firstWhere(
      (e) => e.value.toUpperCase() == val.toUpperCase(),
      orElse: () => KycStatus.pending,
    );
  }
}
