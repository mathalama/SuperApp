import 'dart:io';
import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

class SecurityService {
  static const MethodChannel _channel = MethodChannel('dev.mathalama.app/security');

  /// Enables FLAG_SECURE on Android to block screenshots and screen recording
  static Future<void> enableSecureMode() async {
    if (kIsWeb) return;
    try {
      if (Platform.isAndroid) {
        await _channel.invokeMethod('enableSecure');
      }
    } catch (e) {
      debugPrint('[SecurityService] enableSecureMode error: $e');
    }
  }

  /// Disables FLAG_SECURE on Android
  static Future<void> disableSecureMode() async {
    if (kIsWeb) return;
    try {
      if (Platform.isAndroid) {
        await _channel.invokeMethod('disableSecure');
      }
    } catch (e) {
      debugPrint('[SecurityService] disableSecureMode error: $e');
    }
  }

  /// Safely sanitizes temporary photo files from app cache after upload
  static Future<void> sanitizeTempFiles(List<String?> paths) async {
    for (final path in paths) {
      if (path == null || path.isEmpty) continue;
      try {
        final file = File(path);
        if (await file.exists()) {
          await file.delete();
          debugPrint('[SecurityService] Sanitized temp file: $path');
        }
      } catch (e) {
        debugPrint('[SecurityService] Failed to delete temp file ($path): $e');
      }
    }
  }
}
