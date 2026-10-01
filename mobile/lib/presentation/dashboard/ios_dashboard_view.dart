import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart';
import '../../core/constants/app_colors.dart';
import '../../domain/entities/kyc_status.dart';
import '../../domain/entities/user_entity.dart';
import '../common/ios_bounceable.dart';
import '../common/ios_glass_card.dart';
import '../common/ios_grouped_card.dart';

class IosDashboardView extends StatelessWidget {
  final UserEntity user;
  final VoidCallback onStartKyc;
  final VoidCallback onOpenDocuments;
  final VoidCallback onOpenProfile;

  const IosDashboardView({
    super.key,
    required this.user,
    required this.onStartKyc,
    required this.onOpenDocuments,
    required this.onOpenProfile,
  });

  String get _greeting {
    final hour = DateTime.now().hour;
    if (hour < 6) return 'Доброй ночи';
    if (hour < 12) return 'Доброе утро';
    if (hour < 18) return 'Добрый день';
    return 'Добрый вечер';
  }

  @override
  Widget build(BuildContext context) {
    final isVerified = user.kycStatus == KycStatus.verified;

    return CustomScrollView(
      physics: const BouncingScrollPhysics(),
      slivers: [
        // iOS Large Title Header
        SliverToBoxAdapter(
          child: Padding(
            padding: const EdgeInsets.fromLTRB(20, 16, 20, 12),
            child: Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              crossAxisAlignment: CrossAxisAlignment.end,
              children: [
                Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      _greeting,
                      style: const TextStyle(
                        fontSize: 13,
                        fontWeight: FontWeight.w500,
                        color: AppColors.textSecondary,
                        letterSpacing: -0.2,
                      ),
                    ),
                    const SizedBox(height: 2),
                    Text(
                      user.username.isNotEmpty ? user.username : 'Пользователь',
                      style: const TextStyle(
                        fontSize: 30,
                        fontWeight: FontWeight.w700,
                        color: AppColors.textPrimary,
                        letterSpacing: -0.8,
                      ),
                    ),
                  ],
                ),
                // Apple ID Avatar Button
                IosBounceable(
                  onTap: onOpenProfile,
                  child: Container(
                    width: 44,
                    height: 44,
                    decoration: BoxDecoration(
                      gradient: const LinearGradient(
                        colors: [AppColors.iosBlue, AppColors.iosIndigo],
                        begin: Alignment.topLeft,
                        end: Alignment.bottomRight,
                      ),
                      shape: BoxShape.circle,
                      boxShadow: [
                        BoxShadow(
                          color: AppColors.iosBlue.withValues(alpha: 0.3),
                          blurRadius: 10,
                          offset: const Offset(0, 4),
                        ),
                      ],
                    ),
                    child: Center(
                      child: Text(
                        user.username.isNotEmpty ? user.username[0].toUpperCase() : 'U',
                        style: const TextStyle(
                          color: Colors.white,
                          fontSize: 18,
                          fontWeight: FontWeight.w700,
                        ),
                      ),
                    ),
                  ),
                ),
              ],
            ),
          ),
        ),

        // Apple Wallet style Digital ID Card
        SliverToBoxAdapter(
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 10),
            child: IosBounceable(
              onTap: isVerified ? onOpenDocuments : onStartKyc,
              child: Container(
                height: 210,
                decoration: BoxDecoration(
                  borderRadius: BorderRadius.circular(26),
                  gradient: LinearGradient(
                    colors: isVerified
                        ? [
                            const Color(0xFF0F2027),
                            const Color(0xFF203A43),
                            const Color(0xFF2C5364),
                          ]
                        : [
                            const Color(0xFF1E1E2E),
                            const Color(0xFF2A2B42),
                            const Color(0xFF382E54),
                          ],
                    begin: Alignment.topLeft,
                    end: Alignment.bottomRight,
                  ),
                  boxShadow: [
                    BoxShadow(
                      color: (isVerified ? const Color(0xFF2C5364) : AppColors.iosIndigo)
                          .withValues(alpha: 0.35),
                      blurRadius: 28,
                      offset: const Offset(0, 12),
                    ),
                  ],
                  border: Border.all(
                    color: Colors.white.withValues(alpha: 0.18),
                    width: 1,
                  ),
                ),
                child: Stack(
                  children: [
                    // Decorative glow circles
                    Positioned(
                      right: -30,
                      top: -30,
                      child: Container(
                        width: 160,
                        height: 160,
                        decoration: BoxDecoration(
                          shape: BoxShape.circle,
                          color: (isVerified ? AppColors.iosGreen : AppColors.iosIndigo)
                              .withValues(alpha: 0.15),
                        ),
                      ),
                    ),

                    // Card Content
                    Padding(
                      padding: const EdgeInsets.all(22.0),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        mainAxisAlignment: MainAxisAlignment.spaceBetween,
                        children: [
                          // Top card row
                          Row(
                            mainAxisAlignment: MainAxisAlignment.spaceBetween,
                            children: [
                              Row(
                                children: [
                                  Container(
                                    width: 32,
                                    height: 32,
                                    decoration: BoxDecoration(
                                      color: Colors.white.withValues(alpha: 0.15),
                                      borderRadius: BorderRadius.circular(10),
                                    ),
                                    child: const Icon(
                                      CupertinoIcons.shield_lefthalf_fill,
                                      color: Colors.white,
                                      size: 18,
                                    ),
                                  ),
                                  const SizedBox(width: 10),
                                  const Text(
                                    'SuperApp Digital ID',
                                    style: TextStyle(
                                      color: Colors.white,
                                      fontSize: 14,
                                      fontWeight: FontWeight.w600,
                                      letterSpacing: -0.2,
                                    ),
                                  ),
                                ],
                              ),
                              // Chip / Status Badge
                              Container(
                                padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                                decoration: BoxDecoration(
                                  color: (isVerified ? AppColors.iosGreen : AppColors.iosOrange)
                                      .withValues(alpha: 0.25),
                                  borderRadius: BorderRadius.circular(20),
                                  border: Border.all(
                                    color: (isVerified ? AppColors.iosGreen : AppColors.iosOrange)
                                        .withValues(alpha: 0.6),
                                    width: 0.8,
                                  ),
                                ),
                                child: Row(
                                  mainAxisSize: MainAxisSize.min,
                                  children: [
                                    Icon(
                                      isVerified
                                          ? CupertinoIcons.checkmark_seal_fill
                                          : CupertinoIcons.exclamationmark_triangle_fill,
                                      size: 13,
                                      color: isVerified ? AppColors.iosGreen : AppColors.iosOrange,
                                    ),
                                    const SizedBox(width: 4),
                                    Text(
                                      isVerified ? 'Верифицирован' : 'Требуется KYC',
                                      style: TextStyle(
                                        color: isVerified ? AppColors.iosGreen : AppColors.iosOrange,
                                        fontSize: 11.5,
                                        fontWeight: FontWeight.w600,
                                      ),
                                    ),
                                  ],
                                ),
                              ),
                            ],
                          ),

                          // Middle: Hologram chip simulation
                          Container(
                            width: 38,
                            height: 28,
                            decoration: BoxDecoration(
                              borderRadius: BorderRadius.circular(6),
                              gradient: const LinearGradient(
                                colors: [
                                  Color(0xFFFFD700),
                                  Color(0xFFFFA500),
                                  Color(0xFFFF8C00),
                                ],
                              ),
                              boxShadow: [
                                BoxShadow(
                                  color: Colors.black.withValues(alpha: 0.2),
                                  blurRadius: 4,
                                  offset: const Offset(0, 2),
                                ),
                              ],
                            ),
                            child: Center(
                              child: Container(
                                width: 26,
                                height: 18,
                                decoration: BoxDecoration(
                                  borderRadius: BorderRadius.circular(4),
                                  border: Border.all(color: Colors.black26, width: 0.8),
                                ),
                              ),
                            ),
                          ),

                          // Bottom card row
                          Row(
                            mainAxisAlignment: MainAxisAlignment.spaceBetween,
                            children: [
                              Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  const Text(
                                    'ВЛАДЕЛЕЦ КАРТЫ',
                                    style: TextStyle(
                                      color: Color(0xFF8E8E93),
                                      fontSize: 10,
                                      fontWeight: FontWeight.w600,
                                      letterSpacing: 0.4,
                                    ),
                                  ),
                                  const SizedBox(height: 3),
                                  Text(
                                    user.username.toUpperCase(),
                                    style: const TextStyle(
                                      color: Colors.white,
                                      fontSize: 15,
                                      fontWeight: FontWeight.w600,
                                      letterSpacing: 0.5,
                                    ),
                                  ),
                                ],
                              ),
                              Column(
                                crossAxisAlignment: CrossAxisAlignment.end,
                                children: [
                                  const Text(
                                    'ID КЛИЕНТА',
                                    style: TextStyle(
                                      color: Color(0xFF8E8E93),
                                      fontSize: 10,
                                      fontWeight: FontWeight.w600,
                                      letterSpacing: 0.4,
                                    ),
                                  ),
                                  const SizedBox(height: 3),
                                  Text(
                                    user.id.length >= 8
                                        ? '••• ${user.id.substring(user.id.length - 4)}'
                                        : '••• 8824',
                                    style: const TextStyle(
                                      color: Colors.white,
                                      fontSize: 14,
                                      fontFamily: 'monospace',
                                      fontWeight: FontWeight.w600,
                                    ),
                                  ),
                                ],
                              ),
                            ],
                          ),
                        ],
                      ),
                    ),
                  ],
                ),
              ),
            ),
          ),
        ),

        // Quick Stats / Security Health Pill Row
        SliverToBoxAdapter(
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 8),
            child: Row(
              children: [
                Expanded(
                  child: IosGlassCard(
                    padding: const EdgeInsets.all(14),
                    borderRadius: 20,
                    child: Row(
                      children: [
                        Container(
                          padding: const EdgeInsets.all(8),
                          decoration: BoxDecoration(
                            color: AppColors.iosGreen.withValues(alpha: 0.12),
                            shape: BoxShape.circle,
                          ),
                          child: const Icon(
                            CupertinoIcons.checkmark_shield_fill,
                            color: AppColors.iosGreen,
                            size: 18,
                          ),
                        ),
                        const SizedBox(width: 10),
                        Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: const [
                            Text(
                              '98%',
                              style: TextStyle(
                                fontSize: 16,
                                fontWeight: FontWeight.w700,
                                color: AppColors.textPrimary,
                              ),
                            ),
                            Text(
                              'Trust Score',
                              style: TextStyle(
                                fontSize: 11,
                                color: AppColors.textSecondary,
                              ),
                            ),
                          ],
                        ),
                      ],
                    ),
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: IosGlassCard(
                    padding: const EdgeInsets.all(14),
                    borderRadius: 20,
                    child: Row(
                      children: [
                        Container(
                          padding: const EdgeInsets.all(8),
                          decoration: BoxDecoration(
                            color: AppColors.iosBlue.withValues(alpha: 0.12),
                            shape: BoxShape.circle,
                          ),
                          child: const Icon(
                            CupertinoIcons.viewfinder,
                            color: AppColors.iosBlue,
                            size: 18,
                          ),
                        ),
                        const SizedBox(width: 10),
                        Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: const [
                            Text(
                              'Face ID',
                              style: TextStyle(
                                fontSize: 16,
                                fontWeight: FontWeight.w700,
                                color: AppColors.textPrimary,
                              ),
                            ),
                            Text(
                              'Биометрия',
                              style: TextStyle(
                                fontSize: 11,
                                color: AppColors.textSecondary,
                              ),
                            ),
                          ],
                        ),
                      ],
                    ),
                  ),
                ),
              ],
            ),
          ),
        ),

        // Quick Actions Section Header
        const SliverToBoxAdapter(
          child: Padding(
            padding: EdgeInsets.fromLTRB(20, 16, 20, 8),
            child: Text(
              'БЫСТРЫЕ ДЕЙСТВИЯ',
              style: TextStyle(
                fontSize: 13,
                fontWeight: FontWeight.w600,
                color: AppColors.textSecondary,
                letterSpacing: -0.2,
              ),
            ),
          ),
        ),

        // Quick Actions Grid (Apple Squircle Tiles)
        SliverToBoxAdapter(
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 20),
            child: Row(
              children: [
                _buildQuickActionTile(
                  icon: CupertinoIcons.person_crop_circle_badge_checkmark,
                  title: 'Пройти\nKYC',
                  color: AppColors.iosBlue,
                  onTap: onStartKyc,
                ),
                const SizedBox(width: 12),
                _buildQuickActionTile(
                  icon: CupertinoIcons.doc_text_fill,
                  title: 'Мои\nДокументы',
                  color: AppColors.iosIndigo,
                  onTap: onOpenDocuments,
                ),
                const SizedBox(width: 12),
                _buildQuickActionTile(
                  icon: CupertinoIcons.lock_shield_fill,
                  title: 'Центр\nЗащиты',
                  color: AppColors.iosGreen,
                  onTap: onOpenProfile,
                ),
              ],
            ),
          ),
        ),

        // Inset Grouped Section for Activity Feed
        SliverToBoxAdapter(
          child: Padding(
            padding: const EdgeInsets.only(top: 14),
            child: IosGroupedSection(
              header: 'История безопасности',
              items: [
                IosGroupedItem(
                  leading: Container(
                    width: 32,
                    height: 32,
                    decoration: BoxDecoration(
                      color: AppColors.iosGreen.withValues(alpha: 0.15),
                      borderRadius: BorderRadius.circular(8),
                    ),
                    child: const Icon(
                      CupertinoIcons.device_phone_portrait,
                      size: 18,
                      color: AppColors.iosGreen,
                    ),
                  ),
                  title: 'Вход с нового устройства',
                  subtitle: 'Android 17 • Текущий сеанс',
                  trailing: const Text(
                    'Сейчас',
                    style: TextStyle(fontSize: 13, color: AppColors.textSecondary),
                  ),
                  showChevron: false,
                ),
                IosGroupedItem(
                  leading: Container(
                    width: 32,
                    height: 32,
                    decoration: BoxDecoration(
                      color: AppColors.iosBlue.withValues(alpha: 0.15),
                      borderRadius: BorderRadius.circular(8),
                    ),
                    child: const Icon(
                      CupertinoIcons.shield_fill,
                      size: 18,
                      color: AppColors.iosBlue,
                    ),
                  ),
                  title: 'Служба верификации активна',
                  subtitle: 'FastAPI AI Engine & DeepFace 1:1',
                  trailing: const Text(
                    'Онлайн',
                    style: TextStyle(
                      fontSize: 13,
                      fontWeight: FontWeight.w600,
                      color: AppColors.iosGreen,
                    ),
                  ),
                  showChevron: false,
                ),
              ],
            ),
          ),
        ),

        const SliverToBoxAdapter(
          child: SizedBox(height: 100), // Spacing for floating bottom bar
        ),
      ],
    );
  }

  Widget _buildQuickActionTile({
    required IconData icon,
    required String title,
    required Color color,
    required VoidCallback onTap,
  }) {
    return Expanded(
      child: IosBounceable(
        onTap: onTap,
        child: Container(
          height: 110,
          padding: const EdgeInsets.all(14),
          decoration: BoxDecoration(
            color: AppColors.bgSurface,
            borderRadius: BorderRadius.circular(22),
            boxShadow: [
              BoxShadow(
                color: Colors.black.withValues(alpha: 0.03),
                blurRadius: 16,
                offset: const Offset(0, 4),
              ),
            ],
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Container(
                width: 38,
                height: 38,
                decoration: BoxDecoration(
                  color: color.withValues(alpha: 0.12),
                  borderRadius: BorderRadius.circular(12),
                ),
                child: Icon(icon, color: color, size: 20),
              ),
              Text(
                title,
                style: const TextStyle(
                  fontSize: 13,
                  fontWeight: FontWeight.w600,
                  color: AppColors.textPrimary,
                  height: 1.2,
                  letterSpacing: -0.2,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
