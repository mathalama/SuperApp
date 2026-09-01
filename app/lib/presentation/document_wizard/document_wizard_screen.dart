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

class _DocumentWizardScreenState extends State<DocumentWizardScreen> {
  CameraController? _cameraController;
  List<CameraDescription> _cameras = [];
  bool _isCameraInitialized = false;
  bool _isCapturing = false;

  DocumentType _selectedType = DocumentType.idCard;
  int _sideIndex = 0; // 0: Front, 1: Back
  String? _frontCapturedPath;
  String? _backCapturedPath;

  @override
  void initState() {
    super.initState();
    SecurityService.enableSecureMode();
    _initCamera();
  }

  Future<void> _initCamera() async {
    try {
      _cameras = await availableCameras();
      if (_cameras.isNotEmpty) {
        // Back camera for document scanning
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
          setState(() {
            _isCameraInitialized = true;
          });
        }
      }
    } catch (e) {
      debugPrint('[DocumentWizard] Camera init error: $e');
    }
  }

  @override
  void dispose() {
    _cameraController?.dispose();
    super.dispose();
  }

  Future<void> _capturePhoto() async {
    if (_cameraController == null || !_cameraController!.value.isInitialized || _isCapturing) {
      return;
    }

    setState(() => _isCapturing = true);

    try {
      final xFile = await _cameraController!.takePicture();

      if (_sideIndex == 0) {
        _frontCapturedPath = xFile.path;
        if (_selectedType == DocumentType.passport) {
          // Passports only require front (main page)
          widget.onComplete(_selectedType, _frontCapturedPath!, null);
        } else {
          // ID Card & Driving License require Back Side
          setState(() {
            _sideIndex = 1;
            _isCapturing = false;
          });
        }
      } else {
        _backCapturedPath = xFile.path;
        widget.onComplete(_selectedType, _frontCapturedPath!, _backCapturedPath);
      }
    } catch (e) {
      debugPrint('[DocumentWizard] Capture error: $e');
      setState(() => _isCapturing = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final isBackRequired = _selectedType != DocumentType.passport;
    final currentSideLabel = _sideIndex == 0 ? 'Front Side' : 'Back Side';

    return SingleChildScrollView(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
      child: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: 480),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // Header Info
            const Text(
              'Scan Government ID',
              style: TextStyle(
                fontSize: 20,
                fontWeight: FontWeight.w800,
                color: AppColors.textPrimary,
                letterSpacing: -0.5,
              ),
            ),
            const SizedBox(height: 4),
            Text(
              'Step 1 of 2 • Position your ${_selectedType.label} ($currentSideLabel) in the frame',
              style: const TextStyle(fontSize: 13, color: AppColors.textSecondary),
            ),
            const SizedBox(height: 14),

            // Document Type Chips Selector
            SingleChildScrollView(
              scrollDirection: Axis.horizontal,
              child: Row(
                children: DocumentType.values.map((type) {
                  final isSelected = _selectedType == type;
                  return Padding(
                    padding: const EdgeInsets.only(right: 8),
                    child: ChoiceChip(
                      label: Text(type.label),
                      selected: isSelected,
                      selectedColor: AppColors.primaryLight,
                      backgroundColor: AppColors.bgSurface,
                      side: BorderSide(
                        color: isSelected ? AppColors.primary : AppColors.border,
                        width: isSelected ? 1.5 : 1,
                      ),
                      labelStyle: TextStyle(
                        fontSize: 12,
                        fontWeight: isSelected ? FontWeight.w700 : FontWeight.w500,
                        color: isSelected ? AppColors.primary : AppColors.textSecondary,
                      ),
                      onSelected: (selected) {
                        if (selected && _sideIndex == 0) {
                          setState(() => _selectedType = type);
                        }
                      },
                    ),
                  );
                }).toList(),
              ),
            ),
            const SizedBox(height: 16),

            // Camera Viewfinder Box with Document Overlay
            Container(
              height: 280,
              width: double.infinity,
              decoration: BoxDecoration(
                color: Colors.black,
                borderRadius: BorderRadius.circular(16),
                border: Border.all(color: AppColors.border),
              ),
              clipBehavior: Clip.antiAlias,
              child: Stack(
                fit: StackFit.expand,
                children: [
                  if (_isCameraInitialized && _cameraController != null)
                    Center(
                      child: AspectRatio(
                        aspectRatio: 1 / _cameraController!.value.aspectRatio,
                        child: CameraPreview(_cameraController!),
                      ),
                    )
                  else
                    const Center(
                      child: Column(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          CircularProgressIndicator(color: Colors.white),
                          SizedBox(height: 12),
                          Text(
                            'Initializing Secure Camera...',
                            style: TextStyle(color: Colors.white70, fontSize: 13),
                          ),
                        ],
                      ),
                    ),

                  // Document Guideline Overlay
                  Center(
                    child: Container(
                      width: 280,
                      height: 180,
                      decoration: BoxDecoration(
                        border: Border.all(color: AppColors.primaryBorder, width: 2),
                        borderRadius: BorderRadius.circular(12),
                        color: Colors.white.withValues(alpha: 0.05),
                      ),
                      child: Column(
                        mainAxisAlignment: MainAxisAlignment.spaceBetween,
                        children: [
                          Container(
                            margin: const EdgeInsets.only(top: 8),
                            padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                            decoration: BoxDecoration(
                              color: Colors.black.withValues(alpha: 0.6),
                              borderRadius: BorderRadius.circular(6),
                            ),
                            child: Text(
                              '$currentSideLabel (Keep Flat)',
                              style: const TextStyle(
                                color: Colors.white,
                                fontSize: 11,
                                fontWeight: FontWeight.w600,
                              ),
                            ),
                          ),
                          Container(
                            margin: const EdgeInsets.only(bottom: 8),
                            padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                            decoration: BoxDecoration(
                              color: Colors.black.withValues(alpha: 0.6),
                              borderRadius: BorderRadius.circular(6),
                            ),
                            child: const Text(
                              'Avoid glare & reflections',
                              style: TextStyle(color: Colors.white70, fontSize: 10),
                            ),
                          ),
                        ],
                      ),
                    ),
                  ),
                ],
              ),
            ),
            const SizedBox(height: 20),

            // Capture Action
            AppButton(
              text: isBackRequired && _sideIndex == 0 ? 'Capture Front & Continue' : 'Capture & Proceed to Liveness',
              icon: Icons.camera_alt_outlined,
              isLoading: _isCapturing,
              onPressed: _isCameraInitialized ? _capturePhoto : null,
            ),
          ],
        ),
      ),
    );
  }
}
