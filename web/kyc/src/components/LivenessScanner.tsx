import React, { useEffect, useRef, useState, useCallback } from 'react';
import { Check, AlertCircle, RotateCcw, SwitchCamera, Sparkles, UserCheck, ArrowLeft, ArrowRight, Smile, ShieldCheck, Camera } from 'lucide-react';
import './LivenessScanner.css';

type CaptureState = 'challenge' | 'preview';

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

export const LivenessScanner: React.FC<LivenessScannerProps> = ({ onCapture, onBack }) => {
  const [captureState, setCaptureState] = useState<CaptureState>('challenge');
  const [previewUrl, setPreviewUrl] = useState<string | null>(null);
  const [pose, setPose] = useState<HeadPose | null>(null);
  const [facingMode, setFacingMode] = useState<'user' | 'environment'>('user');
  const [cameraError, setCameraError] = useState<string | null>(null);

  // Multi-step real-time challenge state
  const [challengeSteps, setChallengeSteps] = useState<string[]>(['CENTER', 'TURN_LEFT', 'SMILE']);
  const [currentStepIdx, setCurrentStepIdx] = useState<number>(0);
  const [isCapturingKeyframe, setIsCapturingKeyframe] = useState<boolean>(false);

  const videoRef = useRef<HTMLVideoElement>(null);
  const streamRef = useRef<MediaStream | null>(null);
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const blobRef = useRef<Blob | null>(null);
  const stableCounterRef = useRef(0);
  const pollTimerRef = useRef<number | null>(null);

  // Fetch dynamic challenge from ML service on mount
  useEffect(() => {
    async function loadChallenge() {
      try {
        const res = await fetch('/api/v1/liveness/challenge');
        if (res.ok) {
          const data = await res.json();
          if (data.steps && data.steps.length > 0) {
            setChallengeSteps(data.steps);
          }
        }
      } catch {
        // Fallback to standard 3-stage challenge sequence
        setChallengeSteps(['CENTER', 'TURN_LEFT', 'SMILE']);
      }
    }
    loadChallenge();
  }, []);

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
          video: { facingMode: { ideal: facing }, width: { ideal: 1280 }, height: { ideal: 960 } },
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
      setCameraError('Real-time camera access is required for biometric liveness verification. Please grant camera permission.');
    }
  }, [facingMode, stopCamera]);

  useEffect(() => {
    if (captureState === 'challenge') {
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
  }, [captureState, facingMode, startCamera, stopCamera]);

  const grabCurrentFrame = (): Blob | null => {
    const video = videoRef.current;
    const canvas = canvasRef.current;
    if (!video || !canvas || video.readyState < 2) return null;
    canvas.width = video.videoWidth || 1280;
    canvas.height = video.videoHeight || 960;
    const ctx = canvas.getContext('2d');
    if (!ctx) return null;
    if (facingMode === 'user') {
      ctx.translate(canvas.width, 0);
      ctx.scale(-1, 1);
    }
    ctx.drawImage(video, 0, 0, canvas.width, canvas.height);
    const dataUrl = canvas.toDataURL('image/jpeg', 0.95);
    const byteString = atob(dataUrl.split(',')[1]);
    const ab = new ArrayBuffer(byteString.length);
    const ia = new Uint8Array(ab);
    for (let i = 0; i < byteString.length; i++) {
      ia[i] = byteString.charCodeAt(i);
    }
    return new Blob([ab], { type: 'image/jpeg' });
  };

  // Real-time evaluation loop
  useEffect(() => {
    if (captureState !== 'challenge' || isCapturingKeyframe) return;

    pollTimerRef.current = window.setInterval(async () => {
      const frameBlob = grabCurrentFrame();
      if (!frameBlob) return;

      const activeChallenge = challengeSteps[currentStepIdx] || 'CENTER';

      try {
        const form = new FormData();
        form.append('frame', frameBlob, 'live_frame.jpg');
        const res = await fetch(`/api/v1/liveness/evaluate-frame?challenge=${activeChallenge}`, {
          method: 'POST',
          body: form
        });

        if (res.ok) {
          const data = await res.json();
          if (data.angles) {
            setPose(data.angles);
          }

          if (data.passed) {
            stableCounterRef.current += 1;
            if (stableCounterRef.current >= STABLE_FRAMES_NEEDED) {
              stableCounterRef.current = 0;
              // Advance to next challenge or finish
              if (currentStepIdx + 1 < challengeSteps.length) {
                setCurrentStepIdx((prev) => prev + 1);
              } else {
                // All challenges successfully passed in real-time!
                setIsCapturingKeyframe(true);
                blobRef.current = frameBlob;
                const url = URL.createObjectURL(frameBlob);
                setPreviewUrl(url);
                setTimeout(() => {
                  setCaptureState('preview');
                  setIsCapturingKeyframe(false);
                  stopCamera();
                }, 400);
              }
            }
          } else {
            stableCounterRef.current = 0;
          }
        }
      } catch (err) {
        console.debug('Evaluation error:', err);
      }
    }, 450);

    return () => {
      if (pollTimerRef.current) {
        window.clearInterval(pollTimerRef.current);
        pollTimerRef.current = null;
      }
    };
  }, [captureState, currentStepIdx, challengeSteps, isCapturingKeyframe, stopCamera]);

  const handleRetake = () => {
    if (previewUrl) URL.revokeObjectURL(previewUrl);
    setPreviewUrl(null);
    blobRef.current = null;
    setPose(null);
    setCurrentStepIdx(0);
    setIsCapturingKeyframe(false);
    stableCounterRef.current = 0;
    setCaptureState('challenge');
  };

  const handleConfirm = () => {
    if (blobRef.current) {
      onCapture(blobRef.current);
    }
  };

  const currentChallenge = challengeSteps[currentStepIdx] || 'CENTER';

  const getChallengeInstructions = (type: string) => {
    switch (type) {
      case 'CENTER':
      case 'ALIGN':
        return {
          title: 'Position your face in the oval',
          subtitle: 'Hold your head straight and level',
          icon: <UserCheck size={20} color="#10b981" />
        };
      case 'TURN_LEFT':
        return {
          title: 'Turn your head slightly to your LEFT',
          subtitle: 'Keep your face visible in the camera frame',
          icon: <ArrowLeft size={20} color="#6366f1" />
        };
      case 'TURN_RIGHT':
        return {
          title: 'Turn your head slightly to your RIGHT',
          subtitle: 'Keep your face visible in the camera frame',
          icon: <ArrowRight size={20} color="#6366f1" />
        };
      case 'SMILE':
        return {
          title: 'Smile naturally at the camera',
          subtitle: 'Biometric expression validation active',
          icon: <Smile size={20} color="#f59e0b" />
        };
      default:
        return {
          title: 'Look directly into the camera',
          subtitle: 'Follow the on-screen indicators',
          icon: <Sparkles size={20} color="#10b981" />
        };
    }
  };

  const instruction = getChallengeInstructions(currentChallenge);
  const progressPercent = Math.round(((currentStepIdx + (isCapturingKeyframe ? 1 : 0)) / challengeSteps.length) * 100);

  return (
    <div className="scanner fade-in">
      <div className="scanner-badge-row">
        <span className="live-pill">
          <span className="pulse-dot" /> LIVE STREAM ONLY
        </span>
        <span className="security-badge">
          <ShieldCheck size={13} /> ISO/IEC 30107-3 PAD
        </span>
      </div>

      <h2 className="scanner-title">Real-Time Biometric Liveness</h2>
      <p className="scanner-subtitle">
        Static photos are disabled. Verification requires real-time interaction through your live camera.
      </p>

      {/* Progress Track */}
      <div className="challenge-progress-bar">
        <div className="progress-fill" style={{ width: `${progressPercent}%` }} />
      </div>
      <div className="step-tracker-label">
        Step {Math.min(currentStepIdx + 1, challengeSteps.length)} of {challengeSteps.length}: {currentChallenge}
      </div>

      {cameraError && (
        <div className="alert alert-error" style={{ marginBottom: '16px' }}>
          <AlertCircle size={18} />
          <div>
            <div style={{ fontWeight: 700 }}>Camera Permission Required</div>
            <div style={{ fontSize: '12px' }}>{cameraError}</div>
            <button
              type="button"
              onClick={() => startCamera(facingMode)}
              className="btn btn-secondary"
              style={{ marginTop: '10px', padding: '6px 14px', fontSize: '12px' }}
            >
              <Camera size={14} /> Retry Camera
            </button>
          </div>
        </div>
      )}

      {/* Viewport */}
      <div className="liveness-viewport">
        {captureState === 'challenge' && (
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
                className={`oval-ring ${isCapturingKeyframe ? 'verified' : ''}`}
              />
            </svg>

            {/* Dynamic Telemetry Chips */}
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
                  <Sparkles size={11} /> Tracking Landmarks...
                </span>
              )}
            </div>
          </>
        )}

        {captureState === 'preview' && previewUrl && (
          <div style={{ position: 'relative', width: '100%', height: '100%' }}>
            <img src={previewUrl} alt="Verified live capture" className="scanner-preview-img" />
            <div className="verified-overlay-badge">
              <Check size={18} /> Live Biometrics Verified
            </div>
          </div>
        )}
      </div>

      {/* Real-time Guidance Message */}
      {captureState === 'challenge' && (
        <div className="guidance-box">
          <div className="guidance-icon">{instruction.icon}</div>
          <div>
            <div className="guidance-title">{instruction.title}</div>
            <div className="guidance-subtitle">{instruction.subtitle}</div>
          </div>
        </div>
      )}

      {/* Actions */}
      {captureState === 'challenge' ? (
        <div className="scanner-actions">
          <button
            type="button"
            onClick={() => {
              const next = facingMode === 'user' ? 'environment' : 'user';
              setFacingMode(next);
              startCamera(next);
            }}
            className="btn btn-secondary"
          >
            <SwitchCamera size={16} /> Switch Camera
          </button>
          {onBack && (
            <button type="button" onClick={onBack} className="btn btn-secondary">
              Back to Document
            </button>
          )}
        </div>
      ) : (
        <div className="scanner-actions">
          <button type="button" onClick={handleRetake} className="btn btn-secondary">
            <RotateCcw size={16} /> Retake Live Scan
          </button>
          <button type="button" onClick={handleConfirm} className="btn btn-primary">
            <Check size={16} /> Submit Verified Biometrics
          </button>
        </div>
      )}

      <canvas ref={canvasRef} style={{ display: 'none' }} />
    </div>
  );
};
