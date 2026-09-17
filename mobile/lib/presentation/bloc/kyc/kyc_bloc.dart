import 'package:flutter_bloc/flutter_bloc.dart';
import '../../../domain/entities/document_type.dart';
import '../../../domain/repositories/i_kyc_repository.dart';
import 'kyc_event.dart';
import 'kyc_state.dart';

class KycBloc extends Bloc<KycEvent, KycState> {
  final IKycRepository _kycRepository;

  DocumentType? _selectedDocType;
  String? _frontImagePath;
  String? _backImagePath;

  KycBloc({required IKycRepository kycRepository})
      : _kycRepository = kycRepository,
        super(KycStepDocumentState()) {
    on<KycDocumentCapturedEvent>(_onDocumentCaptured);
    on<KycLivenessCapturedEvent>(_onLivenessCaptured);
    on<KycProcessingStageAdvancedEvent>(_onStageAdvanced);
    on<KycFetchStatusEvent>(_onFetchStatus);
    on<KycResetEvent>(_onReset);
  }

  void _onDocumentCaptured(
    KycDocumentCapturedEvent event,
    Emitter<KycState> emit,
  ) {
    _selectedDocType = event.documentType;
    _frontImagePath = event.frontImagePath;
    _backImagePath = event.backImagePath;

    emit(KycStepLivenessState(
      documentType: event.documentType,
      frontImagePath: event.frontImagePath,
      backImagePath: event.backImagePath,
    ));
  }

  Future<void> _onLivenessCaptured(
    KycLivenessCapturedEvent event,
    Emitter<KycState> emit,
  ) async {
    if (_selectedDocType == null || _frontImagePath == null) {
      emit(KycErrorState(
        message: 'Missing document photos. Please start over.',
        previousState: KycStepDocumentState(),
      ));
      return;
    }

    emit(const KycSubmittingState(stageIndex: 0));

    try {
      final result = await _kycRepository.submitKyc(
        documentType: _selectedDocType!,
        frontImagePath: _frontImagePath!,
        selfieImagePath: event.selfieImagePath,
        backImagePath: _backImagePath,
      );

      emit(KycResultState(result: result));
    } catch (e) {
      emit(KycErrorState(
        message: e.toString(),
        previousState: KycStepLivenessState(
          documentType: _selectedDocType!,
          frontImagePath: _frontImagePath!,
          backImagePath: _backImagePath,
        ),
      ));
    }
  }

  void _onStageAdvanced(
    KycProcessingStageAdvancedEvent event,
    Emitter<KycState> emit,
  ) {
    if (state is KycSubmittingState) {
      emit(KycSubmittingState(stageIndex: event.stageIndex));
    }
  }

  Future<void> _onFetchStatus(
    KycFetchStatusEvent event,
    Emitter<KycState> emit,
  ) async {
    try {
      final result = await _kycRepository.getMyKycStatus();
      emit(KycResultState(result: result));
    } catch (e) {
      emit(KycErrorState(
        message: e.toString(),
        previousState: state,
      ));
    }
  }

  void _onReset(
    KycResetEvent event,
    Emitter<KycState> emit,
  ) {
    _selectedDocType = null;
    _frontImagePath = null;
    _backImagePath = null;
    emit(KycStepDocumentState());
  }
}
