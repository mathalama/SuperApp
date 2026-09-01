import 'package:flutter/foundation.dart';
import '../../../domain/entities/document_type.dart';

@immutable
abstract class KycEvent {
  const KycEvent();
}

class KycDocumentCapturedEvent extends KycEvent {
  final DocumentType documentType;
  final String frontImagePath;
  final String? backImagePath;

  const KycDocumentCapturedEvent({
    required this.documentType,
    required this.frontImagePath,
    this.backImagePath,
  });
}

class KycLivenessCapturedEvent extends KycEvent {
  final String selfieImagePath;
  const KycLivenessCapturedEvent({required this.selfieImagePath});
}

class KycProcessingStageAdvancedEvent extends KycEvent {
  final int stageIndex;
  const KycProcessingStageAdvancedEvent({required this.stageIndex});
}

class KycFetchStatusEvent extends KycEvent {}

class KycResetEvent extends KycEvent {}
