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
      icon: <CheckCircle2 size={44} color="var(--emerald)" />,
      title: 'Verification Successful',
      subtitle: 'Your identity document and facial biometrics have been authenticated and verified.',
      bg: 'var(--emerald-light)',
      border: 'var(--emerald-border)',
      badgeClass: 'badge badge-verified'
    };
    if (isRejected) return {
      icon: <XCircle size={44} color="var(--rose)" />,
      title: 'Verification Declined',
      subtitle: result.rejectionReason || 'The submitted documents or biometric check failed integrity thresholds.',
      bg: 'var(--rose-light)',
      border: 'var(--rose-border)',
      badgeClass: 'badge badge-rejected'
    };
    return {
      icon: <AlertTriangle size={44} color="var(--amber)" />,
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
    <div className="fade-in" style={{ maxWidth: '640px', margin: '0 auto' }}>
      
      {/* Status Hero Banner */}
      <div className="card" style={{
        padding: '32px 28px',
        marginBottom: '18px',
        textAlign: 'center',
        background: status.bg,
        borderColor: status.border,
      }}>
        <div style={{ marginBottom: '12px', display: 'flex', justifyContent: 'center' }}>
          {status.icon}
        </div>
        <div style={{ marginBottom: '8px' }}>
          <span className={status.badgeClass} style={{ fontSize: '12px' }}>
            STATUS: {result.status}
          </span>
        </div>
        <h2 style={{ fontSize: '22px', fontWeight: 800, color: 'var(--text-primary)', marginBottom: '6px', letterSpacing: '-0.02em' }}>
          {status.title}
        </h2>
        <p style={{ fontSize: '14px', color: 'var(--text-secondary)', maxWidth: '440px', margin: '0 auto', lineHeight: 1.5 }}>
          {status.subtitle}
        </p>
      </div>

      {/* Biometric Scores Telemetry Grid */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: '12px', marginBottom: '18px' }}>
        
        {/* Liveness Score */}
        <div className="card stat-card">
          <div className="stat-label">
            <span style={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
              <Fingerprint size={13} color="var(--primary)" /> Liveness
            </span>
          </div>
          <div className="stat-value" style={{ color: getScoreColor(result.livenessScore, 0.85) }}>
            {formatScore(result.livenessScore)}
          </div>
          <div className="stat-hint">Threshold ≥ 85%</div>
        </div>

        {/* Face Match Score */}
        <div className="card stat-card">
          <div className="stat-label">
            <span style={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
              <ScanFace size={13} color="var(--primary)" /> Face Match
            </span>
          </div>
          <div className="stat-value" style={{ color: getScoreColor(result.faceMatchScore, 0.70) }}>
            {formatScore(result.faceMatchScore)}
          </div>
          <div className="stat-hint">Threshold ≥ 70%</div>
        </div>

        {/* MRZ Validity */}
        <div className="card stat-card">
          <div className="stat-label">
            <span style={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
              <ShieldCheck size={13} color="var(--emerald)" /> MRZ Check
            </span>
          </div>
          <div className="stat-value" style={{
            color: result.mrzValid ? 'var(--emerald)' : 'var(--rose)',
            fontSize: '22px',
          }}>
            {result.mrzValid === null ? 'N/A' : result.mrzValid ? 'VALID' : 'INVALID'}
          </div>
          <div className="stat-hint">ICAO Checksums</div>
        </div>

      </div>

      {/* Extracted Document OCR Data Sheet */}
      {extractedFields.length > 0 && (
        <div className="card" style={{ padding: '24px 22px', marginBottom: '18px' }}>
          <div style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            marginBottom: '18px',
            borderBottom: '1px solid var(--border)',
            paddingBottom: '12px'
          }}>
            <h3 style={{ fontSize: '15px', fontWeight: 800, color: 'var(--text-primary)', display: 'flex', alignItems: 'center', gap: '8px' }}>
              <FileText size={17} color="var(--primary)" />
              Extracted Document Data (OCR / MRZ)
            </h3>
            <span style={{ fontSize: '11px', fontFamily: 'var(--font-mono)', color: 'var(--text-muted)' }}>
              AUTOMATICALLY EXTRACTED
            </span>
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '14px 18px' }}>
            {extractedFields.map((field) => (
              <div
                key={field.label}
                style={{
                  background: '#f8fafc',
                  padding: '10px 12px',
                  borderRadius: 'var(--radius-sm)',
                  border: '1px solid var(--border)',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'space-between'
                }}
              >
                <div>
                  <div style={{ fontSize: '11px', color: 'var(--text-muted)', fontWeight: 600, textTransform: 'uppercase', letterSpacing: '0.3px', marginBottom: '2px' }}>
                    {field.label}
                  </div>
                  <div style={{ fontSize: '14px', fontWeight: 700, color: 'var(--text-primary)', fontFamily: 'var(--font-mono)' }}>
                    {field.value}
                  </div>
                </div>

                <button
                  type="button"
                  onClick={() => field.value && copyToClipboard(field.value, field.label)}
                  className="btn-ghost"
                  title="Copy to clipboard"
                  style={{ padding: '4px', color: copiedField === field.label ? 'var(--emerald)' : 'var(--text-muted)' }}
                >
                  {copiedField === field.label ? <Check size={14} /> : <Copy size={14} />}
                </button>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Raw JSON Inspector */}
      <div className="card" style={{ padding: '16px 20px', marginBottom: '18px' }}>
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
          }}
        >
          <span style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
            <FileText size={15} color="var(--primary)" />
            Server Raw Response Payload
          </span>
          {showRawJson ? <ChevronUp size={16} /> : <ChevronDown size={16} />}
        </button>

        {showRawJson && (
          <pre style={{
            marginTop: '12px',
            padding: '14px',
            background: '#f8fafc',
            borderRadius: 'var(--radius-sm)',
            fontSize: '12px',
            fontFamily: 'var(--font-mono)',
            color: 'var(--text-primary)',
            overflow: 'auto',
            maxHeight: '300px',
            border: '1px solid var(--border)',
            lineHeight: 1.5,
          }}>
            {JSON.stringify(result, null, 2)}
          </pre>
        )}
      </div>

      {/* Actions */}
      <div style={{ display: 'flex', gap: '12px' }}>
        <button onClick={onReset} className="btn-primary" style={{ width: '100%', padding: '13px' }}>
          <RefreshCw size={16} /> {isVerified ? 'Verify Another Document' : 'Try Again'}
        </button>
      </div>

    </div>
  );
};
