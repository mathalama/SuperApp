import 'dart:async';
import 'package:flutter/material.dart';
import '../../core/constants/app_colors.dart';

class ProcessingStage {
  final int id;
  final String label;
  final IconData icon;

  const ProcessingStage({
    required this.id,
    required this.label,
    required this.icon,
  });
}

class VerificationProcessingView extends StatefulWidget {
  const VerificationProcessingView({super.key});

  @override
  State<VerificationProcessingView> createState() => _VerificationProcessingViewState();
}

class _VerificationProcessingViewState extends State<VerificationProcessingView> {
  int _activeStageIndex = 0;
  Timer? _timer;

  static const List<ProcessingStage> stages = [
    ProcessingStage(id: 1, label: 'Document OCR & MRZ Recognition', icon: Icons.document_scanner_outlined),
    ProcessingStage(id: 2, label: 'Facial Feature Vector Extraction', icon: Icons.memory_outlined),
    ProcessingStage(id: 3, label: 'Anti-Spoof 3D Liveness Analysis', icon: Icons.fingerprint_outlined),
    ProcessingStage(id: 4, label: 'Synthesizing Verification Decision', icon: Icons.verified_user_outlined),
  ];

  @override
  void initState() {
    super.initState();
    _timer = Timer.periodic(const Duration(milliseconds: 1100), (t) {
      if (mounted) {
        setState(() {
          if (_activeStageIndex < stages.length - 1) {
            _activeStageIndex++;
          }
        });
      }
    });
  }

  @override
  void dispose() {
    _timer?.cancel();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Center(
      child: SingleChildScrollView(
        padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 24),
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 440),
          child: Container(
            decoration: BoxDecoration(
              color: AppColors.bgSurface,
              borderRadius: BorderRadius.circular(16),
              border: Border.all(color: AppColors.border),
              boxShadow: const [
                BoxShadow(
                  color: Color(0x0A0F172A),
                  blurRadius: 16,
                  offset: Offset(0, 4),
                )
              ],
            ),
            padding: const EdgeInsets.all(24),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                // Top Spinning Icon
                Container(
                  width: 56,
                  height: 56,
                  decoration: const BoxDecoration(
                    color: AppColors.primaryLight,
                    shape: BoxShape.circle,
                  ),
                  alignment: Alignment.center,
                  child: const SizedBox(
                    width: 28,
                    height: 28,
                    child: CircularProgressIndicator(
                      strokeWidth: 2.5,
                      valueColor: AlwaysStoppedAnimation<Color>(AppColors.primary),
                    ),
                  ),
                ),
                const SizedBox(height: 18),
                const Text(
                  'Verifying Identity...',
                  style: TextStyle(
                    fontSize: 18,
                    fontWeight: FontWeight.w800,
                    color: AppColors.textPrimary,
                    letterSpacing: -0.5,
                  ),
                ),
                const SizedBox(height: 4),
                const Text(
                  'Processing document OCR, facial vectors, and liveness frames.',
                  textAlign: TextAlign.center,
                  style: TextStyle(fontSize: 13, color: AppColors.textSecondary),
                ),
                const SizedBox(height: 24),

                // Checklist
                Column(
                  children: List.generate(stages.length, (idx) {
                    final stage = stages[idx];
                    final isDone = idx < _activeStageIndex;
                    final isCurrent = idx == _activeStageIndex;

                    Color itemBg = const Color(0xFFF8FAFC);
                    Color borderColor = AppColors.border;
                    Color textColor = AppColors.textSecondary;
                    FontWeight textWeight = FontWeight.w500;

                    if (isCurrent) {
                      itemBg = AppColors.primaryLight;
                      borderColor = AppColors.primaryBorder;
                      textColor = AppColors.primary;
                      textWeight = FontWeight.w700;
                    } else if (isDone) {
                      textColor = AppColors.emerald;
                      textWeight = FontWeight.w600;
                    }

                    return Container(
                      margin: const EdgeInsets.only(bottom: 8),
                      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
                      decoration: BoxDecoration(
                        color: itemBg,
                        borderRadius: BorderRadius.circular(10),
                        border: Border.all(color: borderColor),
                      ),
                      child: Row(
                        children: [
                          Container(
                            width: 22,
                            height: 22,
                            decoration: BoxDecoration(
                              shape: BoxShape.circle,
                              color: isDone
                                  ? AppColors.emerald
                                  : isCurrent
                                      ? AppColors.primary
                                      : const Color(0xFFE2E8F0),
                            ),
                            alignment: Alignment.center,
                            child: isDone
                                ? const Icon(Icons.check, size: 13, color: Colors.white)
                                : Text(
                                    '${idx + 1}',
                                    style: TextStyle(
                                      color: isCurrent ? Colors.white : AppColors.textMuted,
                                      fontSize: 11,
                                      fontWeight: FontWeight.w700,
                                    ),
                                  ),
                          ),
                          const SizedBox(width: 10),
                          Expanded(
                            child: Text(
                              stage.label,
                              style: TextStyle(
                                fontSize: 13,
                                fontWeight: textWeight,
                                color: textColor,
                              ),
                            ),
                          ),
                          Icon(
                            stage.icon,
                            size: 16,
                            color: isDone
                                ? AppColors.emerald
                                : isCurrent
                                    ? AppColors.primary
                                    : AppColors.textMuted,
                          ),
                        ],
                      ),
                    );
                  }),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
