import 'package:camera/camera.dart';
import 'package:flutter/material.dart';
import '../../core/constants/app_colors.dart';
import '../../core/security/security_service.dart';
import '../common/app_button.dart';

class LivenessScannerScreen extends StatefulWidget {
  final Function(String selfiePath) onCapture;
  final VoidCallback onBack;

  const LivenessScannerScreen({
    super.key,
    required this.onCapture,
    required this.onBack,
  });

  @override
  State<LivenessScannerScreen> createState() => _LivenessScannerScreenState();
}

class _LivenessScannerScreenState extends State<LivenessScannerScreen> {
  CameraController? _cameraController;
  List<CameraDescription> _cameras = [];
  bool _isCameraInitialized = false;
  bool _isCapturing = false;

  @override
  void initState() {
    super.initState();
    SecurityService.enableSecureMode();
    _initFrontCamera();
  }

  Future<void> _initFrontCamera() async {
    try {
      _cameras = await availableCameras();
      if (_cameras.isNotEmpty) {
        // Front camera for facial liveness
        final frontCamera = _cameras.firstWhere(
          (c) => c.lensDirection == CameraLensDirection.front,
          orElse: () => _cameras.first,
        );

        _cameraController = CameraController(
          frontCamera,
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
      debugPrint('[LivenessScanner] Camera init error: $e');
    }
  }

  @override
  void dispose() {
    _cameraController?.dispose();
    super.dispose();
  }

  Future<void> _takeSelfie() async {
    if (_cameraController == null || !_cameraController!.value.isInitialized || _isCapturing) {
      return;
    }

    setState(() => _isCapturing = true);

    try {
      final xFile = await _cameraController!.takePicture();
      widget.onCapture(xFile.path);
    } catch (e) {
      debugPrint('[LivenessScanner] Capture error: $e');
      setState(() => _isCapturing = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return SingleChildScrollView(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
      child: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: 480),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // Back Button & Title
            Row(
              children: [
                IconButton(
                  icon: const Icon(Icons.arrow_back, size: 20),
                  padding: EdgeInsets.zero,
                  constraints: const BoxConstraints(),
                  onPressed: widget.onBack,
                ),
                const SizedBox(width: 8),
                const Text(
                  '3D Facial Liveness',
                  style: TextStyle(
                    fontSize: 20,
                    fontWeight: FontWeight.w800,
                    color: AppColors.textPrimary,
                    letterSpacing: -0.5,
                  ),
                ),
              ],
            ),
            const SizedBox(height: 4),
            const Text(
              'Step 2 of 2 • Align your face inside the oval frame',
              style: TextStyle(fontSize: 13, color: AppColors.textSecondary),
            ),
            const SizedBox(height: 16),

            // Camera Viewfinder Box with Oval Face Mask
            Container(
              height: 320,
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
                      child: CircularProgressIndicator(color: Colors.white),
                    ),

                  // Oval Mask Overlay
                  Center(
                    child: Container(
                      width: 190,
                      height: 250,
                      decoration: BoxDecoration(
                        shape: BoxShape.rectangle,
                        borderRadius: const BorderRadius.all(Radius.elliptical(190, 250)),
                        border: Border.all(color: AppColors.emerald, width: 2.5),
                        color: Colors.transparent,
                      ),
                    ),
                  ),

                  // Bottom Guidance Badge
                  Positioned(
                    bottom: 12,
                    left: 0,
                    right: 0,
                    child: Center(
                      child: Container(
                        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 6),
                        decoration: BoxDecoration(
                          color: Colors.black.withValues(alpha: 0.65),
                          borderRadius: BorderRadius.circular(20),
                        ),
                        child: const Row(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            Icon(Icons.remove_red_eye_outlined, size: 14, color: Colors.white),
                            SizedBox(width: 6),
                            Text(
                              'Look directly into the camera',
                              style: TextStyle(color: Colors.white, fontSize: 12, fontWeight: FontWeight.w600),
                            ),
                          ],
                        ),
                      ),
                    ),
                  ),
                ],
              ),
            ),
            const SizedBox(height: 20),

            // Capture Action
            AppButton(
              text: 'Verify Identity & Submit',
              icon: Icons.fingerprint,
              isLoading: _isCapturing,
              onPressed: _isCameraInitialized ? _takeSelfie : null,
            ),
          ],
        ),
      ),
    );
  }
}
