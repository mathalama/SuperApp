import 'package:camera/camera.dart';
import 'package:flutter/material.dart';
import '../../core/constants/app_colors.dart';
import '../../core/security/security_service.dart';
import '../../domain/entities/document_type.dart';
import '../common/app_button.dart';

class DocumentWizardScreen extends StatefulWidget {
  final Function(DocumentType type, String frontPath, String? backPath) onComplete;

  const DocumentWizardScreen({
    super.key,
    required this.onComplete,
  });

  @override
  State<DocumentWizardScreen> createState() => _DocumentWizardScreenState();
}

class _DocumentWizardScreenState extends State<DocumentWizardScreen>
    with SingleTickerProviderStateMixin {
  CameraController? _cameraController;
  List<CameraDescription> _cameras = [];
  bool _isCameraInitialized = false;
  bool _isCapturing = false;

  DocumentType _selectedType = DocumentType.idCard;
  int _sideIndex = 0; // 0: Front, 1: Back
  String? _frontCapturedPath;
  String? _backCapturedPath;

  late AnimationController _pulseController;
  late Animation<double> _pulseAnimation;

  @override
  void initState() {
    super.initState();
    SecurityService.enableSecureMode();
    _pulseController = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 2000),
    )..repeat(reverse: true);
    _pulseAnimation = Tween<double>(begin: 0.4, end: 0.8).animate(
      CurvedAnimation(parent: _pulseController, curve: Curves.easeInOut),
    );
    _initCamera();
  }

  Future<void> _initCamera() async {
    try {
      _cameras = await availableCameras();
      if (_cameras.isNotEmpty) {
        final backCamera = _cameras.firstWhere(
          (c) => c.lensDirection == CameraLensDirection.back,
          orElse: () => _cameras.first,
        );

        _cameraController = CameraController(
          backCamera,
          ResolutionPreset.high,
          enableAudio: false,
        );

        await _cameraController!.initialize();
        if (mounted) {
          setState(() => _isCameraInitialized = true);
        }
      }
    } catch (e) {
      debugPrint('[DocumentWizard] Camera init error: $e');
    }
  }

  @override
  void dispose() {
    _pulseController.dispose();
    _cameraController?.dispose();
    super.dispose();
  }

  Future<void> _capturePhoto() async {
    if (_cameraController == null ||
        !_cameraController!.value.isInitialized ||
        _isCapturing) {
      return;
    }

    setState(() => _isCapturing = true);

    try {
      final xFile = await _cameraController!.takePicture();

      if (_sideIndex == 0) {
        _frontCapturedPath = xFile.path;
        if (_selectedType == DocumentType.passport) {
          widget.onComplete(_selectedType, _frontCapturedPath!, null);
        } else {
          setState(() {
            _sideIndex = 1;
            _isCapturing = false;
          });
        }
      } else {
        _backCapturedPath = xFile.path;
        widget.onComplete(
            _selectedType, _frontCapturedPath!, _backCapturedPath);
      }
    } catch (e) {
      debugPrint('[DocumentWizard] Capture error: $e');
      setState(() => _isCapturing = false);
    }
  }

  double get _viewfinderAspectRatio {
    return _selectedType == DocumentType.passport ? 1.42 : 1.586;
  }

  @override
  Widget build(BuildContext context) {
    final isBackRequired = _selectedType != DocumentType.passport;
    final currentSideLabel = _sideIndex == 0 ? 'Front Side' : 'Back Side';
    final progress = _sideIndex == 0 ? 0.25 : 0.5;

    return SingleChildScrollView(
      padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 12),
      child: Center(
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 480),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              // ── Progress Bar ──
              _buildProgressBar(progress),
              const SizedBox(height: 16),

              // ── Header Row ──
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  const Text(
                    'Scan Government ID',
                    style: TextStyle(
                      fontSize: 20,
                      fontWeight: FontWeight.w800,
                      color: AppColors.textPrimary,
                      letterSpacing: -0.5,
                    ),
                  ),
                  Container(
                    padding:
                        const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                    decoration: BoxDecoration(
                      color: AppColors.primaryLight,
                      borderRadius: BorderRadius.circular(4),
                      border: Border.all(color: AppColors.primaryBorder),
                    ),
                    child: Text(
                      'STEP 1/2',
                      style: TextStyle(
                        fontSize: 10,
                        fontWeight: FontWeight.w700,
                        color: AppColors.primary,
                        fontFamily: 'monospace',
                        letterSpacing: 0.4,
                      ),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 4),
              Text(
                'Position your ${_selectedType.label} ($currentSideLabel) inside the frame',
                style: const TextStyle(
                    fontSize: 13, color: AppColors.textSecondary, height: 1.45),
              ),
              const SizedBox(height: 16),

              // ── Document Type Selector ──
              _buildDocTypeSelector(),
              const SizedBox(height: 16),

              // ── Camera Viewfinder ──
              _buildViewfinder(currentSideLabel),
              const SizedBox(height: 12),

              // ── Guidance Badges ──
              _buildGuidanceBadges(),
              const SizedBox(height: 16),

              // ── Capture Button ──
              AppButton(
                text: isBackRequired && _sideIndex == 0
                    ? 'Capture Front & Continue'
                    : 'Capture & Proceed to Liveness',
                icon: Icons.camera_alt_rounded,
                isLoading: _isCapturing,
                onPressed: _isCameraInitialized ? _capturePhoto : null,
              ),
            ],
          ),
        ),
      ),
    );
  }

  // ── Progress Bar ──
  Widget _buildProgressBar(double progress) {
    return Container(
      height: 4,
      width: double.infinity,
      decoration: BoxDecoration(
        color: const Color(0xFFF1F5F9),
        borderRadius: BorderRadius.circular(100),
      ),
      child: FractionallySizedBox(
        alignment: Alignment.centerLeft,
        widthFactor: progress,
        child: Container(
          decoration: BoxDecoration(
            color: AppColors.primary,
            borderRadius: BorderRadius.circular(100),
          ),
        ),
      ),
    );
  }

  // ── Document Type Cards ──
  Widget _buildDocTypeSelector() {
    return Column(
      children: DocumentType.values.map((type) {
        final isSelected = _selectedType == type;
        final icon = switch (type) {
          DocumentType.idCard => Icons.badge_outlined,
          DocumentType.passport => Icons.menu_book_outlined,
          DocumentType.drivingLicense => Icons.directions_car_outlined,
        };
        final desc = switch (type) {
          DocumentType.idCard => 'Front & back required',
          DocumentType.passport => 'Main page only',
          DocumentType.drivingLicense => 'Front & back required',
        };

        return Padding(
          padding: const EdgeInsets.only(bottom: 8),
          child: Material(
            color: isSelected ? Colors.white : const Color(0xFFF8FAFC),
            borderRadius: BorderRadius.circular(10),
            child: InkWell(
              borderRadius: BorderRadius.circular(10),
              onTap: _sideIndex == 0 ? () => setState(() => _selectedType = type) : null,
              child: AnimatedContainer(
                duration: const Duration(milliseconds: 200),
                padding:
                    const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
                decoration: BoxDecoration(
                  borderRadius: BorderRadius.circular(10),
                  border: Border.all(
                    color: isSelected ? AppColors.primary : AppColors.border,
                    width: isSelected ? 1.5 : 1,
                  ),
                  boxShadow: isSelected
                      ? [
                          BoxShadow(
                            color: AppColors.primary.withValues(alpha: 0.08),
                            blurRadius: 8,
                            offset: const Offset(0, 2),
                          )
                        ]
                      : null,
                ),
                child: Row(
                  children: [
                    // Icon Box
                    Container(
                      width: 40,
                      height: 40,
                      decoration: BoxDecoration(
                        color: AppColors.primaryLight,
                        borderRadius: BorderRadius.circular(10),
                        border: Border.all(color: AppColors.primaryBorder),
                      ),
                      child: Icon(icon, size: 20, color: AppColors.primary),
                    ),
                    const SizedBox(width: 12),
                    // Text
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            type.label,
                            style: TextStyle(
                              fontSize: 14,
                              fontWeight: FontWeight.w700,
                              color: AppColors.textPrimary,
                              letterSpacing: -0.01,
                            ),
                          ),
                          const SizedBox(height: 1),
                          Text(
                            desc,
                            style: const TextStyle(
                              fontSize: 12,
                              color: AppColors.textSecondary,
                            ),
                          ),
                        ],
                      ),
                    ),
                    // Arrow / Check
                    Icon(
                      isSelected ? Icons.check_circle : Icons.chevron_right,
                      size: 20,
                      color: isSelected
                          ? AppColors.primary
                          : AppColors.textMuted,
                    ),
                  ],
                ),
              ),
            ),
          ),
        );
      }).toList(),
    );
  }

  // ── Camera Viewfinder with Corner Brackets ──
  Widget _buildViewfinder(String sideLabel) {
    return AspectRatio(
      aspectRatio: _viewfinderAspectRatio,
      child: Container(
        width: double.infinity,
        decoration: BoxDecoration(
          color: const Color(0xFF0F172A),
          borderRadius: BorderRadius.circular(12),
          border: Border.all(color: AppColors.border),
        ),
        clipBehavior: Clip.antiAlias,
        child: Stack(
          fit: StackFit.expand,
          children: [
            // Camera Feed
            if (_isCameraInitialized && _cameraController != null)
              FittedBox(
                fit: BoxFit.cover,
                child: SizedBox(
                  width: _cameraController!.value.previewSize?.height ?? 1,
                  height: _cameraController!.value.previewSize?.width ?? 1,
                  child: CameraPreview(_cameraController!),
                ),
              )
            else
              const Center(
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    SizedBox(
                      width: 28,
                      height: 28,
                      child: CircularProgressIndicator(
                        color: Colors.white,
                        strokeWidth: 2.5,
                      ),
                    ),
                    SizedBox(height: 12),
                    Text(
                      'Initializing Camera…',
                      style: TextStyle(
                          color: Colors.white70,
                          fontSize: 12,
                          fontWeight: FontWeight.w500),
                    ),
                  ],
                ),
              ),

            // Dashed Border Frame + Corner Brackets
            Positioned.fill(
              child: Padding(
                padding: const EdgeInsets.all(14),
                child: AnimatedBuilder(
                  animation: _pulseAnimation,
                  builder: (context, child) {
                    return CustomPaint(
                      painter: _ViewfinderFramePainter(
                        cornerOpacity: _pulseAnimation.value,
                      ),
                    );
                  },
                ),
              ),
            ),

            // Top center label
            Positioned(
              top: 24,
              left: 0,
              right: 0,
              child: Center(
                child: Container(
                  padding:
                      const EdgeInsets.symmetric(horizontal: 12, vertical: 5),
                  decoration: BoxDecoration(
                    color: Colors.black.withValues(alpha: 0.55),
                    borderRadius: BorderRadius.circular(6),
                  ),
                  child: Text(
                    '$sideLabel — Keep Flat & Steady',
                    style: const TextStyle(
                      color: Colors.white,
                      fontSize: 11,
                      fontWeight: FontWeight.w600,
                      letterSpacing: 0.2,
                    ),
                  ),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  // ── Guidance Badges Row ──
  Widget _buildGuidanceBadges() {
    return Wrap(
      alignment: WrapAlignment.center,
      spacing: 8,
      runSpacing: 6,
      children: [
        _guideBadge(Icons.wb_sunny_outlined, 'Good Lighting', true),
        _guideBadge(Icons.crop_free, 'Full Document', false),
        _guideBadge(Icons.blur_off, 'No Glare', false),
      ],
    );
  }

  Widget _guideBadge(IconData icon, String label, bool isOk) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
      decoration: BoxDecoration(
        color: isOk ? AppColors.emeraldLight : const Color(0xFFF8FAFC),
        borderRadius: BorderRadius.circular(100),
        border: Border.all(
          color: isOk ? AppColors.emeraldBorder : AppColors.border,
        ),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(
            icon,
            size: 13,
            color: isOk ? AppColors.emerald : AppColors.textSecondary,
          ),
          const SizedBox(width: 4),
          Text(
            label,
            style: TextStyle(
              fontSize: 11,
              fontWeight: FontWeight.w600,
              color: isOk ? AppColors.emerald : AppColors.textSecondary,
            ),
          ),
        ],
      ),
    );
  }
}

// ── Custom Painter for viewfinder frame with corner brackets ──
class _ViewfinderFramePainter extends CustomPainter {
  final double cornerOpacity;

  _ViewfinderFramePainter({required this.cornerOpacity});

  @override
  void paint(Canvas canvas, Size size) {
    final rect = Offset.zero & size;

    // Dashed border
    final dashPaint = Paint()
      ..color = Colors.white.withValues(alpha: 0.35)
      ..strokeWidth = 1.5
      ..style = PaintingStyle.stroke;

    final rrect = RRect.fromRectAndRadius(rect, const Radius.circular(8));
    final path = Path()..addRRect(rrect);

    // Draw dashed path
    final metrics = path.computeMetrics();
    for (final metric in metrics) {
      double distance = 0;
      bool draw = true;
      while (distance < metric.length) {
        final next = distance + (draw ? 8 : 5);
        if (draw) {
          canvas.drawPath(
            metric.extractPath(distance, next.clamp(0, metric.length)),
            dashPaint,
          );
        }
        distance = next;
        draw = !draw;
      }
    }

    // Corner brackets
    const cornerLen = 20.0;
    const cornerWidth = 3.0;
    const cornerRadius = 6.0;

    final cornerPaint = Paint()
      ..color = Colors.white.withValues(alpha: cornerOpacity)
      ..strokeWidth = cornerWidth
      ..style = PaintingStyle.stroke
      ..strokeCap = StrokeCap.round;

    // Top-left
    canvas.drawPath(
      Path()
        ..moveTo(0, cornerLen)
        ..lineTo(0, cornerRadius)
        ..quadraticBezierTo(0, 0, cornerRadius, 0)
        ..lineTo(cornerLen, 0),
      cornerPaint,
    );

    // Top-right
    canvas.drawPath(
      Path()
        ..moveTo(size.width - cornerLen, 0)
        ..lineTo(size.width - cornerRadius, 0)
        ..quadraticBezierTo(size.width, 0, size.width, cornerRadius)
        ..lineTo(size.width, cornerLen),
      cornerPaint,
    );

    // Bottom-left
    canvas.drawPath(
      Path()
        ..moveTo(0, size.height - cornerLen)
        ..lineTo(0, size.height - cornerRadius)
        ..quadraticBezierTo(0, size.height, cornerRadius, size.height)
        ..lineTo(cornerLen, size.height),
      cornerPaint,
    );

    // Bottom-right
    canvas.drawPath(
      Path()
        ..moveTo(size.width - cornerLen, size.height)
        ..lineTo(size.width - cornerRadius, size.height)
        ..quadraticBezierTo(
            size.width, size.height, size.width, size.height - cornerRadius)
        ..lineTo(size.width, size.height - cornerLen),
      cornerPaint,
    );
  }

  @override
  bool shouldRepaint(covariant _ViewfinderFramePainter oldDelegate) {
    return oldDelegate.cornerOpacity != cornerOpacity;
  }
}
