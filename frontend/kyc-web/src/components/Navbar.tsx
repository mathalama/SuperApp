import React from 'react';
import { User } from '../types';
import { LogOut, ShieldCheck, User as UserIcon } from 'lucide-react';

interface NavbarProps {
  user: User | null;
  onLogout: () => void;
}

export const Navbar: React.FC<NavbarProps> = ({ user, onLogout }) => {
  const kycStatus = user?.kycStatus?.toUpperCase() || '';

  const getBadgeClass = () => {
    if (kycStatus === 'VERIFIED') return 'badge badge-verified';
    if (kycStatus === 'REJECTED') return 'badge badge-rejected';
    return 'badge badge-pending';
  };

  const getBadgeLabel = () => {
    if (kycStatus === 'VERIFIED') return 'Verified';
    if (kycStatus === 'REJECTED') return 'Rejected';
    if (kycStatus === 'IN_PROGRESS') return 'In Progress';
    if (kycStatus === 'MANUAL_REVIEW') return 'Manual Review';
    return 'Pending';
  };

  return (
    <header style={{
      background: '#ffffff',
      borderBottom: '1px solid var(--border)',
      padding: '0 28px',
      height: '64px',
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'space-between',
      position: 'sticky',
      top: 0,
      zIndex: 100,
      boxShadow: 'var(--shadow-xs)'
    }}>
      {/* Brand Identity */}
      <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
        <div style={{
          width: '36px',
          height: '36px',
          borderRadius: '10px',
          background: 'var(--primary)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          boxShadow: '0 2px 6px rgba(79, 70, 229, 0.25)',
        }}>
          <ShieldCheck size={20} color="#ffffff" />
        </div>
        <div>
          <div style={{
            fontSize: '16px',
            fontWeight: 800,
            color: 'var(--text-primary)',
            letterSpacing: '-0.02em',
            display: 'flex',
            alignItems: 'center',
            gap: '6px'
          }}>
            SuperApp <span style={{ color: 'var(--primary)', fontWeight: 700 }}>KYC</span>
          </div>
          <div style={{
            fontSize: '11px',
            color: 'var(--text-muted)',
            fontWeight: 600,
            letterSpacing: '0.2px',
          }}>
            Identity Verification
          </div>
        </div>
      </div>

      {/* User Status Bar */}
      {user && (
        <div style={{ display: 'flex', alignItems: 'center', gap: '14px' }}>
          <div style={{
            display: 'flex',
            alignItems: 'center',
            gap: '8px',
            background: '#f8fafc',
            padding: '5px 12px 5px 8px',
            borderRadius: 'var(--radius-full)',
            border: '1px solid var(--border)'
          }}>
            <div style={{
              width: '24px',
              height: '24px',
              borderRadius: '50%',
              background: 'var(--primary-light)',
              color: 'var(--primary)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
            }}>
              <UserIcon size={13} />
            </div>
            <span style={{ fontSize: '13px', fontWeight: 600, color: 'var(--text-primary)' }}>
              {user.username}
            </span>
            {kycStatus && (
              <span className={getBadgeClass()}>
                {getBadgeLabel()}
              </span>
            )}
          </div>

          <button
            onClick={onLogout}
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: '6px',
              padding: '7px 13px',
              background: '#ffffff',
              border: '1px solid var(--border)',
              borderRadius: 'var(--radius-sm)',
              fontSize: '13px',
              color: 'var(--text-secondary)',
              fontWeight: 500,
              cursor: 'pointer',
              transition: 'all 0.2s ease',
            }}
            onMouseEnter={(e) => {
              e.currentTarget.style.borderColor = 'var(--rose)';
              e.currentTarget.style.color = 'var(--rose)';
              e.currentTarget.style.background = 'var(--rose-light)';
            }}
            onMouseLeave={(e) => {
              e.currentTarget.style.borderColor = 'var(--border)';
              e.currentTarget.style.color = 'var(--text-secondary)';
              e.currentTarget.style.background = '#ffffff';
            }}
          >
            <LogOut size={14} />
            <span>Sign Out</span>
          </button>
        </div>
      )}
    </header>
  );
};
