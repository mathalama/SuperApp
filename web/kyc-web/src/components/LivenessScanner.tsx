import React, { useEffect, useRef, useState, useCallback } from 'react';
import { Check, AlertCircle, RotateCcw, SwitchCamera, Upload, Camera, Sparkles, UserCheck } from 'lucide-react';
import './LivenessScanner.css';

type CaptureState = 'live' | 'preview';

interface HeadPose {
  yaw: number;
  pitch: number;
  roll: number;
}

interface LivenessScannerProps {
  onCapture: (blob: Blob) => void;
  onBack?: () => void;
}

const POSE_THRESHOLD = 20; // degrees
const STABLE_FRAMES_NEEDED = 3;

async function checkHeadPose(blob: Blob): Promise<HeadPose | null> {
  try {
    const form = new FormData();
    form.append('frame', blob, 'frame.jpg');
    const res = await fetch('/api/v1/head-pose-check', { method: 'POST', body: form });
    if (!res.ok) return null;
    const data = await res.json();
    return data.head_pose_angles ?? null;
  } catch {
    return null;
  }
}

function poseLabel(pose: HeadPose | null): string {
  if (!pose) return 'Align your face inside the biometric oval';
  if (Math.abs(pose.yaw) > POSE_THRESHOLD) return pose.yaw > 0 ? 'Turn your head slightly left' : 'Turn your head slightly right';
  if (Math.abs(pose.pitch) > POSE_THRESHOLD) return pose.pitch > 0 ? 'Lower your chin slightly' : 'Raise your chin slightly';
  if (Math.abs(pose.roll) > POSE_THRESHOLD) return 'Hold your device and head level';
  return 'Biometrics aligned! Hold still for capture…';
}

export const LivenessScanner: React.FC<LivenessScannerProps> = ({ onCapture, onBack }) => {
  const [captureState, setCaptureState] = useState<CaptureState>('live');
  const [previewUrl, setPreviewUrl] = useState<string | null>(null);
  const [pose, setPose] = useState<HeadPose | null>(null);
  const [deviceTilt, setDeviceTilt] = useState<number | null>(null);
  const [facingMode, setFacingMode] = useState<'user' | 'environment'>('user');
  const [mode, setMode] = useState<'camera' | 'upload'>('camera');
  const [cameraError, setCameraError] = useState<string | null>(null);

  const videoRef = useRef<HTMLVideoElement>(null);
  const streamRef = useRef<MediaStream | null>(null);
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const blobRef = useRef<Blob | null>(null);
  const stableFramesRef = useRef(0);
  const pollTimerRef = useRef<number | null>(null);

  const stopCamera = useCallback(() => {
    if (streamRef.current) {
      streamRef.current.getTracks().forEach((t) => t.stop());
      streamRef.current = null;
    }
    if (videoRef.current) {
      videoRef.current.srcObject = null;
    }
    if (pollTimerRef.current) {
      window.clearInterval(pollTimerRef.current);
      pollTimerRef.current = null;
    }
  }, []);

  const startCamera = useCallback(async (facing: 'user' | 'environment' = facingMode) => {
    stopCamera();
    setCameraError(null);
    try {
      let mediaStream: MediaStream;
      try {
        mediaStream = await navigator.mediaDevices.getUserMedia({
          video: { facingMode: { ideal: facing }, width: { ideal: 720 }, height: { ideal: 960 } },
        });
      } catch {
        try {
          mediaStream = await navigator.mediaDevices.getUserMedia({
            video: { facingMode: facing },
          });
        } catch {
          mediaStream = await navigator.mediaDevices.getUserMedia({ video: true });
        }
      }
      streamRef.current = mediaStream;
      if (videoRef.current) {
        videoRef.current.srcObject = mediaStream;
        await videoRef.current.play();
      }
    } catch (err: any) {
      console.warn('Camera access failed:', err);
      setCameraError('Camera access unavailable. Switch to upload mode.');
      setMode('upload');
    }
  }, [facingMode, stopCamera]);

  useEffect(() => {
    if (mode === 'camera' && captureState === 'live') {
      const timer = setTimeout(() => {
        startCamera(facingMode);
      }, 100);
      return () => {
        clearTimeout(timer);
        stopCamera();
      };
    } else {
      stopCamera();
    }
  }, [mode, captureState, facingMode, startCamera, stopCamera]);

  // Orientation tilt telemetry
  useEffect(() => {
    const handleOrientation = (e: DeviceOrientationEvent) => {
      if (e.beta != null) setDeviceTilt(Math.round(e.beta));
    };
    window.addEventListener('deviceorientation', handleOrientation);
    return () => window.removeEventListener('deviceorientation', handleOrientation);
  }, []);

  const grabCurrentFrame = (): Blob | null => {
    const video = videoRef.current;
    const canvas = canvasRef.current;
    if (!video || !canvas || video.readyState < 2) return null;
    canvas.width = video.videoWidth || 720;
    canvas.height = video.videoHeight || 960;
    const ctx = canvas.getContext('2d');
    if (!ctx) return null;
    if (facingMode === 'user') {
      ctx.translate(canvas.width, 0);
      ctx.scale(-1, 1);
    }
    ctx.drawImage(video, 0, 0, canvas.width, canvas.height);
    const dataUrl = canvas.toDataURL('image/jpeg', 0.92);
    const byteString = atob(dataUrl.split(',')[1]);
    const ab = new ArrayBuffer(byteString.length);
    const ia = new Uint8Array(ab);
    for (let i = 0; i < byteString.length; i++) {
      ia[i] = byteString.charCodeAt(i);
    }
    return new Blob([ab], { type: 'image/jpeg' });
  };

  // Real-time pose telemetry polling
  useEffect(() => {
    if (mode !== 'camera' || captureState !== 'live') return;

    pollTimerRef.current = window.setInterval(async () => {
      const frameBlob = grabCurrentFrame();
      if (!frameBlob) return;

      const p = await checkHeadPose(frameBlob);
      if (!p) {
        setPose(null);
        stableFramesRef.current = 0;
        return;
      }

      setPose(p);

      const ok =
        Math.abs(p.yaw) <= POSE_THRESHOLD &&
        Math.abs(p.pitch) <= POSE_THRESHOLD &&
        Math.abs(p.roll) <= POSE_THRESHOLD;

      if (ok) {
        stableFramesRef.current += 1;
        if (stableFramesRef.current >= STABLE_FRAMES_NEEDED) {
          blobRef.current = frameBlob;
          const url = URL.createObjectURL(frameBlob);
          setPreviewUrl(url);
          setCaptureState('preview');
          stopCamera();
        }
      } else {
        stableFramesRef.current = 0;
      }
    }, 600);

    return () => {
      if (pollTimerRef.current) {
        window.clearInterval(pollTimerRef.current);
        pollTimerRef.current = null;
      }
    };
  }, [mode, captureState, stopCamera]);

  const handleManualCapture = () => {
    const frame = grabCurrentFrame();
    if (!frame) return;
    blobRef.current = frame;
    const url = URL.createObjectURL(frame);
    setPreviewUrl(url);
    setCaptureState('preview');
    stopCamera();
  };

  const handleFileUpload = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    blobRef.current = file;
    const url = URL.createObjectURL(file);
    setPreviewUrl(url);
    setCaptureState('preview');
  };

  const handleRetake = () => {
    if (previewUrl) URL.revokeObjectURL(previewUrl);
    setPreviewUrl(null);
    blobRef.current = null;
    setPose(null);
    stableFramesRef.current = 0;
    setCaptureState('live');
  };

  const handleConfirm = () => {
    if (blobRef.current) {
      onCapture(blobRef.current);
    }
  };

  const isPoseOk =
    pose !== null &&
    Math.abs(pose.yaw) <= POSE_THRESHOLD &&
    Math.abs(pose.pitch) <= POSE_THRESHOLD &&
    Math.abs(pose.roll) <= POSE_THRESHOLD;

  return (
    <div className="scanner fade-in">
      <h2 className="scanner-title">Biometric Face Verification</h2>
      <p className="scanner-subtitle">
        Look directly into the camera. Our neural network will verify liveness and match with your document photo.
      </p>

      {/* Mode selection if camera failed */}
      {cameraError && mode === 'upload' && (
        <div className="alert alert-warning" style={{ marginBottom: '16px' }}>
          <AlertCircle size={16} />
          <span>{cameraError}</span>
        </div>
      )}

      {/* Viewport */}
      <div className="liveness-viewport">
        {captureState === 'live' && mode === 'camera' && (
          <>
            <video
              ref={videoRef}
              playsInline
              muted
              autoPlay
              className={`scanner-video ${facingMode === 'user' ? 'mirrored' : ''}`}
            />

            {/* Oval Biometric Overlay */}
            <svg className="oval-overlay" viewBox="0 0 300 400" preserveAspectRatio="none">
              <defs>
                <mask id="hudOvalHole">
                  <rect width="300" height="400" fill="white" />
                  <ellipse cx="150" cy="190" rx="95" ry="135" fill="black" />
                </mask>
              </defs>
              <rect width="300" height="400" className="dim" mask="url(#hudOvalHole)" />
              <ellipse
                cx="150"
                cy="190"
                rx="95"
                ry="135"
                className={`oval-ring ${isPoseOk ? 'stable' : ''}`}
              />
            </svg>

            {/* Telemetry chips */}
            <div className="pose-chip-row">
              {pose ? (
                <>
                  <span className={`chip ${Math.abs(pose.yaw) <= POSE_THRESHOLD ? 'ok' : 'warn'}`}>
                    Yaw: {Math.round(pose.yaw)}°
                  </span>
                  <span className={`chip ${Math.abs(pose.pitch) <= POSE_THRESHOLD ? 'ok' : 'warn'}`}>
                    Pitch: {Math.round(pose.pitch)}°
                  </span>
                  <span className={`chip ${Math.abs(pose.roll) <= POSE_THRESHOLD ? 'ok' : 'warn'}`}>
                    Roll: {Math.round(pose.roll)}°
                  </span>
                </>
              ) : (
                <span className="chip">
                  <Sparkles size={11} /> Biometric HUD Active
                </span>
              )}
              {deviceTilt !== null && (
                <span className="chip">Tilt: {deviceTilt}°</span>
              )}
            </div>
          </>
        )}

        {captureState === 'preview' && previewUrl && (
          <img src={previewUrl} alt="Selfie preview" className="scanner-preview-img" />
        )}
      </div>

      {/* Real-time Guidance Message */}
      {captureState === 'live' && mode === 'camera' && (
        <div style={{
          background: isPoseOk ? 'var(--emerald-light)' : 'var(--bg-surface-elevated)',
          border: `1px solid ${isPoseOk ? 'var(--emerald-border)' : 'var(--border)'}`,
          borderRadius: 'var(--radius-md)',
          padding: '12px 16px',
          marginBottom: '20px',
          color: isPoseOk ? '#34d399' : 'var(--text-secondary)',
          fontSize: '13px',
          fontWeight: 700,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          gap: '8px',
          transition: 'all 0.25s ease'
        }}>
          {isPoseOk ? <UserCheck size={16} /> : <Sparkles size={16} />}
          <span>{poseLabel(pose)}</span>
        </div>
      )}

      {/* Capture Actions */}
      {captureState === 'live' ? (
        <div className="scanner-actions">
          {mode === 'camera' ? (
            <>
              <button
                type="button"
                onClick={() => {
                  const next = facingMode === 'user' ? 'environment' : 'user';
                  setFacingMode(next);
                  startCamera(next);
                }}
                className="btn btn-secondary"
              >
                <SwitchCamera size={16} /> Flip
              </button>
              <button type="button" onClick={handleManualCapture} className="btn btn-primary">
                <Camera size={16} /> Take Photo
              </button>
            </>
          ) : (
            <button
              type="button"
              onClick={() => fileInputRef.current?.click()}
              className="btn btn-primary"
              style={{ width: '100%' }}
            >
              <Upload size={16} /> Choose Selfie Photo
            </button>
          )}
        </div>
      ) : (
        <div className="scanner-actions">
          <button type="button" onClick={handleRetake} className="btn btn-secondary">
            <RotateCcw size={16} /> Retake
          </button>
          <button type="button" onClick={handleConfirm} className="btn btn-primary">
            <Check size={16} /> Confirm & Verify
          </button>
        </div>
      )}

      {/* Fallback switch toggle */}
      {captureState === 'live' && (
        <div style={{ display: 'flex', justifyContent: 'center', gap: '16px', marginTop: '14px' }}>
          <button
            type="button"
            onClick={() => setMode(mode === 'camera' ? 'upload' : 'camera')}
            className="btn-text"
          >
            {mode === 'camera' ? 'Switch to photo upload' : 'Switch to live camera'}
          </button>

          {onBack && (
            <button type="button" onClick={onBack} className="btn-text">
              Back to document
            </button>
          )}
        </div>
      )}

      <input
        ref={fileInputRef}
        type="file"
        accept="image/jpeg,image/png"
        onChange={handleFileUpload}
        style={{ display: 'none' }}
      />
      <canvas ref={canvasRef} style={{ display: 'none' }} />
    </div>
  );
};
