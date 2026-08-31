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
    if (kycStatus === 'MANUAL_REVIEW') return 'Review';
    return 'Pending';
  };

  return (
    <header style={{
      background: '#ffffff',
      borderBottom: '1px solid var(--border)',
      padding: '0 max(16px, env(safe-area-inset-right)) 0 max(16px, env(safe-area-inset-left))',
      height: '58px',
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'space-between',
      position: 'sticky',
      top: 0,
      zIndex: 100,
      boxShadow: 'var(--shadow-xs)'
    }}>
      {/* Brand Identity */}
      <div style={{ display: 'flex', alignItems: 'center', gap: '10px', minWidth: 0 }}>
        <div style={{
          width: '32px',
          height: '32px',
          borderRadius: '8px',
          background: 'var(--primary)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          boxShadow: '0 2px 6px rgba(79, 70, 229, 0.25)',
          flexShrink: 0
        }}>
          <ShieldCheck size={18} color="#ffffff" />
        </div>
        <div style={{ minWidth: 0 }}>
          <div style={{
            fontSize: '15px',
            fontWeight: 800,
            color: 'var(--text-primary)',
            letterSpacing: '-0.02em',
            display: 'flex',
            alignItems: 'center',
            gap: '4px',
            whiteSpace: 'nowrap'
          }}>
            SuperApp <span style={{ color: 'var(--primary)', fontWeight: 700 }}>KYC</span>
          </div>
        </div>
      </div>

      {/* User Status Bar */}
      {user && (
        <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
          <div style={{
            display: 'flex',
            alignItems: 'center',
            gap: '6px',
            background: '#f8fafc',
            padding: '4px 8px',
            borderRadius: 'var(--radius-full)',
            border: '1px solid var(--border)',
            maxWidth: '160px'
          }}>
            <div style={{
              width: '20px',
              height: '20px',
              borderRadius: '50%',
              background: 'var(--primary-light)',
              color: 'var(--primary)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              flexShrink: 0
            }}>
              <UserIcon size={11} />
            </div>
            <span style={{
              fontSize: '12px',
              fontWeight: 600,
              color: 'var(--text-primary)',
              overflow: 'hidden',
              textOverflow: 'ellipsis',
              whiteSpace: 'nowrap'
            }}>
              {user.username}
            </span>
            {kycStatus && (
              <span className={getBadgeClass()} style={{ fontSize: '10px', padding: '2px 6px' }}>
                {getBadgeLabel()}
              </span>
            )}
          </div>

          <button
            onClick={onLogout}
            title="Sign Out"
            style={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              gap: '4px',
              height: '34px',
              padding: '0 10px',
              background: '#ffffff',
              border: '1px solid var(--border)',
              borderRadius: 'var(--radius-sm)',
              fontSize: '12px',
              color: 'var(--text-secondary)',
              fontWeight: 500,
              cursor: 'pointer',
              touchAction: 'manipulation',
              transition: 'all 0.2s ease',
            }}
          >
            <LogOut size={13} />
            <span className="navbar-logout-text" style={{ display: 'inline' }}>Exit</span>
          </button>
        </div>
      )}
    </header>
  );
};
