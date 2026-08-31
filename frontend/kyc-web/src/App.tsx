import React, { useState, useEffect } from 'react';
import { Navbar } from './components/Navbar';
import { AuthScreen } from './components/AuthScreen';
import { DocumentWizard } from './components/DocumentWizard';
import { LivenessScanner } from './components/LivenessScanner';
import { VerificationDashboard } from './components/VerificationDashboard';
import { User, DocumentType, KycApplicationResponse } from './types';
import { api } from './services/api';
import { CheckCircle2, AlertCircle, Cpu, Scan, ShieldCheck, Fingerprint, Loader2 } from 'lucide-react';

type Step = 'auth' | 'document' | 'liveness' | 'submitting' | 'result';

const PROCESSING_STAGES = [
  { id: 1, label: 'Document OCR & MRZ Recognition', icon: Scan },
  { id: 2, label: 'Facial Feature Vector Extraction', icon: Cpu },
  { id: 3, label: 'Anti-Spoof 3D Liveness Analysis', icon: Fingerprint },
  { id: 4, label: 'Synthesizing Verification Decision', icon: ShieldCheck },
];

export const App: React.FC = () => {
  const [token, setToken] = useState<string | null>(null);
  const [user, setUser] = useState<User | null>(null);
  const [step, setStep] = useState<Step>('auth');

  const [documentBlob, setDocumentBlob] = useState<Blob | null>(null);
  const [documentBackBlob, setDocumentBackBlob] = useState<Blob | null>(null);
  const [docType, setDocType] = useState<DocumentType>('ID_CARD');
  const [, setSelfieBlob] = useState<Blob | null>(null);

  const [activeStageIndex, setActiveStageIndex] = useState(0);

  const [verificationResult, setVerificationResult] = useState<KycApplicationResponse | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  // Staged milestone animation during ML submission
  useEffect(() => {
    let interval: number;
    if (step === 'submitting') {
      setActiveStageIndex(0);
      interval = window.setInterval(() => {
        setActiveStageIndex((prev) => (prev < PROCESSING_STAGES.length - 1 ? prev + 1 : prev));
      }, 1100);
    }
    return () => {
      if (interval) clearInterval(interval);
    };
  }, [step]);

  const handleAuthSuccess = (newToken: string, newUser: User) => {
    setToken(newToken);
    setUser(newUser);
    setStep('document');
  };

  const handleLogout = () => {
    setToken(null);
    setUser(null);
    setDocumentBlob(null);
    setDocumentBackBlob(null);
    setSelfieBlob(null);
    setVerificationResult(null);
    setStep('auth');
  };

  const handleDocumentCaptured = (front: Blob, type: DocumentType, back?: Blob) => {
    setDocumentBlob(front);
    if (back) setDocumentBackBlob(back);
    setDocType(type);
    setStep('liveness');
  };

  const handleLivenessCaptured = async (selfie: Blob) => {
    setSelfieBlob(selfie);
    if (!token || !documentBlob) return;

    setStep('submitting');
    setErrorMessage(null);

    try {
      const result = await api.submitKyc(token, docType, documentBlob, selfie, documentBackBlob || undefined);
      setVerificationResult(result);
      if (user) {
        setUser({ ...user, kycStatus: result.status });
      }
      setStep('result');
    } catch (err: any) {
      console.error('KYC submission error:', err);
      setErrorMessage(err.message || 'Verification pipeline encountered an error. Please try again.');
      setStep('liveness');
    }
  };

  const handleReset = () => {
    setDocumentBlob(null);
    setDocumentBackBlob(null);
    setSelfieBlob(null);
    setVerificationResult(null);
    setErrorMessage(null);
    setStep('document');
  };

  return (
    <div style={{ minHeight: '100vh', display: 'flex', flexDirection: 'column', background: 'var(--bg-base)' }}>
      <Navbar user={user} onLogout={handleLogout} />

      <main style={{
        flex: 1,
        padding: '18px max(12px, env(safe-area-inset-right)) max(40px, env(safe-area-inset-bottom)) max(12px, env(safe-area-inset-left))',
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center'
      }}>
        
        {/* Responsive Stepper */}
        {token && step !== 'auth' && (
          <div className="stepper-container" style={{ width: '100%' }}>
            <div className={`stepper-item ${step === 'document' ? 'active' : 'completed'}`}>
              <div className="stepper-circle">
                {step !== 'document' ? <CheckCircle2 size={14} /> : '1'}
              </div>
              <span className="stepper-text">Document</span>
            </div>

            <div className={`stepper-line ${step !== 'document' ? 'completed' : ''}`} />

            <div className={`stepper-item ${step === 'liveness' || step === 'submitting' ? 'active' : step === 'result' ? 'completed' : ''}`}>
              <div className="stepper-circle">
                {step === 'result' ? <CheckCircle2 size={14} /> : '2'}
              </div>
              <span className="stepper-text">Selfie</span>
            </div>

            <div className={`stepper-line ${step === 'result' ? 'completed' : ''}`} />

            <div className={`stepper-item ${step === 'result' ? 'active' : ''}`}>
              <div className="stepper-circle">3</div>
              <span className="stepper-text">Result</span>
            </div>
          </div>
        )}

        {/* Global Error Alert */}
        {errorMessage && (
          <div style={{ maxWidth: '640px', width: '100%', margin: '0 auto 16px' }}>
            <div className="alert alert-error">
              <AlertCircle size={16} style={{ flexShrink: 0, marginTop: '2px' }} />
              <span>{errorMessage}</span>
            </div>
          </div>
        )}

        {/* Dynamic Views */}
        <div style={{ width: '100%' }}>
          {step === 'auth' && <AuthScreen onSuccess={handleAuthSuccess} />}

          {step === 'document' && <DocumentWizard onComplete={handleDocumentCaptured} />}

          {step === 'liveness' && (
            <LivenessScanner
              onCapture={handleLivenessCaptured}
              onBack={() => setStep('document')}
            />
          )}

          {/* Staged Neural Processing View */}
          {step === 'submitting' && (
            <div style={{ maxWidth: '440px', margin: '40px auto', width: '100%', textAlign: 'center' }}>
              <div className="card fade-in" style={{ padding: '32px 20px' }}>
                
                <div style={{
                  width: '56px',
                  height: '56px',
                  borderRadius: '50%',
                  background: 'var(--primary-light)',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  margin: '0 auto 20px',
                }}>
                  <Loader2 size={28} color="var(--primary)" style={{ animation: 'spin 1.4s linear infinite' }} />
                </div>

                <h2 style={{ fontSize: '18px', fontWeight: 800, marginBottom: '4px', color: 'var(--text-primary)', letterSpacing: '-0.02em' }}>
                  Verifying Identity...
                </h2>
                <p style={{ color: 'var(--text-secondary)', fontSize: '13px', marginBottom: '20px', lineHeight: 1.45 }}>
                  Processing document OCR, facial vectors, and liveness frames.
                </p>

                {/* Milestone Checklist */}
                <div style={{ display: 'flex', flexDirection: 'column', gap: '8px', textAlign: 'left' }}>
                  {PROCESSING_STAGES.map((stage, idx) => {
                    const Icon = stage.icon;
                    const isDone = idx < activeStageIndex;
                    const isCurrent = idx === activeStageIndex;

                    return (
                      <div
                        key={stage.id}
                        style={{
                          display: 'flex',
                          alignItems: 'center',
                          gap: '10px',
                          padding: '9px 12px',
                          borderRadius: 'var(--radius-sm)',
                          background: isCurrent ? 'var(--primary-light)' : '#f8fafc',
                          border: `1px solid ${isCurrent ? 'var(--primary-border)' : 'var(--border)'}`,
                          transition: 'all 0.25s ease'
                        }}
                      >
                        <div style={{
                          width: '20px',
                          height: '20px',
                          borderRadius: '50%',
                          display: 'flex',
                          alignItems: 'center',
                          justifyContent: 'center',
                          background: isDone ? 'var(--emerald)' : isCurrent ? 'var(--primary)' : '#e2e8f0',
                          color: '#ffffff',
                          fontSize: '10px',
                          fontFamily: 'var(--font-mono)',
                          fontWeight: 700,
                          flexShrink: 0
                        }}>
                          {isDone ? <CheckCircle2 size={12} /> : idx + 1}
                        </div>

                        <span style={{
                          fontSize: '12px',
                          fontWeight: isCurrent ? 700 : 500,
                          color: isDone ? 'var(--emerald)' : isCurrent ? 'var(--primary)' : 'var(--text-secondary)',
                          fontFamily: 'var(--font-sans)',
                          flex: 1,
                          lineHeight: 1.3
                        }}>
                          {stage.label}
                        </span>

                        <Icon size={14} color={isDone ? 'var(--emerald)' : isCurrent ? 'var(--primary)' : 'var(--text-muted)'} style={{ flexShrink: 0 }} />
                      </div>
                    );
                  })}
                </div>

              </div>
            </div>
          )}

          {step === 'result' && verificationResult && (
            <VerificationDashboard result={verificationResult} onReset={handleReset} />
          )}
        </div>
      </main>
    </div>
  );
};
