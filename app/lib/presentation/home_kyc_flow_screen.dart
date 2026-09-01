import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import '../core/constants/app_colors.dart';
import '../domain/entities/user_entity.dart';
import 'bloc/auth/auth_bloc.dart';
import 'bloc/auth/auth_event.dart';
import 'bloc/kyc/kyc_bloc.dart';
import 'bloc/kyc/kyc_event.dart';
import 'bloc/kyc/kyc_state.dart';
import 'common/custom_alert.dart';
import 'common/kyc_stepper.dart';
import 'dashboard/verification_dashboard_view.dart';
import 'document_wizard/document_wizard_screen.dart';
import 'liveness/liveness_scanner_screen.dart';
import 'processing/verification_processing_view.dart';

class HomeKycFlowScreen extends StatelessWidget {
  final UserEntity user;

  const HomeKycFlowScreen({super.key, required this.user});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Container(
              width: 28,
              height: 28,
              decoration: BoxDecoration(
                color: AppColors.primaryLight,
                borderRadius: BorderRadius.circular(8),
              ),
              child: const Icon(Icons.shield_outlined, size: 16, color: AppColors.primary),
            ),
            const SizedBox(width: 8),
            const Text('SuperApp KYC'),
          ],
        ),
        actions: [
          // User Chip
          Container(
            margin: const EdgeInsets.only(right: 8),
            padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
            decoration: BoxDecoration(
              color: const Color(0xFFF1F5F9),
              borderRadius: BorderRadius.circular(20),
              border: Border.all(color: AppColors.border),
            ),
            child: Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                const Icon(Icons.person, size: 14, color: AppColors.textSecondary),
                const SizedBox(width: 4),
                Text(
                  user.username,
                  style: const TextStyle(fontSize: 12, fontWeight: FontWeight.w600, color: AppColors.textPrimary),
                ),
              ],
            ),
          ),
          // Logout Action
          IconButton(
            icon: const Icon(Icons.logout_rounded, size: 20, color: AppColors.rose),
            tooltip: 'Sign Out',
            onPressed: () {
              context.read<AuthBloc>().add(LogoutRequestedEvent());
            },
          ),
        ],
      ),
      body: BlocConsumer<KycBloc, KycState>(
        listener: (context, state) {
          if (state is KycErrorState) {
            ScaffoldMessenger.of(context).showSnackBar(
              SnackBar(
                content: Text(state.message),
                backgroundColor: AppColors.rose,
              ),
            );
          }
        },
        builder: (context, state) {
          int stepperIndex = 0;
          if (state is KycStepLivenessState) stepperIndex = 1;
          if (state is KycSubmittingState) stepperIndex = 1;
          if (state is KycResultState) stepperIndex = 2;

          return SafeArea(
            child: Column(
              children: [
                // Top Progress Stepper
                Padding(
                  padding: const EdgeInsets.only(left: 16, right: 16, top: 12),
                  child: KycStepper(currentStep: stepperIndex),
                ),

                // Error banner if any
                if (state is KycErrorState)
                  Padding(
                    padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 4),
                    child: CustomAlert(message: state.message, type: AlertType.error),
                  ),

                // Dynamic Screen View
                Expanded(
                  child: _buildCurrentStepView(context, state),
                ),
              ],
            ),
          );
        },
      ),
    );
  }

  Widget _buildCurrentStepView(BuildContext context, KycState state) {
    if (state is KycStepDocumentState) {
      return DocumentWizardScreen(
        onComplete: (docType, frontPath, backPath) {
          context.read<KycBloc>().add(
                KycDocumentCapturedEvent(
                  documentType: docType,
                  frontImagePath: frontPath,
                  backImagePath: backPath,
                ),
              );
        },
      );
    }

    if (state is KycStepLivenessState) {
      return LivenessScannerScreen(
        onCapture: (selfiePath) {
          context.read<KycBloc>().add(
                KycLivenessCapturedEvent(selfieImagePath: selfiePath),
              );
        },
        onBack: () {
          context.read<KycBloc>().add(KycResetEvent());
        },
      );
    }

    if (state is KycSubmittingState) {
      return const VerificationProcessingView();
    }

    if (state is KycResultState) {
      return VerificationDashboardView(
        result: state.result,
        onReset: () {
          context.read<KycBloc>().add(KycResetEvent());
        },
      );
    }

    // Default fallback
    return DocumentWizardScreen(
      onComplete: (docType, frontPath, backPath) {
        context.read<KycBloc>().add(
              KycDocumentCapturedEvent(
                documentType: docType,
                frontImagePath: frontPath,
                backImagePath: backPath,
              ),
            );
      },
    );
  }
}
