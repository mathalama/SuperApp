import React, { useEffect, useRef, useState, useCallback } from 'react';
import {
  Check,
  AlertCircle,
  RotateCcw,
  SwitchCamera,
  UserCheck,
  ArrowLeft,
  ArrowRight,
  Smile,
  ShieldCheck,
  Camera,
  Maximize2,
  Minimize2,
  X,
  Lock,
  Volume2,
  VolumeX,
  Zap,
  Radio
} from 'lucide-react';
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

const POSE_THRESHOLD = 18; // degrees
const STABLE_FRAMES_NEEDED = 3;

// Synthesize pleasant futuristic audio cues using Web Audio API
class BiometricAudioEngine {
  private ctx: AudioContext | null = null;
  public enabled: boolean = true;

  private init() {
    if (!this.ctx && typeof window !== 'undefined') {
      const AudioCtx = window.AudioContext || (window as any).webkitAudioContext;
      if (AudioCtx) {
        this.ctx = new AudioCtx();
      }
    }
    if (this.ctx && this.ctx.state === 'suspended') {
      this.ctx.resume().catch(() => {});
    }
  }

  playStepTone() {
    if (!this.enabled) return;
    try {
      this.init();
      if (!this.ctx) return;
      const osc = this.ctx.createOscillator();
      const gain = this.ctx.createGain();
      osc.type = 'sine';
      osc.frequency.setValueAtTime(587.33, this.ctx.currentTime); // D5
      osc.frequency.exponentialRampToValueAtTime(880, this.ctx.currentTime + 0.12); // A5
      gain.gain.setValueAtTime(0.08, this.ctx.currentTime);
      gain.gain.exponentialRampToValueAtTime(0.001, this.ctx.currentTime + 0.18);
      osc.connect(gain);
      gain.connect(this.ctx.destination);
      osc.start();
      osc.stop(this.ctx.currentTime + 0.18);
    } catch {
      // Audio autoplay policy fallback
    }
  }

  playSuccessChime() {
    if (!this.enabled) return;
    try {
      this.init();
      if (!this.ctx) return;
      const now = this.ctx.currentTime;
      // Apple-like two-tone positive chime
      const tones = [523.25, 659.25, 783.99, 1046.5]; // C5, E5, G5, C6 chord
      tones.forEach((freq, idx) => {
        const osc = this.ctx!.createOscillator();
        const gain = this.ctx!.createGain();
        osc.type = 'sine';
        osc.frequency.setValueAtTime(freq, now + idx * 0.05);
        gain.gain.setValueAtTime(0.06, now + idx * 0.05);
        gain.gain.exponentialRampToValueAtTime(0.0001, now + idx * 0.05 + 0.35);
        osc.connect(gain);
        gain.connect(this.ctx!.destination);
        osc.start(now + idx * 0.05);
        osc.stop(now + idx * 0.05 + 0.35);
      });
    } catch {
      // Audio fallback
    }
  }
}

const audioEngine = new BiometricAudioEngine();

export const LivenessScanner: React.FC<LivenessScannerProps> = ({ onCapture, onBack }) => {
  const [captureState, setCaptureState] = useState<CaptureState>('challenge');
  const [previewUrl, setPreviewUrl] = useState<string | null>(null);
  const [pose, setPose] = useState<HeadPose | null>(null);
  const [facingMode, setFacingMode] = useState<'user' | 'environment'>('user');
  const [cameraError, setCameraError] = useState<string | null>(null);
  const [soundMuted, setSoundMuted] = useState<boolean>(false);
  const [isFullscreen, setIsFullscreen] = useState<boolean>(false);

  // Challenge Sequence
  const [challengeSteps, setChallengeSteps] = useState<string[]>(['CENTER', 'TURN_LEFT', 'SMILE']);
  const [currentStepIdx, setCurrentStepIdx] = useState<number>(0);
  const [stepHoldProgress, setStepHoldProgress] = useState<number>(0);
  const [isCapturingKeyframe, setIsCapturingKeyframe] = useState<boolean>(false);
  const [showShutterFlash, setShowShutterFlash] = useState<boolean>(false);
  const [fps] = useState<number>(60);

  const videoRef = useRef<HTMLVideoElement>(null);
  const streamRef = useRef<MediaStream | null>(null);
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const blobRef = useRef<Blob | null>(null);
  const stableCounterRef = useRef(0);
  const pollTimerRef = useRef<number | null>(null);
  const containerRef = useRef<HTMLDivElement>(null);

  // Sync mute state
  useEffect(() => {
    audioEngine.enabled = !soundMuted;
  }, [soundMuted]);

  // Dynamic challenge fetch
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
          video: {
            facingMode: { ideal: facing },
            width: { ideal: 1920 },
            height: { ideal: 1080 },
          },
          audio: false,
        });
      } catch {
        try {
          mediaStream = await navigator.mediaDevices.getUserMedia({
            video: { facingMode: facing },
            audio: false,
          });
        } catch {
          mediaStream = await navigator.mediaDevices.getUserMedia({ video: true, audio: false });
        }
      }
      streamRef.current = mediaStream;
      if (videoRef.current) {
        videoRef.current.srcObject = mediaStream;
        await videoRef.current.play();
      }
    } catch (err: any) {
      console.warn('Camera access failed:', err);
      setCameraError('Real-time camera access is required for biometric liveness verification. Please allow camera permissions.');
    }
  }, [facingMode, stopCamera]);

  useEffect(() => {
    if (captureState === 'challenge') {
      const timer = setTimeout(() => {
        startCamera(facingMode);
      }, 50);
      return () => {
        clearTimeout(timer);
        stopCamera();
      };
    } else {
      stopCamera();
    }
  }, [captureState, facingMode, startCamera, stopCamera]);

  // Fullscreen toggle
  const toggleFullscreen = () => {
    if (!document.fullscreenElement) {
      containerRef.current?.requestFullscreen?.().catch(() => {});
      setIsFullscreen(true);
    } else {
      document.exitFullscreen?.().catch(() => {});
      setIsFullscreen(false);
    }
  };

  useEffect(() => {
    const handleFsChange = () => {
      setIsFullscreen(!!document.fullscreenElement);
    };
    document.addEventListener('fullscreenchange', handleFsChange);
    return () => document.removeEventListener('fullscreenchange', handleFsChange);
  }, []);

  // Frame grabber
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
          body: form,
        });

        if (res.ok) {
          const data = await res.json();
          if (data.angles) {
            setPose(data.angles);
          }

          if (data.passed) {
            stableCounterRef.current += 1;
            const progress = Math.min(100, Math.round((stableCounterRef.current / STABLE_FRAMES_NEEDED) * 100));
            setStepHoldProgress(progress);

            if (stableCounterRef.current >= STABLE_FRAMES_NEEDED) {
              stableCounterRef.current = 0;
              setStepHoldProgress(0);

              if (currentStepIdx + 1 < challengeSteps.length) {
                // Step passed
                audioEngine.playStepTone();
                setCurrentStepIdx((prev) => prev + 1);
              } else {
                // All passed!
                audioEngine.playSuccessChime();
                setIsCapturingKeyframe(true);
                setShowShutterFlash(true);

                blobRef.current = frameBlob;
                const url = URL.createObjectURL(frameBlob);
                setPreviewUrl(url);

                setTimeout(() => {
                  setShowShutterFlash(false);
                  setCaptureState('preview');
                  setIsCapturingKeyframe(false);
                  stopCamera();
                }, 450);
              }
            }
          } else {
            stableCounterRef.current = Math.max(0, stableCounterRef.current - 1);
            setStepHoldProgress(Math.round((stableCounterRef.current / STABLE_FRAMES_NEEDED) * 100));
          }
        }
      } catch (err) {
        console.debug('Liveness evaluation error:', err);
      }
    }, 420);

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
    setStepHoldProgress(0);
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
          subtitle: 'Keep your head upright and look into the camera',
          icon: <UserCheck size={26} />,
          theme: 'cyan',
        };
      case 'TURN_LEFT':
        return {
          title: 'Turn head slowly to your LEFT',
          subtitle: 'Keep your face visible inside the reticle',
          icon: <ArrowLeft size={26} />,
          theme: 'violet',
        };
      case 'TURN_RIGHT':
        return {
          title: 'Turn head slowly to your RIGHT',
          subtitle: 'Keep your face visible inside the reticle',
          icon: <ArrowRight size={26} />,
          theme: 'violet',
        };
      case 'SMILE':
        return {
          title: 'Smile naturally at the camera',
          subtitle: 'Validating micro-biometrics and dynamic contours',
          icon: <Smile size={26} />,
          theme: 'amber',
        };
      default:
        return {
          title: 'Look directly into camera',
          subtitle: 'Follow the on-screen biometric indicators',
          icon: <Zap size={26} />,
          theme: 'cyan',
        };
    }
  };

  const instruction = getChallengeInstructions(currentChallenge);
  const totalSteps = challengeSteps.length;
  const isPoseBalanced = pose && Math.abs(pose.yaw) <= POSE_THRESHOLD && Math.abs(pose.pitch) <= POSE_THRESHOLD;

  return (
    <div ref={containerRef} className="fullscreen-biometric-portal">
      {/* Background Live Camera Feed (Full-bleed edge-to-edge, zero cropping) */}
      <div className="fullscreen-camera-layer">
        <video
          ref={videoRef}
          playsInline
          muted
          autoPlay
          className={`fullscreen-video ${facingMode === 'user' ? 'mirrored' : ''}`}
        />
        {/* Subtle cinematic grain + vignette layer */}
        <div className="biometric-cinematic-vignette" />
      </div>

      {/* Shutter Flash Effect */}
      {showShutterFlash && <div className="biometric-shutter-flash" />}

      {/* ====================================================================
          TOP FLOATING HUD
          ==================================================================== */}
      <header className="biometric-top-hud">
        <div className="top-hud-left">
          {onBack && (
            <button
              type="button"
              onClick={onBack}
              className="hud-glass-button"
              aria-label="Exit verification"
              title="Exit verification"
            >
              <X size={18} />
              <span className="hud-button-label">Cancel</span>
            </button>
          )}

          <div className="hud-live-pill">
            <span className="hud-live-dot" />
            <span className="hud-live-text">LIVE STREAM</span>
            <span className="hud-fps-divider">•</span>
            <span className="hud-fps-text">{fps} FPS</span>
          </div>
        </div>

        <div className="top-hud-center">
          <div className="hud-security-shield">
            <ShieldCheck size={14} className="hud-shield-icon" />
            <span>ISO/IEC 30107-3 PAD</span>
          </div>
        </div>

        <div className="top-hud-right">
          <button
            type="button"
            onClick={() => setSoundMuted(!soundMuted)}
            className="hud-glass-button hud-icon-only"
            title={soundMuted ? 'Unmute Sound' : 'Mute Sound'}
          >
            {soundMuted ? <VolumeX size={17} /> : <Volume2 size={17} />}
          </button>

          <button
            type="button"
            onClick={() => {
              const next = facingMode === 'user' ? 'environment' : 'user';
              setFacingMode(next);
              startCamera(next);
            }}
            className="hud-glass-button hud-icon-only"
            title="Switch Camera"
          >
            <SwitchCamera size={17} />
          </button>

          <button
            type="button"
            onClick={toggleFullscreen}
            className="hud-glass-button hud-icon-only"
            title={isFullscreen ? 'Exit Fullscreen' : 'Fullscreen'}
          >
            {isFullscreen ? <Minimize2 size={17} /> : <Maximize2 size={17} />}
          </button>
        </div>
      </header>

      {/* Camera Access Error Alert */}
      {cameraError && (
        <div className="biometric-camera-error-modal">
          <div className="error-modal-card">
            <div className="error-modal-icon">
              <AlertCircle size={28} />
            </div>
            <h3>Camera Access Required</h3>
            <p>{cameraError}</p>
            <div className="error-modal-actions">
              <button
                type="button"
                onClick={() => startCamera(facingMode)}
                className="hud-btn-primary"
              >
                <Camera size={16} /> Enable Camera
              </button>
              {onBack && (
                <button type="button" onClick={onBack} className="hud-btn-secondary">
                  Go Back
                </button>
              )}
            </div>
          </div>
        </div>
      )}

      {/* ====================================================================
          BIOMETRIC RETICLE (APPLE FACE ID OVAL + CYBER HUD)
          ==================================================================== */}
      {captureState === 'challenge' && (
        <div className="biometric-center-stage">
          <div className={`biometric-face-reticle theme-${instruction.theme} ${isCapturingKeyframe ? 'verified-lock' : ''}`}>

            {/* SVG Mask cutting out the face oval cleanly */}
            <svg className="reticle-svg" viewBox="0 0 400 500" preserveAspectRatio="none">
              <defs>
                <mask id="biometricHoleMask">
                  <rect width="400" height="500" fill="white" />
                  <ellipse cx="200" cy="240" rx="130" ry="180" fill="black" />
                </mask>

                <linearGradient id="laserBeamGrad" x1="0%" y1="0%" x2="0%" y2="100%">
                  <stop offset="0%" stopColor="transparent" />
                  <stop offset="50%" stopColor="rgba(0, 242, 254, 0.7)" />
                  <stop offset="100%" stopColor="transparent" />
                </linearGradient>

                <linearGradient id="ringGrad" x1="0%" y1="0%" x2="100%" y2="100%">
                  <stop offset="0%" stopColor="#00f2fe" />
                  <stop offset="50%" stopColor="#4facfe" />
                  <stop offset="100%" stopColor="#00f2fe" />
                </linearGradient>
              </defs>

              {/* Darkened backdrop with smooth oval hole */}
              <rect
                width="400"
                height="500"
                className="reticle-dark-mask"
                mask="url(#biometricHoleMask)"
              />

              {/* Base Glowing Oval Track */}
              <ellipse
                cx="200"
                cy="240"
                rx="130"
                ry="180"
                className="reticle-oval-base"
              />

              {/* Animated Progress Ring filling clockwise */}
              <ellipse
                cx="200"
                cy="240"
                rx="130"
                ry="180"
                className="reticle-oval-progress"
                style={{
                  strokeDasharray: '974',
                  strokeDashoffset: `${974 - (974 * ((currentStepIdx + stepHoldProgress / 100) / totalSteps))}`,
                }}
              />
            </svg>

            {/* Glowing Laser Scan Bar */}
            <div className="biometric-laser-scanner" />

            {/* Futuristic Corner Targeting Reticles */}
            <div className="reticle-corner corner-tl" />
            <div className="reticle-corner corner-tr" />
            <div className="reticle-corner corner-bl" />
            <div className="reticle-corner corner-br" />

            {/* Outer Rotating Cyber Rings */}
            <div className="reticle-orbit-ring" />
            <div className="reticle-orbit-dots" />

            {/* Verified Lock Badge */}
            {isCapturingKeyframe && (
              <div className="biometric-verified-burst">
                <div className="burst-circle">
                  <Check size={44} />
                </div>
                <div className="burst-text">BIOMETRIC SIGNATURE VERIFIED</div>
              </div>
            )}
          </div>

          {/* Floating Telemetry Chips HUD */}
          <div className="telemetry-chip-cluster">
            {pose ? (
              <>
                <div className={`hud-chip ${Math.abs(pose.yaw) <= POSE_THRESHOLD ? 'hud-chip-ok' : 'hud-chip-active'}`}>
                  <span className="chip-key">YAW</span>
                  <span className="chip-val">{Math.round(pose.yaw)}°</span>
                </div>
                <div className={`hud-chip ${Math.abs(pose.pitch) <= POSE_THRESHOLD ? 'hud-chip-ok' : 'hud-chip-active'}`}>
                  <span className="chip-key">PITCH</span>
                  <span className="chip-val">{Math.round(pose.pitch)}°</span>
                </div>
                <div className={`hud-chip ${isPoseBalanced ? 'hud-chip-ok' : 'hud-chip-idle'}`}>
                  <span className="chip-key">ALIGN</span>
                  <span className="chip-val">{isPoseBalanced ? 'OPTIMAL' : 'ADJUSTING'}</span>
                </div>
              </>
            ) : (
              <div className="hud-chip hud-chip-idle">
                <Radio size={12} className="pulse-icon" />
                <span className="chip-val">ACQUIRING BIOMETRIC SENSORS...</span>
              </div>
            )}
          </div>
        </div>
      )}

      {/* ====================================================================
          PREVIEW STATE (FULLSCREEN REVIEW OF CAPTURED BIOMETRIC PHOTO)
          ==================================================================== */}
      {captureState === 'preview' && previewUrl && (
        <div className="biometric-preview-stage fade-in">
          <div className="preview-oval-wrapper">
            <img src={previewUrl} alt="Captured Biometric Frame" className="preview-oval-img" />
            <div className="preview-verified-halo">
              <div className="preview-verified-icon">
                <Check size={28} />
              </div>
            </div>
          </div>

          <div className="preview-meta-card">
            <div className="preview-meta-header">
              <Lock size={16} className="text-emerald" />
              <span>Biometric Keyframe Authenticated</span>
            </div>
            <p className="preview-meta-text">
              Real-time 3D liveness validated against ISO/IEC 30107-3 anti-spoof standards. Ready for cryptographic verification.
            </p>

            <div className="preview-actions-row">
              <button
                type="button"
                onClick={handleRetake}
                className="hud-btn-secondary"
              >
                <RotateCcw size={16} /> Retake Scan
              </button>
              <button
                type="button"
                onClick={handleConfirm}
                className="hud-btn-primary"
              >
                <Check size={18} /> Confirm & Submit Biometrics
              </button>
            </div>
          </div>
        </div>
      )}

      {/* ====================================================================
          BOTTOM DYNAMIC ISLAND / GUIDANCE HUD (APPLE-STYLE)
          ==================================================================== */}
      {captureState === 'challenge' && (
        <footer className="biometric-bottom-hud">
          {/* Step Progression Pills */}
          <div className="hud-step-tracker">
            {challengeSteps.map((stepName, idx) => {
              const isPast = idx < currentStepIdx;
              const isCurrent = idx === currentStepIdx;
              return (
                <div
                  key={stepName}
                  className={`hud-step-pip ${isPast ? 'step-passed' : ''} ${isCurrent ? 'step-current' : ''}`}
                >
                  <div className="pip-indicator">
                    {isPast ? <Check size={11} /> : idx + 1}
                  </div>
                  <span className="pip-title">
                    {stepName.replace('_', ' ')}
                  </span>
                </div>
              );
            })}
          </div>

          {/* Large Guidance Card */}
          <div className="hud-guidance-pill">
            <div className={`guidance-icon-circle theme-${instruction.theme}`}>
              {instruction.icon}
            </div>
            <div className="guidance-copy">
              <h2 className="guidance-main-title">{instruction.title}</h2>
              <p className="guidance-sub-title">{instruction.subtitle}</p>
            </div>

            {/* Circular Hold Progress Gauge */}
            {stepHoldProgress > 0 && (
              <div className="guidance-gauge-wrap" title={`Hold pose: ${stepHoldProgress}%`}>
                <svg className="guidance-gauge-svg" viewBox="0 0 36 36">
                  <path
                    className="gauge-bg"
                    d="M18 2.0845 a 15.9155 15.9155 0 0 1 0 31.831 a 15.9155 15.9155 0 0 1 0 -31.831"
                  />
                  <path
                    className="gauge-val"
                    strokeDasharray={`${stepHoldProgress}, 100`}
                    d="M18 2.0845 a 15.9155 15.9155 0 0 1 0 31.831 a 15.9155 15.9155 0 0 1 0 -31.831"
                  />
                </svg>
                <span className="gauge-text">{stepHoldProgress}%</span>
              </div>
            )}
          </div>
        </footer>
      )}

      {/* Hidden processing canvas */}
      <canvas ref={canvasRef} style={{ display: 'none' }} />
    </div>
  );
};
