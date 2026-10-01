import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import '../../core/constants/app_colors.dart';
import '../../domain/entities/kyc_status.dart';
import '../../domain/entities/user_entity.dart';
import '../bloc/auth/auth_bloc.dart';
import '../bloc/auth/auth_event.dart';
import '../common/ios_bounceable.dart';
import '../common/ios_grouped_card.dart';

class ProfileScreen extends StatefulWidget {
  final UserEntity user;

  const ProfileScreen({super.key, required this.user});

  @override
  State<ProfileScreen> createState() => _ProfileScreenState();
}

class _ProfileScreenState extends State<ProfileScreen> {
  bool _biometricsEnabled = true;
  bool _twoFactorEnabled = true;
  bool _hapticEnabled = true;

  void _showLogoutDialog(BuildContext context) {
    showCupertinoModalPopup(
      context: context,
      builder: (ctx) => CupertinoActionSheet(
        title: const Text('Выход из аккаунта'),
        message: const Text('Вы уверены, что хотите завершить сеанс на этом устройстве?'),
        actions: [
          CupertinoActionSheetAction(
            isDestructiveAction: true,
            onPressed: () {
              Navigator.pop(ctx);
              context.read<AuthBloc>().add(LogoutRequestedEvent());
            },
            child: const Text('Выйти'),
          ),
        ],
        cancelButton: CupertinoActionSheetAction(
          onPressed: () => Navigator.pop(ctx),
          child: const Text('Отмена'),
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final isVerified = widget.user.kycStatus == KycStatus.verified;

    return CustomScrollView(
      physics: const BouncingScrollPhysics(),
      slivers: [
        // Navigation Title
        const SliverToBoxAdapter(
          child: Padding(
            padding: EdgeInsets.fromLTRB(20, 16, 20, 16),
            child: Text(
              'Профиль',
              style: TextStyle(
                fontSize: 32,
                fontWeight: FontWeight.w700,
                color: AppColors.textPrimary,
                letterSpacing: -0.8,
              ),
            ),
          ),
        ),

        // Apple ID Profile Header Card
        SliverToBoxAdapter(
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 8),
            child: Container(
              padding: const EdgeInsets.all(20),
              decoration: BoxDecoration(
                color: AppColors.bgSurface,
                borderRadius: BorderRadius.circular(24),
                boxShadow: [
                  BoxShadow(
                    color: Colors.black.withValues(alpha: 0.03),
                    blurRadius: 20,
                    offset: const Offset(0, 6),
                  ),
                ],
              ),
              child: Column(
                children: [
                  // Avatar with Verified Ring
                  Stack(
                    children: [
                      Container(
                        width: 86,
                        height: 86,
                        decoration: BoxDecoration(
                          shape: BoxShape.circle,
                          gradient: const LinearGradient(
                            colors: [AppColors.iosBlue, AppColors.iosIndigo],
                            begin: Alignment.topLeft,
                            end: Alignment.bottomRight,
                          ),
                          boxShadow: [
                            BoxShadow(
                              color: AppColors.iosBlue.withValues(alpha: 0.25),
                              blurRadius: 18,
                              offset: const Offset(0, 6),
                            ),
                          ],
                        ),
                        child: Center(
                          child: Text(
                            widget.user.username.isNotEmpty
                                ? widget.user.username[0].toUpperCase()
                                : 'U',
                            style: const TextStyle(
                              fontSize: 36,
                              fontWeight: FontWeight.w700,
                              color: Colors.white,
                            ),
                          ),
                        ),
                      ),
                      if (isVerified)
                        Positioned(
                          right: 0,
                          bottom: 0,
                          child: Container(
                            padding: const EdgeInsets.all(4),
                            decoration: const BoxDecoration(
                              color: Colors.white,
                              shape: BoxShape.circle,
                            ),
                            child: const Icon(
                              CupertinoIcons.checkmark_seal_fill,
                              size: 22,
                              color: AppColors.iosGreen,
                            ),
                          ),
                        ),
                    ],
                  ),
                  const SizedBox(height: 14),
                  Text(
                    widget.user.username,
                    style: const TextStyle(
                      fontSize: 22,
                      fontWeight: FontWeight.w700,
                      color: AppColors.textPrimary,
                      letterSpacing: -0.4,
                    ),
                  ),
                  const SizedBox(height: 4),
                  Text(
                    widget.user.email,
                    style: const TextStyle(
                      fontSize: 14,
                      color: AppColors.textSecondary,
                    ),
                  ),
                  const SizedBox(height: 14),
                  // Apple ID pill
                  Container(
                    padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 6),
                    decoration: BoxDecoration(
                      color: AppColors.iosBlue.withValues(alpha: 0.1),
                      borderRadius: BorderRadius.circular(20),
                    ),
                    child: Row(
                      mainAxisSize: MainAxisSize.min,
                      children: const [
                        Icon(CupertinoIcons.shield_lefthalf_fill,
                            size: 14, color: AppColors.iosBlue),
                        SizedBox(width: 6),
                        Text(
                          'SuperApp ID • Защищен',
                          style: TextStyle(
                            fontSize: 12.5,
                            fontWeight: FontWeight.w600,
                            color: AppColors.iosBlue,
                          ),
                        ),
                      ],
                    ),
                  ),
                ],
              ),
            ),
          ),
        ),

        // Section: БЕЗОПАСНОСТЬ & ДОСТУП
        SliverToBoxAdapter(
          child: IosGroupedSection(
            header: 'Безопасность и Доступ',
            items: [
              IosGroupedItem(
                leading: _buildIconCircle(
                  CupertinoIcons.viewfinder,
                  AppColors.iosBlue,
                ),
                title: 'Face ID / Touch ID',
                subtitle: 'Быстрый вход и подтверждение',
                trailing: CupertinoSwitch(
                  value: _biometricsEnabled,
                  activeTrackColor: AppColors.iosGreen,
                  onChanged: (val) {
                    setState(() {
                      _biometricsEnabled = val;
                    });
                  },
                ),
                showChevron: false,
              ),
              IosGroupedItem(
                leading: _buildIconCircle(
                  CupertinoIcons.lock_shield_fill,
                  AppColors.iosGreen,
                ),
                title: 'Двухфакторная защита (2FA)',
                subtitle: 'SMS / Authenticator',
                trailing: CupertinoSwitch(
                  value: _twoFactorEnabled,
                  activeTrackColor: AppColors.iosGreen,
                  onChanged: (val) {
                    setState(() {
                      _twoFactorEnabled = val;
                    });
                  },
                ),
                showChevron: false,
              ),
              IosGroupedItem(
                leading: _buildIconCircle(
                  CupertinoIcons.lock_fill,
                  AppColors.iosOrange,
                ),
                title: 'Сменить пароль',
                onTap: () {
                  _showFeaturePlaceholder(context, 'Смена пароля');
                },
              ),
              IosGroupedItem(
                leading: _buildIconCircle(
                  CupertinoIcons.device_phone_portrait,
                  AppColors.iosIndigo,
                ),
                title: 'Активные устройства',
                subtitle: '1 подключенное устройство',
                onTap: () {
                  _showFeaturePlaceholder(context, 'Список устройств');
                },
              ),
            ],
          ),
        ),

        // Section: KYC И ЛИМИТЫ
        SliverToBoxAdapter(
          child: IosGroupedSection(
            header: 'Верификация и Лимиты',
            items: [
              IosGroupedItem(
                leading: _buildIconCircle(
                  CupertinoIcons.person_badge_plus_fill,
                  isVerified ? AppColors.iosGreen : AppColors.iosOrange,
                ),
                title: 'Статус аккаунта',
                subtitle: widget.user.kycStatus.label,
                trailing: Container(
                  padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                  decoration: BoxDecoration(
                    color: (isVerified ? AppColors.iosGreen : AppColors.iosOrange)
                        .withValues(alpha: 0.15),
                    borderRadius: BorderRadius.circular(12),
                  ),
                  child: Text(
                    isVerified ? 'Подтвержден' : 'Требуется KYC',
                    style: TextStyle(
                      fontSize: 12,
                      fontWeight: FontWeight.w600,
                      color: isVerified ? AppColors.iosGreen : AppColors.iosOrange,
                    ),
                  ),
                ),
                showChevron: false,
              ),
              IosGroupedItem(
                leading: _buildIconCircle(
                  CupertinoIcons.chart_bar_alt_fill,
                  AppColors.iosTeal,
                ),
                title: 'Суточные лимиты',
                subtitle: isVerified ? 'До 5,000,000 ₽' : 'До 15,000 ₽',
                onTap: () {
                  _showFeaturePlaceholder(context, 'Управление лимитами');
                },
              ),
              IosGroupedItem(
                leading: _buildIconCircle(
                  CupertinoIcons.doc_checkmark_fill,
                  AppColors.iosPurple,
                ),
                title: 'Сертификат KYC',
                subtitle: 'Электронная подпись подтверждения',
                onTap: () {
                  _showFeaturePlaceholder(context, 'Электронный сертификат');
                },
              ),
            ],
          ),
        ),

        // Section: НАСТРОЙКИ ПРИЛОЖЕНИЯ
        SliverToBoxAdapter(
          child: IosGroupedSection(
            header: 'Система и настройки',
            items: [
              IosGroupedItem(
                leading: _buildIconCircle(
                  CupertinoIcons.waveform,
                  AppColors.iosPink,
                ),
                title: 'Тактильный отклик (Haptic)',
                trailing: CupertinoSwitch(
                  value: _hapticEnabled,
                  activeTrackColor: AppColors.iosGreen,
                  onChanged: (val) {
                    setState(() {
                      _hapticEnabled = val;
                    });
                  },
                ),
                showChevron: false,
              ),
              IosGroupedItem(
                leading: _buildIconCircle(
                  CupertinoIcons.info_circle_fill,
                  AppColors.textSecondary,
                ),
                title: 'О приложении',
                subtitle: 'SuperApp Mobile v1.0.0 (Apple HIG Edition)',
                showChevron: false,
              ),
            ],
          ),
        ),

        // Section: ВЫХОД
        SliverToBoxAdapter(
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 12),
            child: IosBounceable(
              onTap: () => _showLogoutDialog(context),
              child: Container(
                height: 52,
                decoration: BoxDecoration(
                  color: AppColors.bgSurface,
                  borderRadius: BorderRadius.circular(16),
                  boxShadow: [
                    BoxShadow(
                      color: Colors.black.withValues(alpha: 0.03),
                      blurRadius: 10,
                      offset: const Offset(0, 4),
                    ),
                  ],
                ),
                child: const Center(
                  child: Text(
                    'Выйти из аккаунта',
                    style: TextStyle(
                      fontSize: 16,
                      fontWeight: FontWeight.w600,
                      color: AppColors.iosRed,
                    ),
                  ),
                ),
              ),
            ),
          ),
        ),

        const SliverToBoxAdapter(
          child: SizedBox(height: 100),
        ),
      ],
    );
  }

  static Widget _buildIconCircle(IconData icon, Color color) {
    return Container(
      width: 32,
      height: 32,
      decoration: BoxDecoration(
        color: color,
        borderRadius: BorderRadius.circular(8),
      ),
      child: Icon(icon, color: Colors.white, size: 18),
    );
  }

  void _showFeaturePlaceholder(BuildContext context, String title) {
    showCupertinoDialog(
      context: context,
      builder: (ctx) => CupertinoAlertDialog(
        title: Text(title),
        content: const Padding(
          padding: EdgeInsets.only(top: 8.0),
          child: Text('Раздел находится в активной разработке.'),
        ),
        actions: [
          CupertinoDialogAction(
            child: const Text('Понятно'),
            onPressed: () => Navigator.pop(ctx),
          ),
        ],
      ),
    );
  }
}
