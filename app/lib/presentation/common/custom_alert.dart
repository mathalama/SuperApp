import 'package:flutter/material.dart';
import '../../core/constants/app_colors.dart';

enum AlertType { error, warning, success, info }

class CustomAlert extends StatelessWidget {
  final String message;
  final AlertType type;
  final VoidCallback? onClose;

  const CustomAlert({
    super.key,
    required this.message,
    this.type = AlertType.error,
    this.onClose,
  });

  @override
  Widget build(BuildContext context) {
    Color bg;
    Color border;
    Color iconColor;
    IconData icon;

    switch (type) {
      case AlertType.error:
        bg = AppColors.roseLight;
        border = AppColors.roseBorder;
        iconColor = AppColors.rose;
        icon = Icons.error_outline_rounded;
        break;
      case AlertType.warning:
        bg = AppColors.amberLight;
        border = AppColors.amberBorder;
        iconColor = AppColors.amber;
        icon = Icons.warning_amber_rounded;
        break;
      case AlertType.success:
        bg = AppColors.emeraldLight;
        border = AppColors.emeraldBorder;
        iconColor = AppColors.emerald;
        icon = Icons.check_circle_outline_rounded;
        break;
      case AlertType.info:
        bg = AppColors.primaryLight;
        border = AppColors.primaryBorder;
        iconColor = AppColors.primary;
        icon = Icons.info_outline_rounded;
        break;
    }

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
      decoration: BoxDecoration(
        color: bg,
        borderRadius: BorderRadius.circular(10),
        border: Border.all(color: border),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Icon(icon, size: 18, color: iconColor),
          const SizedBox(width: 10),
          Expanded(
            child: Text(
              message,
              style: TextStyle(
                fontSize: 13,
                fontWeight: FontWeight.w500,
                color: iconColor,
                height: 1.35,
              ),
            ),
          ),
          if (onClose != null) ...[
            const SizedBox(width: 8),
            GestureDetector(
              onTap: onClose,
              child: Icon(Icons.close, size: 16, color: iconColor.withValues(alpha: 0.7)),
            ),
          ],
        ],
      ),
    );
  }
}
