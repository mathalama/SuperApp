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
  Volume2,
  VolumeX,
  Zap,
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

const POSE_THRESHOLD = 18;
const STABLE_FRAMES_NEEDED = 3;

// Web Audio synthesizer for sleek biometric haptic audio feedback
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
      osc.frequency.setValueAtTime(587.33, this.ctx.currentTime);
      osc.frequency.exponentialRampToValueAtTime(880, this.ctx.currentTime + 0.12);
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
      const tones = [523.25, 659.25, 783.99, 1046.5];
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

  // Challenge Steps
  const [challengeSteps, setChallengeSteps] = useState<string[]>(['CENTER', 'TURN_LEFT', 'SMILE']);
  const [currentStepIdx, setCurrentStepIdx] = useState<number>(0);
  const [stepHoldProgress, setStepHoldProgress] = useState<number>(0);
  const [isCapturingKeyframe, setIsCapturingKeyframe] = useState<boolean>(false);
  const [showShutterFlash, setShowShutterFlash] = useState<boolean>(false);

  const videoRef = useRef<HTMLVideoElement>(null);
  const streamRef = useRef<MediaStream | null>(null);
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const blobRef = useRef<Blob | null>(null);
  const stableCounterRef = useRef(0);
  const pollTimerRef = useRef<number | null>(null);
  const containerRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    audioEngine.enabled = !soundMuted;
  }, [soundMuted]);

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
      setCameraError('Для биометрической проверки необходим доступ к камере в реальном времени. Пожалуйста, разрешите доступ к камере.');
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
                audioEngine.playStepTone();
                setCurrentStepIdx((prev) => prev + 1);
              } else {
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
                }, 400);
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
          title: 'Поместите лицо в контур',
          subtitle: 'Держите голову прямо и смотрите в камеру',
          icon: <UserCheck size={26} />,
          theme: 'cyan',
        };
      case 'TURN_LEFT':
        return {
          title: 'Плавно поверните голову ВЛЕВО',
          subtitle: 'Удерживайте лицо в поле видимости камеры',
          icon: <ArrowLeft size={26} />,
          theme: 'violet',
        };
      case 'TURN_RIGHT':
        return {
          title: 'Плавно поверните голову ВПРАВО',
          subtitle: 'Удерживайте лицо в поле видимости камеры',
          icon: <ArrowRight size={26} />,
          theme: 'violet',
        };
      case 'SMILE':
        return {
          title: 'Естественно улыбнитесь',
          subtitle: 'Проверка мимических биометрических маркеров',
          icon: <Smile size={26} />,
          theme: 'amber',
        };
      default:
        return {
          title: 'Смотрите прямо в камеру',
          subtitle: 'Следуйте указаниям на экране',
          icon: <Zap size={26} />,
          theme: 'cyan',
        };
    }
  };

  const instruction = getChallengeInstructions(currentChallenge);
  const totalSteps = challengeSteps.length;
  const isPoseBalanced = pose && Math.abs(pose.yaw) <= POSE_THRESHOLD && Math.abs(pose.pitch) <= POSE_THRESHOLD;

  return (
    <div ref={containerRef} className="fullscreen-live-camera-root">
      {/* 100% Fullscreen Uncut Live Camera Video */}
      <video
        ref={videoRef}
        playsInline
        muted
        autoPlay
        className={`fullbleed-live-video ${facingMode === 'user' ? 'mirrored' : ''}`}
      />

      {/* Shutter Flash Animation on Keyframe Capture */}
      {showShutterFlash && <div className="fullscreen-shutter-flash" />}

      {/* TOP FLOATING MINIMAL HUD */}
      <header className="fullscreen-top-bar">
        <div className="top-bar-left">
          {onBack && (
            <button
              type="button"
              onClick={onBack}
              className="glass-pill-btn"
              title="Выйти"
            >
              <X size={18} />
              <span className="btn-text">Закрыть</span>
            </button>
          )}

          <div className="live-stream-badge">
            <span className="live-red-beacon" />
            <span className="live-label">LIVE КАМЕРА</span>
          </div>
        </div>

        <div className="top-bar-right">
          <div className="security-pill">
            <ShieldCheck size={14} className="shield-green" />
            <span>ISO 30107-3 PAD</span>
          </div>

          <button
            type="button"
            onClick={() => setSoundMuted(!soundMuted)}
            className="glass-pill-btn icon-only"
            title={soundMuted ? 'Включить звук' : 'Выключить звук'}
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
            className="glass-pill-btn icon-only"
            title="Переключить камеру"
          >
            <SwitchCamera size={17} />
          </button>

          <button
            type="button"
            onClick={toggleFullscreen}
            className="glass-pill-btn icon-only"
            title={isFullscreen ? 'Выйти из полноэкранного режима' : 'На весь экран'}
          >
            {isFullscreen ? <Minimize2 size={17} /> : <Maximize2 size={17} />}
          </button>
        </div>
      </header>

      {/* Camera Error Alert */}
      {cameraError && (
        <div className="fullscreen-camera-error-backdrop">
          <div className="error-card-glass">
            <AlertCircle size={36} color="#ef4444" />
            <h3>Доступ к камере заблокирован</h3>
            <p>{cameraError}</p>
            <div className="error-card-actions">
              <button
                type="button"
                onClick={() => startCamera(facingMode)}
                className="btn-accent-emerald"
              >
                <Camera size={16} /> Повторить попытку
              </button>
              {onBack && (
                <button type="button" onClick={onBack} className="btn-subtle-glass">
                  Назад
                </button>
              )}
            </div>
          </div>
        </div>
      )}

      {/* CENTER: PURE FACE RETICLE (NO DARK MASKS, JUST THE FACE CONTOUR) */}
      {captureState === 'challenge' && (
        <div className="fullscreen-center-reticle-area">
          <div className={`face-biometric-target theme-${instruction.theme} ${isCapturingKeyframe ? 'verified-lock' : ''}`}>

            {/* SVG Face Contour Outline (Anatomical Human Head & Biometric Target) */}
            <svg
              className="face-silhouette-svg"
              viewBox="0 0 320 420"
              preserveAspectRatio="xMidYMid meet"
            >
              {/* Outer Head Contour Guide */}
              <path
                className="head-contour-path"
                d="M 160,30
                   C 230,30 280,80 280,160
                   C 280,240 260,310 210,360
                   C 185,385 160,390 160,390
                   C 160,390 135,385 110,360
                   C 60,310 40,240 40,160
                   C 40,80 90,30 160,30 Z"
              />

              {/* Dynamic Progress Ring Over Contour */}
              <path
                className="head-progress-path"
                d="M 160,30
                   C 230,30 280,80 280,160
                   C 280,240 260,310 210,360
                   C 185,385 160,390 160,390
                   C 160,390 135,385 110,360
                   C 60,310 40,240 40,160
                   C 40,80 90,30 160,30 Z"
                style={{
                  strokeDasharray: '960',
                  strokeDashoffset: `${960 - (960 * ((currentStepIdx + stepHoldProgress / 100) / totalSteps))}`,
                }}
              />

              {/* Eye Alignment Level Tick Marks */}
              <line x1="28" y1="160" x2="62" y2="160" className="alignment-tick" />
              <line x1="258" y1="160" x2="292" y2="160" className="alignment-tick" />

              {/* Forehead & Chin Level Marks */}
              <line x1="160" y1="18" x2="160" y2="34" className="alignment-tick" />
              <line x1="160" y1="386" x2="160" y2="402" className="alignment-tick" />
            </svg>

            {/* Glowing Biometric Laser Bar */}
            <div className="reticle-laser-line" />

            {/* Corner Focus Brackets */}
            <div className="corner-bracket corner-top-left" />
            <div className="corner-bracket corner-top-right" />
            <div className="corner-bracket corner-bottom-left" />
            <div className="corner-bracket corner-bottom-right" />

            {/* Success Lock Stamp */}
            {isCapturingKeyframe && (
              <div className="biometric-locked-stamp">
                <div className="stamp-icon-circle">
                  <Check size={48} />
                </div>
                <span className="stamp-text">ЛИЦО УСПЕШНО ПОДТВЕРЖДЕНО</span>
              </div>
            )}
          </div>

          {/* Telemetry Floating Chips */}
          <div className="floating-telemetry-row">
            {pose ? (
              <>
                <span className={`telemetry-pill ${Math.abs(pose.yaw) <= POSE_THRESHOLD ? 'ok' : 'active'}`}>
                  YAW {Math.round(pose.yaw)}°
                </span>
                <span className={`telemetry-pill ${Math.abs(pose.pitch) <= POSE_THRESHOLD ? 'ok' : 'active'}`}>
                  PITCH {Math.round(pose.pitch)}°
                </span>
                <span className={`telemetry-pill ${isPoseBalanced ? 'ok' : 'idle'}`}>
                  {isPoseBalanced ? 'ПОЛОЖЕНИЕ: ИДЕАЛЬНО' : 'ВЫРАВНИВАНИЕ...'}
                </span>
              </>
            ) : (
              <span className="telemetry-pill idle">ПОИСК ЛИЦА...</span>
            )}
          </div>
        </div>
      )}

      {/* PREVIEW STATE (CAPTURED FRAME) */}
      {captureState === 'preview' && previewUrl && (
        <div className="fullscreen-preview-overlay fade-in">
          <div className="preview-photo-frame">
            <img src={previewUrl} alt="Захваченный снимок биометрии" className="preview-frame-image" />
            <div className="preview-badge-emerald">
              <Check size={24} />
            </div>
          </div>

          <div className="preview-glass-sheet">
            <h2 className="preview-sheet-title">Биометрический снимок зафиксирован</h2>
            <p className="preview-sheet-subtitle">
              Живость лица подтверждена стандартами защиты от спуфинга (ISO/IEC 30107-3).
            </p>
            <div className="preview-sheet-actions">
              <button
                type="button"
                onClick={handleRetake}
                className="btn-subtle-glass"
              >
                <RotateCcw size={16} /> Пересдать
              </button>
              <button
                type="button"
                onClick={handleConfirm}
                className="btn-accent-emerald"
              >
                <Check size={18} /> Подтвердить и отправить
              </button>
            </div>
          </div>
        </div>
      )}

      {/* BOTTOM FLOATING GUIDANCE DYNAMIC ISLAND */}
      {captureState === 'challenge' && (
        <footer className="fullscreen-bottom-dock">
          {/* Step Sequence Dots */}
          <div className="dock-steps-bar">
            {challengeSteps.map((stepName, idx) => {
              const isPast = idx < currentStepIdx;
              const isCurrent = idx === currentStepIdx;
              return (
                <div
                  key={stepName}
                  className={`dock-step-item ${isPast ? 'done' : ''} ${isCurrent ? 'active' : ''}`}
                >
                  <div className="step-circle">
                    {isPast ? <Check size={11} /> : idx + 1}
                  </div>
                  <span className="step-label">{stepName}</span>
                </div>
              );
            })}
          </div>

          {/* Large Guidance Capsule */}
          <div className="dock-instruction-capsule">
            <div className={`instruction-icon-badge theme-${instruction.theme}`}>
              {instruction.icon}
            </div>
            <div className="instruction-texts">
              <div className="instruction-heading">{instruction.title}</div>
              <div className="instruction-subheading">{instruction.subtitle}</div>
            </div>

            {/* Circular Hold Gauge */}
            {stepHoldProgress > 0 && (
              <div className="step-hold-meter">
                <svg viewBox="0 0 36 36" className="meter-svg">
                  <path
                    className="meter-track"
                    d="M18 2.0845 a 15.9155 15.9155 0 0 1 0 31.831 a 15.9155 15.9155 0 0 1 0 -31.831"
                  />
                  <path
                    className="meter-fill"
                    strokeDasharray={`${stepHoldProgress}, 100`}
                    d="M18 2.0845 a 15.9155 15.9155 0 0 1 0 31.831 a 15.9155 15.9155 0 0 1 0 -31.831"
                  />
                </svg>
                <span className="meter-digit">{stepHoldProgress}%</span>
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
