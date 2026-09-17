import 'package:flutter/material.dart';
import '../../core/constants/app_colors.dart';
import '../../core/security/security_service.dart';
import '../../core/theme/kyc_status_theme_extension.dart';
import '../../domain/entities/kyc_application_entity.dart';
import '../../domain/entities/kyc_status.dart';
import '../common/app_button.dart';

class VerificationDashboardView extends StatefulWidget {
  final KycApplicationEntity result;
  final VoidCallback onReset;

  const VerificationDashboardView({
    super.key,
    required this.result,
    required this.onReset,
  });

  @override
  State<VerificationDashboardView> createState() => _VerificationDashboardViewState();
}

class _VerificationDashboardViewState extends State<VerificationDashboardView> {
  @override
  void initState() {
    super.initState();
    SecurityService.enableSecureMode();
  }

  @override
  Widget build(BuildContext context) {
    final statusColors = Theme.of(context).extension<KycStatusColors>() ?? KycStatusColors.light;
    final status = widget.result.status;

    Color bgBadge;
    Color borderBadge;
    Color textBadge;
    IconData iconBadge;

    if (status == KycStatus.verified) {
      bgBadge = statusColors.verifiedBg;
      borderBadge = statusColors.verifiedBorder;
      textBadge = statusColors.verifiedText;
      iconBadge = Icons.verified_rounded;
    } else if (status == KycStatus.rejected) {
      bgBadge = statusColors.rejectedBg;
      borderBadge = statusColors.rejectedBorder;
      textBadge = statusColors.rejectedText;
      iconBadge = Icons.cancel_rounded;
    } else {
      bgBadge = statusColors.pendingBg;
      borderBadge = statusColors.pendingBorder;
      textBadge = statusColors.pendingText;
      iconBadge = Icons.hourglass_top_rounded;
    }

    return SingleChildScrollView(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
      child: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: 500),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // Status Header Card
            Container(
              width: double.infinity,
              padding: const EdgeInsets.all(20),
              decoration: BoxDecoration(
                color: bgBadge,
                borderRadius: BorderRadius.circular(16),
                border: Border.all(color: borderBadge),
              ),
              child: Row(
                children: [
                  Icon(iconBadge, size: 36, color: textBadge),
                  const SizedBox(width: 14),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          status.label,
                          style: TextStyle(
                            fontSize: 18,
                            fontWeight: FontWeight.w800,
                            color: textBadge,
                            letterSpacing: -0.5,
                          ),
                        ),
                        const SizedBox(height: 2),
                        Text(
                          status == KycStatus.verified
                              ? 'Your identity has been successfully verified.'
                              : status == KycStatus.rejected
                                  ? (widget.result.rejectionReason ?? 'Verification checks failed.')
                                  : 'Your application is under compliance review.',
                          style: TextStyle(
                            fontSize: 13,
                            color: textBadge.withValues(alpha: 0.85),
                          ),
                        ),
                      ],
                    ),
                  ),
                ],
              ),
            ),
            const SizedBox(height: 16),

            // AI Confidence Score Cards
            Row(
              children: [
                Expanded(
                  child: _buildScoreCard(
                    title: 'Liveness',
                    score: widget.result.livenessScore,
                    icon: Icons.fingerprint,
                  ),
                ),
                const SizedBox(width: 10),
                Expanded(
                  child: _buildScoreCard(
                    title: 'Face Match',
                    score: widget.result.faceMatchScore,
                    icon: Icons.face_retouching_natural,
                  ),
                ),
                const SizedBox(width: 10),
                Expanded(
                  child: _buildScoreCard(
                    title: 'MRZ Check',
                    isValid: widget.result.mrzValid,
                    icon: Icons.document_scanner,
                  ),
                ),
              ],
            ),
            const SizedBox(height: 16),

            // Extracted OCR Passport / ID Details
            Container(
              width: double.infinity,
              padding: const EdgeInsets.all(18),
              decoration: BoxDecoration(
                color: AppColors.bgSurface,
                borderRadius: BorderRadius.circular(16),
                border: Border.all(color: AppColors.border),
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Row(
                    children: [
                      Icon(Icons.badge_outlined, size: 18, color: AppColors.primary),
                      SizedBox(width: 8),
                      Text(
                        'Extracted Document Data',
                        style: TextStyle(
                          fontSize: 15,
                          fontWeight: FontWeight.w700,
                          color: AppColors.textPrimary,
                        ),
                      ),
                    ],
                  ),
                  const Divider(height: 24, color: AppColors.border),
                  _buildDataRow('Document Type', widget.result.documentType.label),
                  _buildDataRow('Document Number', widget.result.documentNumber ?? '—'),
                  _buildDataRow('First Name', widget.result.firstName ?? '—'),
                  _buildDataRow('Last Name', widget.result.lastName ?? '—'),
                  _buildDataRow('Date of Birth', widget.result.dateOfBirth ?? '—'),
                  _buildDataRow('Expiration Date', widget.result.expiryDate ?? '—'),
                  _buildDataRow('Nationality', widget.result.nationality ?? '—'),
                ],
              ),
            ),
            const SizedBox(height: 20),

            // Reset / Rescan Action Button
            AppButton(
              text: 'Start New Verification',
              icon: Icons.refresh,
              variant: AppButtonVariant.outline,
              onPressed: widget.onReset,
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildScoreCard({
    required String title,
    double? score,
    bool? isValid,
    required IconData icon,
  }) {
    String scoreText = '—';
    Color valueColor = AppColors.textPrimary;

    if (score != null) {
      final percent = (score * 100).toInt();
      scoreText = '$percent%';
      valueColor = percent >= 80 ? AppColors.emerald : AppColors.amber;
    } else if (isValid != null) {
      scoreText = isValid ? 'PASS' : 'FAIL';
      valueColor = isValid ? AppColors.emerald : AppColors.rose;
    }

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 12),
      decoration: BoxDecoration(
        color: AppColors.bgSurface,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: AppColors.border),
      ),
      child: Column(
        children: [
          Icon(icon, size: 18, color: AppColors.primary),
          const SizedBox(height: 6),
          Text(
            title,
            style: const TextStyle(fontSize: 11, color: AppColors.textSecondary, fontWeight: FontWeight.w500),
          ),
          const SizedBox(height: 2),
          Text(
            scoreText,
            style: TextStyle(fontSize: 15, fontWeight: FontWeight.w800, color: valueColor),
          ),
        ],
      ),
    );
  }

  Widget _buildDataRow(String label, String value) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 8),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Text(
            label,
            style: const TextStyle(fontSize: 13, color: AppColors.textSecondary),
          ),
          Text(
            value,
            style: const TextStyle(
              fontSize: 13,
              fontWeight: FontWeight.w600,
              color: AppColors.textPrimary,
            ),
          ),
        ],
      ),
    );
  }
}
