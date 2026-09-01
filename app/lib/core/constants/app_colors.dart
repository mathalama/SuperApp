import 'package:flutter/material.dart';

/// Design tokens matching index.css
class AppColors {
  // Base Surface & Backgrounds
  static const Color bgBase = Color(0xFFF8FAFC);
  static const Color bgSurface = Color(0xFFFFFFFF);
  static const Color bgInput = Color(0xFFFFFFFF);
  
  // Typography Colors
  static const Color textPrimary = Color(0xFF0F172A);
  static const Color textSecondary = Color(0xFF475569);
  static const Color textMuted = Color(0xFF94A3B8);
  static const Color textInverse = Color(0xFFFFFFFF);

  // Primary Accent (Modern Indigo)
  static const Color primary = Color(0xFF4F46E5);
  static const Color primaryHover = Color(0xFF4338CA);
  static const Color primaryLight = Color(0xFFEEF2FF);
  static const Color primaryBorder = Color(0xFFC7D2FE);

  // Status Emerald (Verified)
  static const Color emerald = Color(0xFF059669);
  static const Color emeraldHover = Color(0xFF047857);
  static const Color emeraldLight = Color(0xFFECFDF5);
  static const Color emeraldBorder = Color(0xFFA7F3D0);

  // Status Rose (Rejected / Error)
  static const Color rose = Color(0xFFE11D48);
  static const Color roseHover = Color(0xFFBE123C);
  static const Color roseLight = Color(0xFFFFF1F2);
  static const Color roseBorder = Color(0xFFFECDD3);

  // Status Amber (Warning / Pending)
  static const Color amber = Color(0xFFD97706);
  static const Color amberHover = Color(0xFFB45309);
  static const Color amberLight = Color(0xFFFFFBEB);
  static const Color amberBorder = Color(0xFFFDE68A);

  // Borders & Dividers
  static const Color border = Color(0xFFE2E8F0);
  static const Color borderLight = Color(0xFFCBD5E1);
  static const Color borderFocus = Color(0xFF4F46E5);
}
