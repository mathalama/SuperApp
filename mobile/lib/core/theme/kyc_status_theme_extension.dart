import 'package:flutter/material.dart';
import '../constants/app_colors.dart';

@immutable
class KycStatusColors extends ThemeExtension<KycStatusColors> {
  final Color verifiedBg;
  final Color verifiedBorder;
  final Color verifiedText;

  final Color rejectedBg;
  final Color rejectedBorder;
  final Color rejectedText;

  final Color pendingBg;
  final Color pendingBorder;
  final Color pendingText;

  const KycStatusColors({
    required this.verifiedBg,
    required this.verifiedBorder,
    required this.verifiedText,
    required this.rejectedBg,
    required this.rejectedBorder,
    required this.rejectedText,
    required this.pendingBg,
    required this.pendingBorder,
    required this.pendingText,
  });

  static const light = KycStatusColors(
    verifiedBg: AppColors.emeraldLight,
    verifiedBorder: AppColors.emeraldBorder,
    verifiedText: AppColors.emerald,
    rejectedBg: AppColors.roseLight,
    rejectedBorder: AppColors.roseBorder,
    rejectedText: AppColors.rose,
    pendingBg: AppColors.amberLight,
    pendingBorder: AppColors.amberBorder,
    pendingText: AppColors.amber,
  );

  @override
  KycStatusColors copyWith({
    Color? verifiedBg,
    Color? verifiedBorder,
    Color? verifiedText,
    Color? rejectedBg,
    Color? rejectedBorder,
    Color? rejectedText,
    Color? pendingBg,
    Color? pendingBorder,
    Color? pendingText,
  }) {
    return KycStatusColors(
      verifiedBg: verifiedBg ?? this.verifiedBg,
      verifiedBorder: verifiedBorder ?? this.verifiedBorder,
      verifiedText: verifiedText ?? this.verifiedText,
      rejectedBg: rejectedBg ?? this.rejectedBg,
      rejectedBorder: rejectedBorder ?? this.rejectedBorder,
      rejectedText: rejectedText ?? this.rejectedText,
      pendingBg: pendingBg ?? this.pendingBg,
      pendingBorder: pendingBorder ?? this.pendingBorder,
      pendingText: pendingText ?? this.pendingText,
    );
  }

  @override
  KycStatusColors lerp(ThemeExtension<KycStatusColors>? other, double t) {
    if (other is! KycStatusColors) return this;
    return KycStatusColors(
      verifiedBg: Color.lerp(verifiedBg, other.verifiedBg, t)!,
      verifiedBorder: Color.lerp(verifiedBorder, other.verifiedBorder, t)!,
      verifiedText: Color.lerp(verifiedText, other.verifiedText, t)!,
      rejectedBg: Color.lerp(rejectedBg, other.rejectedBg, t)!,
      rejectedBorder: Color.lerp(rejectedBorder, other.rejectedBorder, t)!,
      rejectedText: Color.lerp(rejectedText, other.rejectedText, t)!,
      pendingBg: Color.lerp(pendingBg, other.pendingBg, t)!,
      pendingBorder: Color.lerp(pendingBorder, other.pendingBorder, t)!,
      pendingText: Color.lerp(pendingText, other.pendingText, t)!,
    );
  }
}
