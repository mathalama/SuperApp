import React, { useState } from 'react';
import { KycApplicationResponse } from '../types';
import { CheckCircle2, XCircle, AlertTriangle, RefreshCw, ChevronDown, ChevronUp, FileText, ScanFace, Fingerprint, Copy, Check, ShieldCheck } from 'lucide-react';

interface VerificationDashboardProps {
  result: KycApplicationResponse;
  onReset: () => void;
}

export const VerificationDashboard: React.FC<VerificationDashboardProps> = ({ result, onReset }) => {
  const [showRawJson, setShowRawJson] = useState(false);
  const [copiedField, setCopiedField] = useState<string | null>(null);

  const isVerified = result.status === 'VERIFIED';
  const isRejected = result.status === 'REJECTED';

  const copyToClipboard = (text: string, field: string) => {
    navigator.clipboard.writeText(text);
    setCopiedField(field);
    setTimeout(() => setCopiedField(null), 2000);
  };

  const getStatusConfig = () => {
    if (isVerified) return {
      icon: <CheckCircle2 size={38} color="var(--emerald)" />,
      title: 'Verification Successful',
      subtitle: 'Your identity document and facial biometrics have been authenticated and verified.',
      bg: 'var(--emerald-light)',
      border: 'var(--emerald-border)',
      badgeClass: 'badge badge-verified'
    };
    if (isRejected) return {
      icon: <XCircle size={38} color="var(--rose)" />,
      title: 'Verification Declined',
      subtitle: result.rejectionReason || 'The submitted documents or biometric check failed integrity thresholds.',
      bg: 'var(--rose-light)',
      border: 'var(--rose-border)',
      badgeClass: 'badge badge-rejected'
    };
    return {
      icon: <AlertTriangle size={38} color="var(--amber)" />,
      title: 'Manual Review Queued',
      subtitle: result.rejectionReason || 'Your verification is queued for manual compliance review.',
      bg: 'var(--amber-light)',
      border: 'var(--amber-border)',
      badgeClass: 'badge badge-pending'
    };
  };

  const status = getStatusConfig();

  const formatScore = (score: number | null) => {
    if (score === null || score === undefined) return 'N/A';
    return `${Math.round(score * 100)}%`;
  };

  const getScoreColor = (score: number | null, threshold: number) => {
    if (score === null || score === undefined) return 'var(--text-muted)';
    return score >= threshold ? 'var(--emerald)' : 'var(--rose)';
  };

  const extractedFields = [
    { label: 'First Name', value: result.firstName },
    { label: 'Last Name', value: result.lastName },
    { label: 'Document Number', value: result.documentNumber },
    { label: 'Nationality', value: result.nationality },
    { label: 'Date of Birth', value: result.dateOfBirth },
    { label: 'Expiry Date', value: result.expiryDate },
  ].filter(f => f.value);

  return (
    <div className="fade-in" style={{ maxWidth: '640px', margin: '0 auto', width: '100%' }}>
      
      {/* Status Hero Banner */}
      <div className="card" style={{
        padding: '24px 18px',
        marginBottom: '14px',
        textAlign: 'center',
        background: status.bg,
        borderColor: status.border,
      }}>
        <div style={{ marginBottom: '10px', display: 'flex', justifyContent: 'center' }}>
          {status.icon}
        </div>
        <div style={{ marginBottom: '6px' }}>
          <span className={status.badgeClass} style={{ fontSize: '11px' }}>
            STATUS: {result.status}
          </span>
        </div>
        <h2 style={{ fontSize: '1.25rem', fontWeight: 800, color: 'var(--text-primary)', marginBottom: '4px', letterSpacing: '-0.02em' }}>
          {status.title}
        </h2>
        <p style={{ fontSize: '13px', color: 'var(--text-secondary)', maxWidth: '440px', margin: '0 auto', lineHeight: 1.45 }}>
          {status.subtitle}
        </p>
      </div>

      {/* Biometric Scores Telemetry Grid */}
      <div style={{
        display: 'grid',
        gridTemplateColumns: 'repeat(3, 1fr)',
        gap: '8px',
        marginBottom: '14px'
      }}>
        
        {/* Liveness Score */}
        <div className="card stat-card">
          <div className="stat-label">
            <span style={{ display: 'flex', alignItems: 'center', gap: '3px' }}>
              <Fingerprint size={12} color="var(--primary)" /> Liveness
            </span>
          </div>
          <div className="stat-value" style={{ color: getScoreColor(result.livenessScore, 0.85) }}>
            {formatScore(result.livenessScore)}
          </div>
          <div className="stat-hint">Cutoff ≥ 85%</div>
        </div>

        {/* Face Match Score */}
        <div className="card stat-card">
          <div className="stat-label">
            <span style={{ display: 'flex', alignItems: 'center', gap: '3px' }}>
              <ScanFace size={12} color="var(--primary)" /> Match
            </span>
          </div>
          <div className="stat-value" style={{ color: getScoreColor(result.faceMatchScore, 0.70) }}>
            {formatScore(result.faceMatchScore)}
          </div>
          <div className="stat-hint">Cutoff ≥ 70%</div>
        </div>

        {/* MRZ Validity */}
        <div className="card stat-card">
          <div className="stat-label">
            <span style={{ display: 'flex', alignItems: 'center', gap: '3px' }}>
              <ShieldCheck size={12} color="var(--emerald)" /> MRZ
            </span>
          </div>
          <div className="stat-value" style={{
            color: result.mrzValid ? 'var(--emerald)' : 'var(--rose)',
            fontSize: '18px',
          }}>
            {result.mrzValid === null ? 'N/A' : result.mrzValid ? 'VALID' : 'INVALID'}
          </div>
          <div className="stat-hint">ICAO Valid</div>
        </div>

      </div>

      {/* Extracted Document OCR Data Sheet */}
      {extractedFields.length > 0 && (
        <div className="card" style={{ padding: '18px 16px', marginBottom: '14px' }}>
          <div style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            marginBottom: '14px',
            borderBottom: '1px solid var(--border)',
            paddingBottom: '10px'
          }}>
            <h3 style={{ fontSize: '14px', fontWeight: 800, color: 'var(--text-primary)', display: 'flex', alignItems: 'center', gap: '6px' }}>
              <FileText size={16} color="var(--primary)" />
              Extracted Document Data
            </h3>
            <span style={{ fontSize: '10px', fontFamily: 'var(--font-mono)', color: 'var(--text-muted)' }}>
              OCR / MRZ
            </span>
          </div>

          <div style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))',
            gap: '10px'
          }}>
            {extractedFields.map((field) => (
              <div
                key={field.label}
                style={{
                  background: '#f8fafc',
                  padding: '8px 10px',
                  borderRadius: 'var(--radius-sm)',
                  border: '1px solid var(--border)',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'space-between'
                }}
              >
                <div>
                  <div style={{ fontSize: '10px', color: 'var(--text-muted)', fontWeight: 600, textTransform: 'uppercase', letterSpacing: '0.3px', marginBottom: '1px' }}>
                    {field.label}
                  </div>
                  <div style={{ fontSize: '13px', fontWeight: 700, color: 'var(--text-primary)', fontFamily: 'var(--font-mono)' }}>
                    {field.value}
                  </div>
                </div>

                <button
                  type="button"
                  onClick={() => field.value && copyToClipboard(field.value, field.label)}
                  className="btn-ghost"
                  title="Copy to clipboard"
                  style={{ padding: '6px', minHeight: '34px', color: copiedField === field.label ? 'var(--emerald)' : 'var(--text-muted)' }}
                >
                  {copiedField === field.label ? <Check size={14} /> : <Copy size={14} />}
                </button>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Raw JSON Inspector */}
      <div className="card" style={{ padding: '12px 16px', marginBottom: '14px' }}>
        <button
          type="button"
          onClick={() => setShowRawJson(!showRawJson)}
          style={{
            background: 'none',
            border: 'none',
            cursor: 'pointer',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            width: '100%',
            color: 'var(--text-secondary)',
            fontSize: '13px',
            fontWeight: 600,
            fontFamily: 'var(--font-sans)',
            padding: 0,
            minHeight: '36px',
            touchAction: 'manipulation'
          }}
        >
          <span style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
            <FileText size={14} color="var(--primary)" />
            Server Raw Payload Response
          </span>
          {showRawJson ? <ChevronUp size={15} /> : <ChevronDown size={15} />}
        </button>

        {showRawJson && (
          <pre style={{
            marginTop: '10px',
            padding: '12px',
            background: '#f8fafc',
            borderRadius: 'var(--radius-sm)',
            fontSize: '11px',
            fontFamily: 'var(--font-mono)',
            color: 'var(--text-primary)',
            overflow: 'auto',
            maxHeight: '260px',
            border: '1px solid var(--border)',
            lineHeight: 1.4,
          }}>
            {JSON.stringify(result, null, 2)}
          </pre>
        )}
      </div>

      {/* Actions */}
      <div>
        <button onClick={onReset} className="btn-primary" style={{ width: '100%' }}>
          <RefreshCw size={16} /> {isVerified ? 'Verify Another Document' : 'Try Again'}
        </button>
      </div>

    </div>
  );
};
