import 'package:flutter/material.dart';

/// Apple iOS 17/18 Human Interface Guidelines Design Tokens
class AppColors {
  // Apple System Grays & Backgrounds
  static const Color bgBase = Color(0xFFF2F2F7); // iOS System Grouped Background
  static const Color bgSurface = Color(0xFFFFFFFF); // iOS System Background (Card)
  static const Color bgSecondary = Color(0xFFF8F8FA);
  static const Color bgInput = Color(0xFFF2F2F7); // iOS Inset Textfield Fill
  static const Color bgTertiary = Color(0xFFE5E5EA);

  // iOS Glassmorphism
  static const Color glassSurface = Color(0xCCFFFFFF); // 80% opacity white
  static const Color glassBorder = Color(0x66FFFFFF); // 40% white border
  static const Color glassDarkSurface = Color(0xB31C1C1E);
  static const Color glassDarkBorder = Color(0x33FFFFFF);

  // Apple System Typography
  static const Color textPrimary = Color(0xFF000000); // iOS Label
  static const Color textSecondary = Color(0xFF8E8E93); // iOS Secondary Label
  static const Color textTertiary = Color(0xFFC7C7CC); // iOS Tertiary Label
  static const Color textMuted = Color(0xFFAEAEB2);
  static const Color textInverse = Color(0xFFFFFFFF);

  // Apple System Accent Colors (iOS 17/18)
  static const Color iosBlue = Color(0xFF007AFF); // System Blue
  static const Color iosIndigo = Color(0xFF5856D6); // System Indigo
  static const Color iosPurple = Color(0xFFAF52DE); // System Purple
  static const Color iosPink = Color(0xFFFF2D55); // System Pink
  static const Color iosGreen = Color(0xFF34C759); // System Green
  static const Color iosOrange = Color(0xFFFF9500); // System Orange
  static const Color iosRed = Color(0xFFFF3B30); // System Red
  static const Color iosYellow = Color(0xFFFFCC00); // System Yellow
  static const Color iosTeal = Color(0xFF5AC8FA); // System Teal
  static const Color iosMint = Color(0xFF00C7BE); // System Mint

  // Primary Theme mappings (System Blue & Indigo Gradient)
  static const Color primary = Color(0xFF007AFF);
  static const Color primaryHover = Color(0xFF0056B3);
  static const Color primaryLight = Color(0xFFEBF5FF);
  static const Color primaryBorder = Color(0xFFB3D7FF);

  // Status Colors (Matching KYC Logic)
  static const Color emerald = Color(0xFF34C759); // iOS System Green
  static const Color emeraldHover = Color(0xFF28A745);
  static const Color emeraldLight = Color(0xFFE8F9EE);
  static const Color emeraldBorder = Color(0xFFA3E9B7);

  static const Color rose = Color(0xFFFF3B30); // iOS System Red
  static const Color roseHover = Color(0xFFD32F2F);
  static const Color roseLight = Color(0xFFFFECEB);
  static const Color roseBorder = Color(0xFFFFB3B0);

  static const Color amber = Color(0xFFFF9500); // iOS System Orange
  static const Color amberHover = Color(0xFFE08500);
  static const Color amberLight = Color(0xFFFFF4E5);
  static const Color amberBorder = Color(0xFFFFD499);

  // Dividers & Hairlines (Apple 0.5px style)
  static const Color border = Color(0xFFE5E5EA); // iOS Separator
  static const Color borderLight = Color(0x2E3C3C43); // iOS Translucent Separator
  static const Color borderFocus = Color(0xFF007AFF);
}
