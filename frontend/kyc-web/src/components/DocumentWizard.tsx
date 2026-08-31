import React, { useState, useRef, useEffect, useCallback } from 'react';
import {
  CreditCard,
  BookOpen,
  Car,
  ChevronRight,
  ChevronLeft,
  Camera,
  Upload,
  SwitchCamera,
  RotateCcw,
  Check,
  ArrowRight,
  ShieldCheck,
  Sun,
  Maximize2,
  Sparkles,
  AlertCircle,
  LucideIcon
} from 'lucide-react';
import { DocumentType } from '../types';
import './DocumentWizard.css';

export interface DocumentWizardProps {
  onComplete: (frontBlob: Blob, docType: DocumentType, backBlob?: Blob) => void;
  onCancel?: () => void;
}

type WizardSubStep = 'SELECT_TYPE' | 'CAPTURE_FRONT' | 'CAPTURE_BACK' | 'REVIEW';
type SlideDirection = 'forward' | 'backward';

interface DocTypeOption {
  type: DocumentType;
  title: string;
  subtitle: string;
  icon: LucideIcon;
  isDualSided: boolean;
  aspectClass: string;
}

const DOC_OPTIONS: DocTypeOption[] = [
  {
    type: 'ID_CARD',
    title: 'ID Card / National ID',
    subtitle: 'Plastic card (Front photo + Back side with MRZ)',
    icon: CreditCard,
    isDualSided: true,
    aspectClass: 'aspect-id-card',
  },
  {
    type: 'PASSPORT',
    title: 'International Passport',
    subtitle: 'Main biographical page with photo and 2 MRZ lines',
    icon: BookOpen,
    isDualSided: false,
    aspectClass: 'aspect-passport',
  },
  {
    type: 'DRIVING_LICENSE',
    title: "Driver's License",
    subtitle: 'Official plastic driving permit (Dual-sided)',
    icon: Car,
    isDualSided: true,
    aspectClass: 'aspect-id-card',
  },
];

export const DocumentWizard: React.FC<DocumentWizardProps> = ({ onComplete, onCancel }) => {
  const [subStep, setSubStep] = useState<WizardSubStep>('SELECT_TYPE');
  const [direction, setDirection] = useState<SlideDirection>('forward');
  const [docType, setDocType] = useState<DocumentType>('ID_CARD');

  // Captured Blobs & Previews
  const [frontBlob, setFrontBlob] = useState<Blob | null>(null);
  const [frontPreview, setFrontPreview] = useState<string | null>(null);
  const [backBlob, setBackBlob] = useState<Blob | null>(null);
  const [backPreview, setBackPreview] = useState<string | null>(null);

  // Camera & Upload states
  const [inputMode, setInputMode] = useState<'camera' | 'upload'>('camera');
  const [facingMode, setFacingMode] = useState<'environment' | 'user'>('environment');
  const [cameraError, setCameraError] = useState<string | null>(null);

  const videoRef = useRef<HTMLVideoElement>(null);
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const streamRef = useRef<MediaStream | null>(null);

  const selectedOption = DOC_OPTIONS.find((o) => o.type === docType) || DOC_OPTIONS[0];

  // Clean up media stream
  const stopCamera = useCallback(() => {
    if (streamRef.current) {
      streamRef.current.getTracks().forEach((track) => track.stop());
      streamRef.current = null;
    }
    if (videoRef.current) {
      videoRef.current.srcObject = null;
    }
  }, []);

  // Initialize camera
  const startCamera = useCallback(async (facing: 'environment' | 'user' = facingMode) => {
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
      console.warn('Camera initiation failed:', err);
      setCameraError('Camera access unavailable. Switch to file upload mode.');
      setInputMode('upload');
    }
  }, [facingMode, stopCamera]);

  // Manage camera on step transitions
  useEffect(() => {
    if (inputMode === 'camera' && (subStep === 'CAPTURE_FRONT' || subStep === 'CAPTURE_BACK')) {
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
  }, [subStep, inputMode, facingMode, startCamera, stopCamera]);

  // Clean up URLs
  useEffect(() => {
    return () => {
      stopCamera();
      if (frontPreview) URL.revokeObjectURL(frontPreview);
      if (backPreview) URL.revokeObjectURL(backPreview);
    };
  }, [stopCamera, frontPreview, backPreview]);

  const toggleCameraFacing = () => {
    const nextFacing = facingMode === 'environment' ? 'user' : 'environment';
    setFacingMode(nextFacing);
    startCamera(nextFacing);
  };

  // Capture current camera snapshot
  const handleSnapPhoto = () => {
    if (!videoRef.current || !canvasRef.current) return;
    const video = videoRef.current;
    const canvas = canvasRef.current;

    canvas.width = video.videoWidth || 1280;
    canvas.height = video.videoHeight || 720;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    ctx.drawImage(video, 0, 0, canvas.width, canvas.height);

    canvas.toBlob(
      (blob) => {
        if (!blob) return;
        const previewUrl = URL.createObjectURL(blob);

        if (subStep === 'CAPTURE_FRONT') {
          setFrontBlob(blob);
          setFrontPreview(previewUrl);
          if (selectedOption.isDualSided) {
            setDirection('forward');
            setSubStep('CAPTURE_BACK');
          } else {
            setDirection('forward');
            setSubStep('REVIEW');
          }
        } else if (subStep === 'CAPTURE_BACK') {
          setBackBlob(blob);
          setBackPreview(previewUrl);
          setDirection('forward');
          setSubStep('REVIEW');
        }
      },
      'image/jpeg',
      0.95
    );
  };

  // Handle file uploads
  const handleFileUpload = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;

    const previewUrl = URL.createObjectURL(file);

    if (subStep === 'CAPTURE_FRONT') {
      setFrontBlob(file);
      setFrontPreview(previewUrl);
      if (selectedOption.isDualSided) {
        setDirection('forward');
        setSubStep('CAPTURE_BACK');
      } else {
        setDirection('forward');
        setSubStep('REVIEW');
      }
    } else if (subStep === 'CAPTURE_BACK') {
      setBackBlob(file);
      setBackPreview(previewUrl);
      setDirection('forward');
      setSubStep('REVIEW');
    }
  };

  // Retake side trigger
  const handleRetake = (side: 'front' | 'back') => {
    setDirection('backward');
    if (side === 'front') {
      setFrontBlob(null);
      if (frontPreview) URL.revokeObjectURL(frontPreview);
      setFrontPreview(null);
      setSubStep('CAPTURE_FRONT');
    } else {
      setBackBlob(null);
      if (backPreview) URL.revokeObjectURL(backPreview);
      setBackPreview(null);
      setSubStep('CAPTURE_BACK');
    }
  };

  // Navigation helpers
  const handleSelectDocType = (type: DocumentType) => {
    setDocType(type);
    setFrontBlob(null);
    setBackBlob(null);
    setFrontPreview(null);
    setBackPreview(null);
    setDirection('forward');
    setSubStep('CAPTURE_FRONT');
  };

  const handleGoBack = () => {
    setDirection('backward');
    if (subStep === 'CAPTURE_FRONT') {
      setSubStep('SELECT_TYPE');
    } else if (subStep === 'CAPTURE_BACK') {
      setSubStep('CAPTURE_FRONT');
    } else if (subStep === 'REVIEW') {
      if (selectedOption.isDualSided) {
        setSubStep('CAPTURE_BACK');
      } else {
        setSubStep('CAPTURE_FRONT');
      }
    }
  };

  const handleFinalSubmit = () => {
    if (!frontBlob) return;
    onComplete(frontBlob, docType, backBlob || undefined);
  };

  // Calculate micro-progress percentage
  const getProgressPercent = () => {
    switch (subStep) {
      case 'SELECT_TYPE': return 10;
      case 'CAPTURE_FRONT': return selectedOption.isDualSided ? 35 : 50;
      case 'CAPTURE_BACK': return 70;
      case 'REVIEW': return 100;
      default: return 0;
    }
  };

  return (
    <div className="doc-wizard-root fade-in">
      <div className="doc-wizard-card">
        {/* Progress Bar */}
        <div className="doc-wizard-progress-track">
          <div className="doc-wizard-progress-bar" style={{ width: `${getProgressPercent()}%` }} />
        </div>

        {/* Top Header */}
        <div className="doc-wizard-header">
          {subStep !== 'SELECT_TYPE' ? (
            <button type="button" onClick={handleGoBack} className="doc-wizard-back-btn">
              <ChevronLeft size={16} />
              <span>Back</span>
            </button>
          ) : (
            <div style={{ display: 'flex', alignItems: 'center', gap: '6px', color: 'var(--primary)', fontSize: '13px', fontWeight: 700 }}>
              <ShieldCheck size={16} />
              <span>Identity Verification</span>
            </div>
          )}

          <span className="doc-wizard-step-badge">
            {subStep === 'SELECT_TYPE' && 'STEP 1 / 3'}
            {subStep === 'CAPTURE_FRONT' && (selectedOption.isDualSided ? 'FRONT SIDE' : 'MAIN PAGE')}
            {subStep === 'CAPTURE_BACK' && 'BACK SIDE'}
            {subStep === 'REVIEW' && 'VERIFY & SUBMIT'}
          </span>
        </div>

        {/* Main Stage with direction-aware animation */}
        <div className={`doc-wizard-stage ${direction === 'forward' ? 'doc-wizard-slide-forward' : 'doc-wizard-slide-backward'}`} key={subStep}>
          
          {/* SUBSTEP 1: SELECT DOCUMENT TYPE */}
          {subStep === 'SELECT_TYPE' && (
            <div>
              <h2 className="doc-wizard-title">Select Document Type</h2>
              <p className="doc-wizard-subtitle">
                Choose the official government-issued identity document you wish to verify.
              </p>

              <div className="doc-type-grid">
                {DOC_OPTIONS.map((option) => {
                  const Icon = option.icon;
                  return (
                    <button
                      key={option.type}
                      type="button"
                      className="doc-type-card"
                      onClick={() => handleSelectDocType(option.type)}
                    >
                      <div className="doc-type-card-left">
                        <div className="doc-type-icon-box">
                          <Icon size={24} />
                        </div>
                        <div>
                          <div className="doc-type-name">{option.title}</div>
                          <div className="doc-type-desc">{option.subtitle}</div>
                        </div>
                      </div>
                      <ChevronRight size={20} className="doc-type-arrow" />
                    </button>
                  );
                })}
              </div>

              {onCancel && (
                <div style={{ textAlign: 'center', marginTop: '16px' }}>
                  <button type="button" onClick={onCancel} className="btn-ghost" style={{ fontSize: '13px' }}>
                    Cancel & Return
                  </button>
                </div>
              )}
            </div>
          )}

          {/* SUBSTEP 2 & 3: CAPTURE FRONT / BACK */}
          {(subStep === 'CAPTURE_FRONT' || subStep === 'CAPTURE_BACK') && (
            <div>
              <h2 className="doc-wizard-title">
                {subStep === 'CAPTURE_FRONT'
                  ? selectedOption.isDualSided
                    ? 'Capture Front Side'
                    : 'Capture Main Passport Spread'
                  : 'Capture Back Side'}
              </h2>
              <p className="doc-wizard-subtitle">
                {subStep === 'CAPTURE_FRONT'
                  ? 'Align the front side of your document within the frame. Ensure text and photo are clear.'
                  : 'Now flip the document and align the back side with MRZ code lines within the frame.'}
              </p>

              {/* Mode Toggle (Camera vs Upload) */}
              <div style={{
                display: 'flex',
                background: '#f1f5f9',
                borderRadius: 'var(--radius-sm)',
                padding: '3px',
                marginBottom: '16px',
                border: '1px solid var(--border)'
              }}>
                <button
                  type="button"
                  onClick={() => setInputMode('camera')}
                  style={{
                    flex: 1,
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    gap: '6px',
                    padding: '8px',
                    border: 'none',
                    borderRadius: 'var(--radius-xs)',
                    background: inputMode === 'camera' ? '#ffffff' : 'transparent',
                    color: inputMode === 'camera' ? 'var(--text-primary)' : 'var(--text-secondary)',
                    fontSize: '12px',
                    fontWeight: 700,
                    cursor: 'pointer',
                    transition: 'all 0.2s ease',
                    boxShadow: inputMode === 'camera' ? '0 1px 3px rgba(0,0,0,0.08)' : 'none'
                  }}
                >
                  <Camera size={14} /> Live Viewfinder
                </button>
                <button
                  type="button"
                  onClick={() => setInputMode('upload')}
                  style={{
                    flex: 1,
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    gap: '6px',
                    padding: '8px',
                    border: 'none',
                    borderRadius: 'var(--radius-xs)',
                    background: inputMode === 'upload' ? '#ffffff' : 'transparent',
                    color: inputMode === 'upload' ? 'var(--text-primary)' : 'var(--text-secondary)',
                    fontSize: '12px',
                    fontWeight: 700,
                    cursor: 'pointer',
                    transition: 'all 0.2s ease',
                    boxShadow: inputMode === 'upload' ? '0 1px 3px rgba(0,0,0,0.08)' : 'none'
                  }}
                >
                  <Upload size={14} /> Upload File
                </button>
              </div>

              {/* Camera Error Message */}
              {cameraError && inputMode === 'camera' && (
                <div className="alert alert-warning" style={{ marginBottom: '16px' }}>
                  <AlertCircle size={16} />
                  <span>{cameraError}</span>
                </div>
              )}

              {/* CAMERA VIEWFINDER */}
              {inputMode === 'camera' ? (
                <div>
                  <div className={`viewfinder-container ${selectedOption.aspectClass}`}>
                    <video ref={videoRef} playsInline muted autoPlay className="viewfinder-video" />

                    {/* Frame */}
                    <div className="laser-frame">
                      <div className="laser-corner laser-top-left" />
                      <div className="laser-corner laser-top-right" />
                      <div className="laser-corner laser-bottom-left" />
                      <div className="laser-corner laser-bottom-right" />
                    </div>
                  </div>

                  {/* Guidance Strip */}
                  <div className="guideline-strip">
                    <span className="guide-badge ok">
                      <Sun size={12} /> Good Lighting
                    </span>
                    <span className="guide-badge ok">
                      <Maximize2 size={12} /> Fill Frame
                    </span>
                    <span className="guide-badge">
                      <Sparkles size={12} /> Avoid Glare
                    </span>
                  </div>

                  {/* Camera Controls */}
                  <div className="wizard-actions-row">
                    <button type="button" onClick={handleSnapPhoto} className="btn-wizard-primary">
                      <Camera size={18} /> Take Snapshot
                    </button>
                    <button type="button" onClick={toggleCameraFacing} className="btn-wizard-secondary">
                      <SwitchCamera size={18} /> Flip
                    </button>
                  </div>
                </div>
              ) : (
                /* FILE UPLOAD DROPZONE */
                <div>
                  <div
                    className="dropzone-box"
                    onClick={() => fileInputRef.current?.click()}
                  >
                    <div style={{
                      width: '48px',
                      height: '48px',
                      borderRadius: '50%',
                      background: 'var(--primary-light)',
                      color: 'var(--primary)',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      margin: '0 auto 12px'
                    }}>
                      <Upload size={22} />
                    </div>
                    <div style={{ fontSize: '15px', fontWeight: 700, color: 'var(--text-primary)', marginBottom: '4px' }}>
                      Click to choose document image
                    </div>
                    <div style={{ fontSize: '12px', color: 'var(--text-muted)' }}>
                      Supports high-res JPG, PNG (up to 10MB)
                    </div>
                    <input
                      ref={fileInputRef}
                      type="file"
                      accept="image/jpeg,image/png,image/webp"
                      onChange={handleFileUpload}
                      style={{ display: 'none' }}
                    />
                  </div>
                </div>
              )}
            </div>
          )}

          {/* SUBSTEP 4: INSPECTION REVIEW BEFORE SUBMIT */}
          {subStep === 'REVIEW' && (
            <div>
              <h2 className="doc-wizard-title">Review Document Photos</h2>
              <p className="doc-wizard-subtitle">
                Ensure all text, MRZ lines, and photos are crisp and clearly legible without glare.
              </p>

              <div className="review-grid">
                {/* Front Side Thumbnail */}
                <div className="review-card">
                  <div className="review-card-header">
                    <span>{selectedOption.isDualSided ? 'FRONT SIDE' : 'MAIN SPREAD'}</span>
                    <span style={{ color: 'var(--emerald)', display: 'flex', alignItems: 'center', gap: '3px' }}>
                      <Check size={13} /> CAPTURED
                    </span>
                  </div>
                  <div className="review-card-img-wrapper">
                    {frontPreview && <img src={frontPreview} alt="Document Front" className="review-card-img" />}
                  </div>
                  <button type="button" onClick={() => handleRetake('front')} className="btn-retake-micro">
                    <RotateCcw size={13} /> Retake
                  </button>
                </div>

                {/* Back Side Thumbnail (if dual-sided) */}
                {selectedOption.isDualSided && (
                  <div className="review-card">
                    <div className="review-card-header">
                      <span>BACK SIDE (MRZ)</span>
                      <span style={{ color: 'var(--emerald)', display: 'flex', alignItems: 'center', gap: '3px' }}>
                        <Check size={13} /> CAPTURED
                      </span>
                    </div>
                    <div className="review-card-img-wrapper">
                      {backPreview && <img src={backPreview} alt="Document Back" className="review-card-img" />}
                    </div>
                    <button type="button" onClick={() => handleRetake('back')} className="btn-retake-micro">
                      <RotateCcw size={13} /> Retake
                    </button>
                  </div>
                )}
              </div>

              <div className="wizard-actions-row">
                <button type="button" onClick={handleFinalSubmit} className="btn-wizard-primary" style={{ flex: 1 }}>
                  <span>Proceed to Selfie Verification</span>
                  <ArrowRight size={18} />
                </button>
              </div>
            </div>
          )}

        </div>

        {/* Hidden processing canvas for high-res captures */}
        <canvas ref={canvasRef} style={{ display: 'none' }} />
      </div>
    </div>
  );
};
