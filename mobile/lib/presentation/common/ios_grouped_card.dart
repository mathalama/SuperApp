import 'package:flutter/material.dart';
import '../../core/constants/app_colors.dart';
import 'ios_bounceable.dart';

/// Single item in an iOS Inset Grouped list
class IosGroupedItem {
  final Widget? leading;
  final String title;
  final String? subtitle;
  final Widget? trailing;
  final VoidCallback? onTap;
  final Color? titleColor;
  final bool showChevron;

  const IosGroupedItem({
    this.leading,
    required this.title,
    this.subtitle,
    this.trailing,
    this.onTap,
    this.titleColor,
    this.showChevron = true,
  });
}

/// Inset Grouped section container matching Apple Settings & Wallet style.
class IosGroupedSection extends StatelessWidget {
  final String? header;
  final String? footer;
  final List<IosGroupedItem> items;
  final EdgeInsetsGeometry margin;
  final double borderRadius;

  const IosGroupedSection({
    super.key,
    this.header,
    this.footer,
    required this.items,
    this.margin = const EdgeInsets.symmetric(horizontal: 16.0, vertical: 8.0),
    this.borderRadius = 18.0,
  });

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: margin,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          if (header != null)
            Padding(
              padding: const EdgeInsets.only(left: 16.0, bottom: 6.0),
              child: Text(
                header!.toUpperCase(),
                style: const TextStyle(
                  fontSize: 13,
                  fontWeight: FontWeight.w500,
                  letterSpacing: -0.2,
                  color: AppColors.textSecondary,
                ),
              ),
            ),
          Container(
            decoration: BoxDecoration(
              color: AppColors.bgSurface,
              borderRadius: BorderRadius.circular(borderRadius),
              boxShadow: [
                BoxShadow(
                  color: Colors.black.withValues(alpha: 0.03),
                  blurRadius: 16,
                  offset: const Offset(0, 4),
                ),
              ],
            ),
            child: ClipRRect(
              borderRadius: BorderRadius.circular(borderRadius),
              child: Column(
                children: List.generate(items.length, (index) {
                  final item = items[index];
                  final isLast = index == items.length - 1;

                  return Column(
                    children: [
                      IosBounceable(
                        onTap: item.onTap,
                        child: Container(
                          padding: const EdgeInsets.symmetric(horizontal: 16.0, vertical: 14.0),
                          color: Colors.transparent,
                          child: Row(
                            children: [
                              if (item.leading != null) ...[
                                item.leading!,
                                const SizedBox(width: 14),
                              ],
                              Expanded(
                                child: Column(
                                  crossAxisAlignment: CrossAxisAlignment.start,
                                  children: [
                                    Text(
                                      item.title,
                                      style: TextStyle(
                                        fontSize: 16,
                                        fontWeight: FontWeight.w500,
                                        letterSpacing: -0.3,
                                        color: item.titleColor ?? AppColors.textPrimary,
                                      ),
                                    ),
                                    if (item.subtitle != null) ...[
                                      const SizedBox(height: 2),
                                      Text(
                                        item.subtitle!,
                                        style: const TextStyle(
                                          fontSize: 13,
                                          fontWeight: FontWeight.w400,
                                          color: AppColors.textSecondary,
                                        ),
                                      ),
                                    ],
                                  ],
                                ),
                              ),
                              if (item.trailing != null) item.trailing!,
                              if (item.showChevron && item.onTap != null) ...[
                                const SizedBox(width: 6),
                                const Icon(
                                  Icons.chevron_right_rounded,
                                  size: 20,
                                  color: AppColors.textTertiary,
                                ),
                              ],
                            ],
                          ),
                        ),
                      ),
                      if (!isLast)
                        Padding(
                          padding: EdgeInsets.only(left: item.leading != null ? 54.0 : 16.0),
                          child: const Divider(
                            height: 0.5,
                            thickness: 0.5,
                            color: AppColors.border,
                          ),
                        ),
                    ],
                  );
                }),
              ),
            ),
          ),
          if (footer != null)
            Padding(
              padding: const EdgeInsets.only(left: 16.0, top: 6.0),
              child: Text(
                footer!,
                style: const TextStyle(
                  fontSize: 12.5,
                  fontWeight: FontWeight.w400,
                  color: AppColors.textSecondary,
                ),
              ),
            ),
        ],
      ),
    );
  }
}
