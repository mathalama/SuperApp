import React, { useState, useRef } from 'react';
import { api } from '../services/api';
import { User } from '../types';
import { UserPlus, KeyRound, AlertCircle, ArrowRight, CheckCircle2, RefreshCw, Lock, Mail, Eye, EyeOff, ShieldCheck } from 'lucide-react';

interface AuthScreenProps {
  onSuccess: (token: string, user: User) => void;
}

type AuthView = 'login' | 'register' | 'verify' | 'forgot_password' | 'reset_password';

export const AuthScreen: React.FC<AuthScreenProps> = ({ onSuccess }) => {
  const [view, setView] = useState<AuthView>('login');

  const [login, setLogin] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [username, setUsername] = useState('');
  
  // 6-digit OTP state
  const [otpDigits, setOtpDigits] = useState<string[]>(['', '', '', '', '', '']);
  const otpInputsRef = useRef<(HTMLInputElement | null)[]>([]);

  const [resetToken, setResetToken] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');

  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [successMsg, setSuccessMsg] = useState<string | null>(null);

  const resetMessages = () => { setError(null); setSuccessMsg(null); };

  const handleOtpChange = (index: number, value: string) => {
    if (!/^\d*$/.test(value)) return;
    const newDigits = [...otpDigits];
    
    // Support paste of entire 6-digit code
    if (value.length > 1) {
      const pasted = value.slice(0, 6).split('');
      for (let i = 0; i < 6; i++) {
        newDigits[i] = pasted[i] || '';
      }
      setOtpDigits(newDigits);
      const nextIndex = Math.min(pasted.length, 5);
      otpInputsRef.current[nextIndex]?.focus();
      return;
    }

    newDigits[index] = value;
    setOtpDigits(newDigits);

    // Auto-advance to next input
    if (value && index < 5) {
      otpInputsRef.current[index + 1]?.focus();
    }
  };

  const handleOtpKeyDown = (index: number, e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Backspace' && !otpDigits[index] && index > 0) {
      otpInputsRef.current[index - 1]?.focus();
    }
  };

  const handleLogin = async (e: React.FormEvent) => {
    e.preventDefault();
    resetMessages();
    setLoading(true);
    try {
      const authData = await api.login(login, password);
      onSuccess(authData.accessToken, authData.user);
    } catch (err: any) {
      if (err.message?.includes('pending email verification')) {
        setView('verify');
        setError('Account pending email verification. Enter the 6-digit code from your email.');
      } else {
        setError(err.message || 'Authentication failed. Please check your credentials.');
      }
    } finally { setLoading(false); }
  };

  const handleRegister = async (e: React.FormEvent) => {
    e.preventDefault();
    resetMessages();
    setLoading(true);
    try {
      await api.register(username, login, password);
      setSuccessMsg('Verification code sent to your email. Enter the 6-digit code below.');
      setView('verify');
    } catch (err: any) {
      setError(err.message || 'Registration failed');
    } finally { setLoading(false); }
  };

  const handleVerifyCode = async (e: React.FormEvent) => {
    e.preventDefault();
    resetMessages();
    const verificationCode = otpDigits.join('');
    if (verificationCode.length < 6) {
      setError('Please enter all 6 digits.');
      return;
    }
    setLoading(true);
    try {
      await api.verifyEmail(login, verificationCode);
      setSuccessMsg('Email verified successfully! Signing in...');
      const authData = await api.login(login, password);
      onSuccess(authData.accessToken, authData.user);
    } catch (err: any) {
      setError(err.message || 'Invalid or expired verification code');
    } finally { setLoading(false); }
  };

  const handleResendCode = async () => {
    if (!login) { setError('Please enter your email to resend code.'); return; }
    resetMessages();
    setLoading(true);
    try {
      await api.resendVerification(login);
      setSuccessMsg('New 6-digit code sent to your email!');
    } catch (err: any) {
      setError(err.message || 'Failed to resend code');
    } finally { setLoading(false); }
  };

  const handleForgotPassword = async (e: React.FormEvent) => {
    e.preventDefault();
    resetMessages();
    setLoading(true);
    try {
      const res = await api.forgotPassword(login);
      setSuccessMsg(res.message || 'Reset token dispatched to your email.');
      setView('reset_password');
    } catch (err: any) {
      setError(err.message || 'Password reset request failed');
    } finally { setLoading(false); }
  };

  const handleResetPassword = async (e: React.FormEvent) => {
    e.preventDefault();
    resetMessages();
    if (newPassword !== confirmPassword) { setError('Passwords do not match'); return; }
    setLoading(true);
    try {
      const res = await api.resetPassword(resetToken, newPassword);
      setSuccessMsg(res.message || 'Password updated successfully! You can now sign in.');
      setPassword('');
      setView('login');
    } catch (err: any) {
      setError(err.message || 'Invalid reset token or password change failed');
    } finally { setLoading(false); }
  };

  return (
    <div style={{ maxWidth: '440px', margin: '16px auto 40px', width: '100%' }}>
      <div className="card fade-in" style={{ padding: '28px 20px' }}>
        
        {/* Segmented Switcher */}
        {(view === 'login' || view === 'register') && (
          <div style={{
            display: 'flex',
            background: '#f1f5f9',
            borderRadius: 'var(--radius-md)',
            padding: '4px',
            marginBottom: '24px',
            border: '1px solid var(--border)'
          }}>
            <button
              type="button"
              onClick={() => { resetMessages(); setView('login'); }}
              style={{
                flex: 1,
                minHeight: '40px',
                padding: '8px 12px',
                borderRadius: 'var(--radius-sm)',
                border: 'none',
                background: view === 'login' ? '#ffffff' : 'transparent',
                color: view === 'login' ? 'var(--text-primary)' : 'var(--text-secondary)',
                fontWeight: 700,
                fontSize: '13px',
                cursor: 'pointer',
                touchAction: 'manipulation',
                transition: 'all 0.2s ease',
                boxShadow: view === 'login' ? '0 1px 3px rgba(0,0,0,0.08)' : 'none'
              }}
            >
              Sign In
            </button>
            <button
              type="button"
              onClick={() => { resetMessages(); setView('register'); }}
              style={{
                flex: 1,
                minHeight: '40px',
                padding: '8px 12px',
                borderRadius: 'var(--radius-sm)',
                border: 'none',
                background: view === 'register' ? '#ffffff' : 'transparent',
                color: view === 'register' ? 'var(--text-primary)' : 'var(--text-secondary)',
                fontWeight: 700,
                fontSize: '13px',
                cursor: 'pointer',
                touchAction: 'manipulation',
                transition: 'all 0.2s ease',
                boxShadow: view === 'register' ? '0 1px 3px rgba(0,0,0,0.08)' : 'none'
              }}
            >
              Create Account
            </button>
          </div>
        )}

        {/* Header */}
        <div style={{ textAlign: 'center', marginBottom: '20px' }}>
          <div style={{
            display: 'inline-flex',
            padding: '12px',
            borderRadius: '14px',
            background: view === 'verify' ? 'var(--emerald-light)'
              : (view === 'forgot_password' || view === 'reset_password') ? 'var(--amber-light)'
              : 'var(--primary-light)',
            marginBottom: '10px',
            border: `1px solid ${
              view === 'verify' ? 'var(--emerald-border)'
              : (view === 'forgot_password' || view === 'reset_password') ? 'var(--amber-border)'
              : 'var(--primary-border)'
            }`,
          }}>
            {view === 'verify' ? <KeyRound size={24} color="var(--emerald)" />
              : (view === 'forgot_password' || view === 'reset_password') ? <Lock size={24} color="var(--amber)" />
              : view === 'register' ? <UserPlus size={24} color="var(--primary)" />
              : <ShieldCheck size={24} color="var(--primary)" />
            }
          </div>

          <h2 style={{ fontSize: '20px', fontWeight: 800, letterSpacing: '-0.02em', color: 'var(--text-primary)', marginBottom: '4px' }}>
            {view === 'verify' ? 'Email Verification'
              : view === 'register' ? 'Create Account'
              : view === 'forgot_password' ? 'Reset Password'
              : view === 'reset_password' ? 'Set New Password'
              : 'Sign In'}
          </h2>

          <p style={{ color: 'var(--text-secondary)', fontSize: '13px', lineHeight: 1.4 }}>
            {view === 'verify' ? `Enter the 6-digit code sent to ${login || 'your email'}`
              : view === 'forgot_password' ? 'Enter your registered email to receive a password reset token'
              : view === 'reset_password' ? 'Enter the token from your email and set a new password'
              : view === 'register' ? 'Sign up to start fast KYC verification'
              : 'Enter your credentials to continue'}
          </p>
        </div>

        {/* Alerts */}
        {error && (
          <div className="alert alert-error" style={{ marginBottom: '16px' }}>
            <AlertCircle size={16} style={{ flexShrink: 0, marginTop: '2px' }} />
            <span>{error}</span>
          </div>
        )}
        {successMsg && (
          <div className="alert alert-success" style={{ marginBottom: '16px' }}>
            <CheckCircle2 size={16} style={{ flexShrink: 0, marginTop: '2px' }} />
            <span>{successMsg}</span>
          </div>
        )}

        {/* SIGN IN FORM */}
        {view === 'login' && (
          <form onSubmit={handleLogin} style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
            <div>
              <label className="label">Email or Username</label>
              <div style={{ position: 'relative' }}>
                <input
                  type="text"
                  value={login}
                  onChange={(e) => setLogin(e.target.value)}
                  placeholder="name@example.com"
                  required
                  className="input"
                  style={{ paddingLeft: '38px' }}
                />
                <Mail size={16} color="var(--text-muted)" style={{ position: 'absolute', left: '12px', top: '50%', transform: 'translateY(-50%)' }} />
              </div>
            </div>

            <div>
              <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '6px' }}>
                <label className="label" style={{ marginBottom: 0 }}>Password</label>
                <button
                  type="button"
                  onClick={() => { resetMessages(); setView('forgot_password'); }}
                  className="btn-ghost"
                  style={{ padding: '0 4px', fontSize: '12px', color: 'var(--primary)', minHeight: 'auto' }}
                >
                  Forgot password?
                </button>
              </div>
              <div style={{ position: 'relative' }}>
                <input
                  type={showPassword ? 'text' : 'password'}
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  placeholder="••••••••"
                  required
                  className="input"
                  style={{ paddingLeft: '38px', paddingRight: '38px' }}
                />
                <Lock size={16} color="var(--text-muted)" style={{ position: 'absolute', left: '12px', top: '50%', transform: 'translateY(-50%)' }} />
                <button
                  type="button"
                  onClick={() => setShowPassword(!showPassword)}
                  style={{
                    position: 'absolute',
                    right: '12px',
                    top: '50%',
                    transform: 'translateY(-50%)',
                    background: 'none',
                    border: 'none',
                    color: 'var(--text-muted)',
                    cursor: 'pointer',
                    display: 'flex',
                    padding: '4px'
                  }}
                >
                  {showPassword ? <EyeOff size={16} /> : <Eye size={16} />}
                </button>
              </div>
            </div>

            <button type="submit" disabled={loading} className="btn-primary" style={{ width: '100%', marginTop: '6px' }}>
              {loading ? (
                <>
                  <RefreshCw size={16} style={{ animation: 'spin 1.2s linear infinite' }} />
                  Signing In...
                </>
              ) : (
                <>
                  Sign In <ArrowRight size={16} />
                </>
              )}
            </button>
          </form>
        )}

        {/* REGISTER FORM */}
        {view === 'register' && (
          <form onSubmit={handleRegister} style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
            <div>
              <label className="label">Username</label>
              <input
                type="text"
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                placeholder="johndoe"
                required
                className="input"
              />
            </div>

            <div>
              <label className="label">Email Address</label>
              <input
                type="email"
                value={login}
                onChange={(e) => setLogin(e.target.value)}
                placeholder="name@example.com"
                required
                className="input"
              />
            </div>

            <div>
              <label className="label">Password</label>
              <input
                type="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                placeholder="••••••••"
                required
                className="input"
              />
            </div>

            <button type="submit" disabled={loading} className="btn-primary" style={{ width: '100%', marginTop: '6px' }}>
              {loading ? (
                <>
                  <RefreshCw size={16} style={{ animation: 'spin 1.2s linear infinite' }} />
                  Creating Account...
                </>
              ) : (
                <>
                  Create Account <ArrowRight size={16} />
                </>
              )}
            </button>
          </form>
        )}

        {/* 6-DIGIT OTP VERIFY (FLUID RESPONSIVE FOR MOBILE) */}
        {view === 'verify' && (
          <form onSubmit={handleVerifyCode} style={{ display: 'flex', flexDirection: 'column', gap: '18px' }}>
            <div>
              <label className="label" style={{ textAlign: 'center', marginBottom: '12px' }}>
                Enter 6-Digit Code
              </label>
              <div style={{
                display: 'flex',
                gap: 'clamp(4px, 1.8vw, 8px)',
                justifyContent: 'center',
                width: '100%'
              }}>
                {otpDigits.map((digit, index) => (
                  <input
                    key={index}
                    ref={(el) => (otpInputsRef.current[index] = el)}
                    type="text"
                    inputMode="numeric"
                    maxLength={6}
                    value={digit}
                    onChange={(e) => handleOtpChange(index, e.target.value)}
                    onKeyDown={(e) => handleOtpKeyDown(index, e)}
                    style={{
                      width: 'clamp(36px, 12vw, 48px)',
                      height: 'clamp(44px, 14vw, 54px)',
                      textAlign: 'center',
                      fontSize: 'clamp(18px, 5vw, 22px)',
                      fontWeight: 700,
                      fontFamily: 'var(--font-mono)',
                      background: '#ffffff',
                      color: 'var(--text-primary)',
                      border: digit ? '2px solid var(--primary)' : '1px solid var(--border)',
                      borderRadius: 'var(--radius-sm)',
                      outline: 'none',
                      boxShadow: digit ? '0 0 0 3px rgba(79, 70, 229, 0.12)' : 'none',
                      transition: 'all 0.2s ease',
                      flexShrink: 1
                    }}
                  />
                ))}
              </div>
            </div>

            <button
              type="submit"
              disabled={loading || otpDigits.some((d) => !d)}
              className="btn-primary"
              style={{ width: '100%' }}
            >
              {loading ? 'Verifying...' : 'Verify Email'} <ArrowRight size={16} />
            </button>

            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <button
                type="button"
                onClick={handleResendCode}
                disabled={loading}
                className="btn-ghost"
                style={{ color: 'var(--primary)', fontSize: '12px', padding: '4px' }}
              >
                <RefreshCw size={13} style={{ marginRight: '4px' }} /> Resend code
              </button>

              <button
                type="button"
                onClick={() => { resetMessages(); setView('login'); }}
                className="btn-ghost"
                style={{ fontSize: '12px', padding: '4px' }}
              >
                Back to Sign In
              </button>
            </div>
          </form>
        )}

        {/* FORGOT PASSWORD */}
        {view === 'forgot_password' && (
          <form onSubmit={handleForgotPassword} style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
            <div>
              <label className="label">Account Email</label>
              <input
                type="email"
                value={login}
                onChange={(e) => setLogin(e.target.value)}
                placeholder="name@example.com"
                required
                className="input"
              />
            </div>

            <button type="submit" disabled={loading} className="btn-primary" style={{ width: '100%' }}>
              {loading ? 'Sending...' : 'Send Reset Token'} <ArrowRight size={16} />
            </button>

            <div style={{ display: 'flex', justifyContent: 'space-between' }}>
              <button
                type="button"
                onClick={() => { resetMessages(); setView('reset_password'); }}
                className="btn-ghost"
                style={{ color: 'var(--primary)', fontSize: '12px' }}
              >
                I have a token
              </button>
              <button
                type="button"
                onClick={() => { resetMessages(); setView('login'); }}
                className="btn-ghost"
                style={{ fontSize: '12px' }}
              >
                Back to Sign In
              </button>
            </div>
          </form>
        )}

        {/* RESET PASSWORD */}
        {view === 'reset_password' && (
          <form onSubmit={handleResetPassword} style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
            <div>
              <label className="label">Reset Token from Email</label>
              <input
                type="text"
                value={resetToken}
                onChange={(e) => setResetToken(e.target.value)}
                placeholder="Paste token here"
                required
                className="input"
                style={{ fontFamily: 'var(--font-mono)', fontSize: '13px' }}
              />
            </div>

            <div>
              <label className="label">New Password</label>
              <input
                type="password"
                value={newPassword}
                onChange={(e) => setNewPassword(e.target.value)}
                placeholder="••••••••"
                required
                className="input"
              />
            </div>

            <div>
              <label className="label">Confirm New Password</label>
              <input
                type="password"
                value={confirmPassword}
                onChange={(e) => setConfirmPassword(e.target.value)}
                placeholder="••••••••"
                required
                className="input"
              />
            </div>

            <button type="submit" disabled={loading} className="btn-primary" style={{ width: '100%' }}>
              {loading ? 'Updating...' : 'Change Password'} <ArrowRight size={16} />
            </button>

            <div style={{ textAlign: 'center' }}>
              <button
                type="button"
                onClick={() => { resetMessages(); setView('login'); }}
                className="btn-ghost"
                style={{ fontSize: '12px' }}
              >
                Back to Sign In
              </button>
            </div>
          </form>
        )}

      </div>
    </div>
  );
};
