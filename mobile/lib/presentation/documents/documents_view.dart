import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart';
import '../../core/constants/app_colors.dart';
import '../../domain/entities/kyc_status.dart';
import '../../domain/entities/user_entity.dart';
import '../common/ios_bounceable.dart';
import '../common/ios_glass_card.dart';

class DocumentsView extends StatelessWidget {
  final UserEntity user;
  final VoidCallback onUploadNewDocument;

  const DocumentsView({
    super.key,
    required this.user,
    required this.onUploadNewDocument,
  });

  void _showDocumentDetails(BuildContext context, String docTitle, String docNumber, String expDate) {
    showCupertinoModalPopup(
      context: context,
      builder: (ctx) => Container(
        padding: const EdgeInsets.all(24),
        decoration: const BoxDecoration(
          color: AppColors.bgSurface,
          borderRadius: BorderRadius.vertical(top: Radius.circular(32)),
        ),
        child: SafeArea(
          top: false,
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Center(
                child: Container(
                  width: 36,
                  height: 4,
                  decoration: BoxDecoration(
                    color: AppColors.border,
                    borderRadius: BorderRadius.circular(2),
                  ),
                ),
              ),
              const SizedBox(height: 18),
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Text(
                    docTitle,
                    style: const TextStyle(
                      fontSize: 20,
                      fontWeight: FontWeight.w700,
                      color: AppColors.textPrimary,
                    ),
                  ),
                  const Icon(CupertinoIcons.checkmark_seal_fill, color: AppColors.iosGreen, size: 22),
                ],
              ),
              const SizedBox(height: 16),
              _buildDetailRow('Номер документа', docNumber),
              _buildDetailRow('Срок действия', expDate),
              _buildDetailRow('Орган выдачи', 'МВД / Департамент безопасности'),
              _buildDetailRow('Статус подлинности', 'Защищено электронной подписью'),
              const SizedBox(height: 24),
              SizedBox(
                width: double.infinity,
                height: 50,
                child: ElevatedButton(
                  onPressed: () => Navigator.pop(ctx),
                  child: const Text('Закрыть'),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  static Widget _buildDetailRow(String label, String value) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 6),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Text(label, style: const TextStyle(fontSize: 14, color: AppColors.textSecondary)),
          Text(value, style: const TextStyle(fontSize: 14, fontWeight: FontWeight.w600, color: AppColors.textPrimary)),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final isVerified = user.kycStatus == KycStatus.verified;

    return CustomScrollView(
      physics: const BouncingScrollPhysics(),
      slivers: [
        // Navigation Title
        SliverToBoxAdapter(
          child: Padding(
            padding: const EdgeInsets.fromLTRB(20, 16, 20, 12),
            child: Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                const Text(
                  'Документы',
                  style: TextStyle(
                    fontSize: 32,
                    fontWeight: FontWeight.w700,
                    color: AppColors.textPrimary,
                    letterSpacing: -0.8,
                  ),
                ),
                IosBounceable(
                  onTap: onUploadNewDocument,
                  child: Container(
                    padding: const EdgeInsets.all(8),
                    decoration: BoxDecoration(
                      color: AppColors.iosBlue.withValues(alpha: 0.12),
                      shape: BoxShape.circle,
                    ),
                    child: const Icon(CupertinoIcons.add, color: AppColors.iosBlue, size: 22),
                  ),
                ),
              ],
            ),
          ),
        ),

        // Apple Wallet Stacked Documents
        SliverToBoxAdapter(
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 8),
            child: Column(
              children: [
                _buildDocumentCard(
                  context,
                  title: 'Заграничный паспорт',
                  subtitle: 'Международный стандарт ICAO 9303',
                  docNumber: '75 •••• 9812',
                  expDate: '12 / 2031',
                  gradientColors: const [Color(0xFF1A2A6C), Color(0xFF27408B)],
                  isVerified: isVerified,
                  icon: CupertinoIcons.globe,
                ),
                const SizedBox(height: 14),
                _buildDocumentCard(
                  context,
                  title: 'ID-Карта / Паспорт',
                  subtitle: 'Биометрическое удостоверение',
                  docNumber: '40 •••• 3410',
                  expDate: '08 / 2029',
                  gradientColors: const [Color(0xFF2E3192), Color(0xFF1BFFFF)],
                  isVerified: isVerified,
                  icon: CupertinoIcons.person_crop_square,
                ),
                const SizedBox(height: 14),
                _buildDocumentCard(
                  context,
                  title: 'Водительское удостоверение',
                  subtitle: 'Категории B, B1, M',
                  docNumber: '99 •••• 5511',
                  expDate: '04 / 2033',
                  gradientColors: const [Color(0xFF11998E), Color(0xFF38EF7D)],
                  isVerified: isVerified,
                  icon: CupertinoIcons.car_detailed,
                ),
              ],
            ),
          ),
        ),

        // Document Security Notice (Apple style)
        SliverToBoxAdapter(
          child: Padding(
            padding: const EdgeInsets.fromLTRB(20, 16, 20, 100),
            child: IosGlassCard(
              borderRadius: 20,
              padding: const EdgeInsets.all(16),
              child: Row(
                children: [
                  Container(
                    padding: const EdgeInsets.all(10),
                    decoration: BoxDecoration(
                      color: AppColors.iosBlue.withValues(alpha: 0.12),
                      shape: BoxShape.circle,
                    ),
                    child: const Icon(CupertinoIcons.lock_shield_fill, color: AppColors.iosBlue, size: 22),
                  ),
                  const SizedBox(width: 14),
                  const Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          'Безопасное хранилище Secure Enclave',
                          style: TextStyle(
                            fontSize: 14,
                            fontWeight: FontWeight.w600,
                            color: AppColors.textPrimary,
                          ),
                        ),
                        SizedBox(height: 2),
                        Text(
                          'Все персональные данные и сканы шифруются по стандарту AES-256.',
                          style: TextStyle(
                            fontSize: 12,
                            color: AppColors.textSecondary,
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
      ],
    );
  }

  Widget _buildDocumentCard(
    BuildContext context, {
    required String title,
    required String subtitle,
    required String docNumber,
    required String expDate,
    required List<Color> gradientColors,
    required bool isVerified,
    required IconData icon,
  }) {
    return IosBounceable(
      onTap: () => _showDocumentDetails(context, title, docNumber, expDate),
      child: Container(
        height: 160,
        padding: const EdgeInsets.all(20),
        decoration: BoxDecoration(
          borderRadius: BorderRadius.circular(24),
          gradient: LinearGradient(
            colors: gradientColors,
            begin: Alignment.topLeft,
            end: Alignment.bottomRight,
          ),
          boxShadow: [
            BoxShadow(
              color: gradientColors.first.withValues(alpha: 0.28),
              blurRadius: 20,
              offset: const Offset(0, 8),
            ),
          ],
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Row(
                  children: [
                    Icon(icon, color: Colors.white, size: 22),
                    const SizedBox(width: 8),
                    Text(
                      title,
                      style: const TextStyle(
                        fontSize: 17,
                        fontWeight: FontWeight.w700,
                        color: Colors.white,
                        letterSpacing: -0.3,
                      ),
                    ),
                  ],
                ),
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                  decoration: BoxDecoration(
                    color: Colors.white.withValues(alpha: 0.2),
                    borderRadius: BorderRadius.circular(12),
                  ),
                  child: Text(
                    isVerified ? 'АКТИВЕН' : 'ОЖИДАЕТ',
                    style: const TextStyle(
                      fontSize: 11,
                      fontWeight: FontWeight.w700,
                      color: Colors.white,
                    ),
                  ),
                ),
              ],
            ),
            Text(
              subtitle,
              style: TextStyle(
                fontSize: 12.5,
                color: Colors.white.withValues(alpha: 0.8),
              ),
            ),
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Text(
                  docNumber,
                  style: const TextStyle(
                    fontSize: 15,
                    fontWeight: FontWeight.w600,
                    color: Colors.white,
                    fontFamily: 'monospace',
                    letterSpacing: 1,
                  ),
                ),
                Text(
                  'ДО $expDate',
                  style: TextStyle(
                    fontSize: 12,
                    fontWeight: FontWeight.w600,
                    color: Colors.white.withValues(alpha: 0.85),
                  ),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}
