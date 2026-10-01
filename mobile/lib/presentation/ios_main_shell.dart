import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart';
import '../../core/constants/app_colors.dart';
import '../../domain/entities/user_entity.dart';
import 'common/dynamic_island_header.dart';
import 'common/ios_bottom_bar.dart';
import 'dashboard/ios_dashboard_view.dart';
import 'documents/documents_view.dart';
import 'home_kyc_flow_screen.dart';
import 'profile/profile_screen.dart';

/// Main Shell coordinating iOS 17/18 style tabs, Dynamic Island, and Floating TabBar.
class IosMainShell extends StatefulWidget {
  final UserEntity user;

  const IosMainShell({super.key, required this.user});

  @override
  State<IosMainShell> createState() => _IosMainShellState();
}

class _IosMainShellState extends State<IosMainShell> {
  int _currentTab = 0;

  void _switchTab(int index) {
    setState(() {
      _currentTab = index;
    });
  }

  @override
  Widget build(BuildContext context) {
    final tabs = [
      IosDashboardView(
        user: widget.user,
        onStartKyc: () => _switchTab(1),
        onOpenDocuments: () => _switchTab(2),
        onOpenProfile: () => _switchTab(3),
      ),
      HomeKycFlowScreen(user: widget.user),
      DocumentsView(
        user: widget.user,
        onUploadNewDocument: () => _switchTab(1),
      ),
      ProfileScreen(user: widget.user),
    ];

    return Scaffold(
      backgroundColor: AppColors.bgBase,
      body: Stack(
        children: [
          // Screen Tab Content
          SafeArea(
            bottom: false,
            child: Column(
              children: [
                // Top Dynamic Island Pill
                DynamicIslandHeader(
                  status: widget.user.kycStatus,
                  username: widget.user.username,
                  onKycAction: () => _switchTab(1),
                ),

                // Main tab body
                Expanded(
                  child: IndexedStack(
                    index: _currentTab,
                    children: tabs,
                  ),
                ),
              ],
            ),
          ),

          // Floating Frosted Glass Bottom Navigation Bar
          Positioned(
            left: 0,
            right: 0,
            bottom: 0,
            child: IosFloatingTabBar(
              currentIndex: _currentTab,
              onTabSelected: _switchTab,
              items: const [
                IosTabItem(
                  icon: CupertinoIcons.house,
                  activeIcon: CupertinoIcons.house_fill,
                  label: 'Обзор',
                ),
                IosTabItem(
                  icon: CupertinoIcons.checkmark_shield,
                  activeIcon: CupertinoIcons.checkmark_shield_fill,
                  label: 'KYC',
                ),
                IosTabItem(
                  icon: CupertinoIcons.doc_text,
                  activeIcon: CupertinoIcons.doc_text_fill,
                  label: 'Документы',
                ),
                IosTabItem(
                  icon: CupertinoIcons.person_crop_circle,
                  activeIcon: CupertinoIcons.person_crop_circle_fill,
                  label: 'Профиль',
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
