import 'package:flutter/material.dart';
import '../../core/constants/app_colors.dart';
import '../../domain/entities/kyc_status.dart';
import 'ios_bounceable.dart';

/// Interactive Dynamic Island Pill matching iPhone 15/16 Pro Dynamic Island.
class DynamicIslandHeader extends StatefulWidget {
  final KycStatus status;
  final String username;
  final VoidCallback? onKycAction;

  const DynamicIslandHeader({
    super.key,
    required this.status,
    required this.username,
    this.onKycAction,
  });

  @override
  State<DynamicIslandHeader> createState() => _DynamicIslandHeaderState();
}

class _DynamicIslandHeaderState extends State<DynamicIslandHeader> {
  bool _isExpanded = false;

  Color get _statusColor {
    switch (widget.status) {
      case KycStatus.verified:
        return AppColors.iosGreen;
      case KycStatus.inProgress:
      case KycStatus.manualReview:
        return AppColors.iosOrange;
      case KycStatus.rejected:
        return AppColors.iosRed;
      case KycStatus.pending:
        return AppColors.iosBlue;
    }
  }

  IconData get _statusIcon {
    switch (widget.status) {
      case KycStatus.verified:
        return Icons.verified_user_rounded;
      case KycStatus.inProgress:
      case KycStatus.manualReview:
        return Icons.sync_rounded;
      case KycStatus.rejected:
        return Icons.error_outline_rounded;
      case KycStatus.pending:
        return Icons.shield_outlined;
    }
  }

  String get _statusText {
    switch (widget.status) {
      case KycStatus.verified:
        return 'Верифицирован';
      case KycStatus.inProgress:
        return 'Проверка';
      case KycStatus.manualReview:
        return 'Модерация';
      case KycStatus.rejected:
        return 'Отклонен';
      case KycStatus.pending:
        return _isExpanded ? 'Требуется верификация' : 'Требуется KYC';
    }
  }

  @override
  Widget build(BuildContext context) {
    return Center(
      child: IosBounceable(
        scaleFactor: 0.98,
        onTap: () {
          setState(() {
            _isExpanded = !_isExpanded;
          });
        },
        child: AnimatedContainer(
          duration: const Duration(milliseconds: 320),
          curve: Curves.fastOutSlowIn,
          width: _isExpanded ? 360 : 280,
          margin: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
          padding: EdgeInsets.symmetric(
            horizontal: _isExpanded ? 18 : 14,
            vertical: _isExpanded ? 14 : 9,
          ),
          decoration: BoxDecoration(
            color: const Color(0xFF0F0F12),
            borderRadius: BorderRadius.circular(_isExpanded ? 26 : 28),
            boxShadow: [
              BoxShadow(
                color: Colors.black.withValues(alpha: 0.18),
                blurRadius: 20,
                offset: const Offset(0, 6),
              ),
            ],
            border: Border.all(
              color: Colors.white.withValues(alpha: 0.15),
              width: 0.8,
            ),
          ),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              // Header row
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      // Glowing status dot
                      Container(
                        width: 10,
                        height: 10,
                        decoration: BoxDecoration(
                          color: _statusColor,
                          shape: BoxShape.circle,
                          boxShadow: [
                            BoxShadow(
                              color: _statusColor.withValues(alpha: 0.6),
                              blurRadius: 8,
                              spreadRadius: 1,
                            ),
                          ],
                        ),
                      ),
                      const SizedBox(width: 8),
                      const Text(
                        'SuperApp ID',
                        style: TextStyle(
                          color: Colors.white,
                          fontSize: 13,
                          fontWeight: FontWeight.w600,
                          letterSpacing: -0.2,
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(width: 8),
                  // Trailing status indicator
                  Flexible(
                    child: Container(
                      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                      decoration: BoxDecoration(
                        color: _statusColor.withValues(alpha: 0.18),
                        borderRadius: BorderRadius.circular(12),
                      ),
                      child: Row(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          Icon(_statusIcon, size: 12, color: _statusColor),
                          const SizedBox(width: 4),
                          Flexible(
                            child: Text(
                              _statusText,
                              overflow: TextOverflow.ellipsis,
                              maxLines: 1,
                              style: TextStyle(
                                color: _statusColor,
                                fontSize: 11,
                                fontWeight: FontWeight.w600,
                              ),
                            ),
                          ),
                        ],
                      ),
                    ),
                  ),
                ],
              ),

              // Expanded details
              if (_isExpanded) ...[
                const SizedBox(height: 14),
                const Divider(height: 1, color: Color(0x33FFFFFF)),
                const SizedBox(height: 12),
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        const Text(
                          'ПОЛЬЗОВАТЕЛЬ',
                          style: TextStyle(
                            fontSize: 10,
                            fontWeight: FontWeight.w600,
                            letterSpacing: 0.3,
                            color: Color(0xFF8E8E93),
                          ),
                        ),
                        const SizedBox(height: 2),
                        Text(
                          widget.username,
                          style: const TextStyle(
                            fontSize: 14,
                            fontWeight: FontWeight.w600,
                            color: Colors.white,
                          ),
                        ),
                      ],
                    ),
                    Column(
                      crossAxisAlignment: CrossAxisAlignment.end,
                      children: [
                        const Text(
                          'УРОВЕНЬ ЗАЩИТЫ',
                          style: TextStyle(
                            fontSize: 10,
                            fontWeight: FontWeight.w600,
                            letterSpacing: 0.3,
                            color: Color(0xFF8E8E93),
                          ),
                        ),
                        const SizedBox(height: 2),
                        Text(
                          widget.status == KycStatus.verified ? 'Tier 3 (Макс)' : 'Tier 1',
                          style: TextStyle(
                            fontSize: 14,
                            fontWeight: FontWeight.w600,
                            color: _statusColor,
                          ),
                        ),
                      ],
                    ),
                  ],
                ),
                if (widget.status != KycStatus.verified && widget.onKycAction != null) ...[
                  const SizedBox(height: 12),
                  SizedBox(
                    width: double.infinity,
                    height: 38,
                    child: ElevatedButton(
                      onPressed: widget.onKycAction,
                      style: ElevatedButton.styleFrom(
                        backgroundColor: AppColors.iosBlue,
                        foregroundColor: Colors.white,
                        elevation: 0,
                        shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(12),
                        ),
                        padding: EdgeInsets.zero,
                      ),
                      child: const Text(
                        'Пройти верификацию',
                        style: TextStyle(fontSize: 13.5, fontWeight: FontWeight.w600),
                      ),
                    ),
                  ),
                ],
              ],
            ],
          ),
        ),
      ),
    );
  }
}
