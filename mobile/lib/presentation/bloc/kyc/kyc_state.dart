import 'package:flutter/foundation.dart';
import '../../../domain/entities/document_type.dart';
import '../../../domain/entities/kyc_application_entity.dart';

@immutable
abstract class KycState {
  const KycState();
}

class KycStepDocumentState extends KycState {}

class KycStepLivenessState extends KycState {
  final DocumentType documentType;
  final String frontImagePath;
  final String? backImagePath;

  const KycStepLivenessState({
    required this.documentType,
    required this.frontImagePath,
    this.backImagePath,
  });
}

class KycSubmittingState extends KycState {
  final int stageIndex;
  const KycSubmittingState({this.stageIndex = 0});
}

class KycResultState extends KycState {
  final KycApplicationEntity result;
  const KycResultState({required this.result});
}

class KycErrorState extends KycState {
  final String message;
  final KycState previousState;
  const KycErrorState({required this.message, required this.previousState});
}
