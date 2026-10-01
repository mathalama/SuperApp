CREATE INDEX IF NOT EXISTS idx_kyc_applications_user_created ON kyc_applications (user_id, created_at DESC);
