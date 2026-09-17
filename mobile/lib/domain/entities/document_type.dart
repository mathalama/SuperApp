enum DocumentType {
  passport('PASSPORT', 'Passport'),
  idCard('ID_CARD', 'National ID Card'),
  drivingLicense('DRIVING_LICENSE', 'Driver License');

  final String value;
  final String label;
  const DocumentType(this.value, this.label);

  static DocumentType fromString(String val) {
    return DocumentType.values.firstWhere(
      (e) => e.value.toUpperCase() == val.toUpperCase(),
      orElse: () => DocumentType.idCard,
    );
  }
}
