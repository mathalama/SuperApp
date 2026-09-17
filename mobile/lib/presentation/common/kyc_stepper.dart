import 'package:flutter/material.dart';
import '../../core/constants/app_colors.dart';

class KycStepper extends StatelessWidget {
  final int currentStep; // 0: Document, 1: Selfie, 2: Result

  const KycStepper({
    super.key,
    required this.currentStep,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 14),
      margin: const EdgeInsets.only(bottom: 16),
      decoration: BoxDecoration(
        color: AppColors.bgSurface,
        borderRadius: BorderRadius.circular(14),
        border: Border.all(color: AppColors.border),
      ),
      child: Row(
        children: [
          _buildStep(
            index: 0,
            label: 'Document',
            isActive: currentStep == 0,
            isCompleted: currentStep > 0,
          ),
          _buildDivider(isCompleted: currentStep > 0),
          _buildStep(
            index: 1,
            label: 'Selfie',
            isActive: currentStep == 1,
            isCompleted: currentStep > 1,
          ),
          _buildDivider(isCompleted: currentStep > 1),
          _buildStep(
            index: 2,
            label: 'Result',
            isActive: currentStep == 2,
            isCompleted: currentStep >= 2,
          ),
        ],
      ),
    );
  }

  Widget _buildStep({
    required int index,
    required String label,
    required bool isActive,
    required bool isCompleted,
  }) {
    Color circleBg = const Color(0xFFF1F5F9);
    Color circleText = AppColors.textMuted;
    Color labelColor = AppColors.textMuted;
    FontWeight labelWeight = FontWeight.w500;

    if (isCompleted) {
      circleBg = AppColors.emerald;
      circleText = Colors.white;
      labelColor = AppColors.textPrimary;
      labelWeight = FontWeight.w600;
    } else if (isActive) {
      circleBg = AppColors.primary;
      circleText = Colors.white;
      labelColor = AppColors.primary;
      labelWeight = FontWeight.w700;
    }

    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        Container(
          width: 24,
          height: 24,
          decoration: BoxDecoration(
            color: circleBg,
            shape: BoxShape.circle,
          ),
          alignment: Alignment.center,
          child: isCompleted
              ? const Icon(Icons.check, size: 14, color: Colors.white)
              : Text(
                  '${index + 1}',
                  style: TextStyle(
                    color: circleText,
                    fontSize: 11,
                    fontWeight: FontWeight.w700,
                  ),
                ),
        ),
        const SizedBox(width: 6),
        Text(
          label,
          style: TextStyle(
            color: labelColor,
            fontSize: 13,
            fontWeight: labelWeight,
          ),
        ),
      ],
    );
  }

  Widget _buildDivider({required bool isCompleted}) {
    return Expanded(
      child: Container(
        height: 2,
        margin: const EdgeInsets.symmetric(horizontal: 8),
        color: isCompleted ? AppColors.emerald : AppColors.border,
      ),
    );
  }
}
