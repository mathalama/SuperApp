-- This script runs automatically when the postgres container starts for the first time.
-- It creates additional databases beyond the default POSTGRES_DB.
CREATE DATABASE identity_db;
CREATE DATABASE user_db;
CREATE DATABASE kyc_db;