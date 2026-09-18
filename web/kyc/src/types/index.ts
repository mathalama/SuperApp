export type DocumentType = 'PASSPORT' | 'ID_CARD' | 'DRIVING_LICENSE';

export type KycStatus = 'PENDING' | 'IN_PROGRESS' | 'VERIFIED' | 'REJECTED' | 'MANUAL_REVIEW';

export interface User {
  id: string;
  username: string;
  email: string;
  roles?: string[];
  kycStatus?: string;
}

export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  user: User;
}

export interface KycApplicationResponse {
  id: string;
  userId: string;
  status: KycStatus;
  documentType: DocumentType;
  livenessScore: number | null;
  faceMatchScore: number | null;
  mrzValid: boolean | null;
  firstName: string | null;
  lastName: string | null;
  documentNumber: string | null;
  dateOfBirth: string | null;
  expiryDate: string | null;
  nationality: string | null;
  rejectionReason: string | null;
  createdAt: string;
  updatedAt: string;
}
