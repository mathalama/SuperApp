CREATE TABLE user_profiles (
    id              UUID PRIMARY KEY,
    username        VARCHAR(255) NOT NULL,
    email           VARCHAR(255) NOT NULL,
    avatar_url      VARCHAR(500),
    bio             TEXT,
    phone_number    VARCHAR(20),
    date_of_birth   DATE,
    locale          VARCHAR(10) DEFAULT 'ru',
    timezone        VARCHAR(50) DEFAULT 'Asia/Tashkent',
    profile_status  VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_user_profiles_username ON user_profiles(username);
CREATE INDEX idx_user_profiles_email ON user_profiles(email);
CREATE INDEX idx_user_profiles_status ON user_profiles(profile_status);
