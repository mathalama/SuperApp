CREATE TABLE IF NOT EXISTS kyc_applications (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    document_type VARCHAR(30) NOT NULL,
    
    document_front_key VARCHAR(500) NOT NULL,
    document_back_key VARCHAR(500),
    selfie_key VARCHAR(500) NOT NULL,
    
    liveness_score DOUBLE PRECISION,
    face_match_score DOUBLE PRECISION,
    mrz_valid BOOLEAN,
    
    extracted_first_name VARCHAR(100),
    extracted_last_name VARCHAR(100),
    extracted_document_number VARCHAR(50),
    extracted_date_of_birth DATE,
    extracted_expiry_date DATE,
    extracted_gender VARCHAR(10),
    extracted_nationality VARCHAR(50),
    
    raw_ocr_data TEXT,
    rejection_reason TEXT,
    
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL
);

CREATE INDEX idx_kyc_applications_user_id ON kyc_applications(user_id);
CREATE INDEX idx_kyc_applications_status ON kyc_applications(status);
