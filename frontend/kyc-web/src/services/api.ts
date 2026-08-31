import { AuthResponse, KycApplicationResponse, DocumentType } from '../types';

const API_BASE_URL = (import.meta as any).env?.VITE_API_BASE_URL || '';

export const api = {
  // 1. Регистрация аккаунта
  async register(username: string, email: string, password: string): Promise<{ message: string }> {
    const res = await fetch(`${API_BASE_URL}/auth/register`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username, email, password }),
    });
    const data = await res.json();
    if (!res.ok) {
      throw new Error(data.message || data.error || 'Failed to register');
    }
    return data;
  },

  // 2. Подтверждение email 6-значным кодом
  async verifyEmail(email: string, code: string): Promise<{ message: string; verified: boolean }> {
    const res = await fetch(`${API_BASE_URL}/auth/verify-email`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email, code }),
    });
    const data = await res.json();
    if (!res.ok) {
      throw new Error(data.message || data.error || 'Verification failed');
    }
    return data;
  },

  // 3. Повторная отправка кода верификации
  async resendVerification(email: string): Promise<{ message: string }> {
    const res = await fetch(`${API_BASE_URL}/auth/resend-verification`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email }),
    });
    const data = await res.json();
    if (!res.ok) {
      throw new Error(data.message || data.error || 'Failed to resend code');
    }
    return data;
  },

  // 4. Запрос на сброс пароля (отправка ссылки/токена на почту)
  async forgotPassword(email: string): Promise<{ message: string }> {
    const res = await fetch(`${API_BASE_URL}/auth/forgot-password`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email }),
    });
    const data = await res.json();
    if (!res.ok) {
      throw new Error(data.message || data.error || 'Failed to request password reset');
    }
    return data;
  },

  // 5. Установка нового пароля по токену
  async resetPassword(token: string, newPassword: string): Promise<{ message: string }> {
    const res = await fetch(`${API_BASE_URL}/auth/reset-password`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ token, newPassword }),
    });
    const data = await res.json();
    if (!res.ok) {
      throw new Error(data.message || data.error || 'Failed to reset password');
    }
    return data;
  },

  // 6. Вход и получение JWT токена
  async login(login: string, password: string): Promise<AuthResponse> {
    const res = await fetch(`${API_BASE_URL}/auth/authenticate`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ login, password }),
    });
    const data = await res.json();
    if (!res.ok) {
      throw new Error(data.message || data.error || 'Authentication failed');
    }
    return data;
  },

  // 7. Отправка заявки на верификацию KYC
  async submitKyc(
    token: string,
    documentType: DocumentType,
    documentFront: Blob,
    selfie: Blob,
    documentBack?: Blob
  ): Promise<KycApplicationResponse> {
    const formData = new FormData();
    formData.append('documentType', documentType);
    formData.append('documentFront', documentFront, 'document_front.jpg');
    formData.append('selfie', selfie, 'selfie.jpg');
    if (documentBack) {
      formData.append('documentBack', documentBack, 'document_back.jpg');
    }

    const res = await fetch(`${API_BASE_URL}/api/kyc/verify`, {
      method: 'POST',
      headers: {
        Authorization: `Bearer ${token}`,
      },
      body: formData,
    });

    const data = await res.json();
    if (!res.ok) {
      throw new Error(data.message || data.error || 'KYC verification submission failed');
    }
    return data;
  },

  // 8. Получение статуса своей верификации
  async getMyKyc(token: string): Promise<KycApplicationResponse> {
    const res = await fetch(`${API_BASE_URL}/api/kyc/me`, {
      headers: {
        Authorization: `Bearer ${token}`,
      },
    });
    const data = await res.json();
    if (!res.ok) {
      throw new Error(data.message || data.error || 'Failed to fetch KYC status');
    }
    return data;
  },
};
