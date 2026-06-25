-- Remove deprecated/unreferenced tables
DROP TABLE IF EXISTS recommendation_items;
DROP TABLE IF EXISTS recommendations;
DROP TABLE IF EXISTS ai_async_jobs;
DROP TABLE IF EXISTS fraud_cases;

-- Align users table with JPA entity fields used during auth/registration
ALTER TABLE IF EXISTS users
    ADD COLUMN IF NOT EXISTS failed_login_attempts INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS lock_until TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS last_login_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS phone_number VARCHAR(30),
    ADD COLUMN IF NOT EXISTS email_verified BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS email_verified_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS two_factor_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS two_factor_method VARCHAR(30) NOT NULL DEFAULT 'NONE';

UPDATE users
SET email_verified = TRUE
WHERE email_verified IS NULL;

-- Ensure refresh token lookup index exists without failing if already present
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_user_id ON refresh_tokens (user_id);

-- Assessment: profile skill test snapshot + public slug
ALTER TABLE profiles
    ADD COLUMN IF NOT EXISTS real_score INTEGER,
    ADD COLUMN IF NOT EXISTS test_taken_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS skill_real_scores TEXT,
    ADD COLUMN IF NOT EXISTS test_passed BOOLEAN,
    ADD COLUMN IF NOT EXISTS public_slug VARCHAR(80);

CREATE UNIQUE INDEX IF NOT EXISTS idx_profiles_public_slug ON profiles (public_slug)
    WHERE public_slug IS NOT NULL;

CREATE TABLE IF NOT EXISTS candidate_test_results (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users (id),
    overall_score INTEGER,
    skill_scores TEXT,
    taken_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    passed BOOLEAN,
    test_type VARCHAR(20)
);

CREATE INDEX IF NOT EXISTS idx_ctr_user_taken ON candidate_test_results (user_id, taken_at DESC);



CREATE TABLE IF NOT EXISTS job_matches (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users (id),
    job_url VARCHAR(2000),
    job_title VARCHAR(500),
    match_score INTEGER,
    skill_breakdown TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS campaigns (
    id UUID PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    template_id VARCHAR(80),
    template_name VARCHAR(200),
    channel VARCHAR(20),
    target_group VARCHAR(40),
    recipient_count INTEGER,
    status VARCHAR(20),
    scheduled_at TIMESTAMPTZ,
    sent_count INTEGER,
    failed_count INTEGER,
    open_rate DOUBLE PRECISION,
    click_rate DOUBLE PRECISION,
    is_paused BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_campaigns_created_at ON campaigns (created_at DESC);

CREATE TABLE IF NOT EXISTS candidate_badges (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users (id),
    skill VARCHAR(200) NOT NULL,
    score INTEGER,
    issued_at TIMESTAMPTZ,
    badge_svg_url VARCHAR(1000),
    CONSTRAINT uk_user_skill_badge UNIQUE (user_id, skill)
);

-- Auth hardening: email verification + two-factor one-time codes
CREATE TABLE IF NOT EXISTS email_verification_tokens (
    id UUID PRIMARY KEY,
    token VARCHAR(120) NOT NULL UNIQUE,
    user_id UUID NOT NULL REFERENCES users (id),
    expiry_date TIMESTAMP NOT NULL,
    used BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_email_verification_user_id ON email_verification_tokens (user_id);

CREATE TABLE IF NOT EXISTS two_factor_codes (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users (id),
    purpose VARCHAR(20) NOT NULL,
    code_hash VARCHAR(128) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    used BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_two_factor_user_id ON two_factor_codes (user_id);
CREATE INDEX IF NOT EXISTS idx_two_factor_purpose ON two_factor_codes (purpose);
CREATE INDEX IF NOT EXISTS idx_two_factor_expires ON two_factor_codes (expires_at);

-- In-app notifications center
CREATE TABLE IF NOT EXISTS user_notifications (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users (id),
    type VARCHAR(20) NOT NULL,
    category VARCHAR(40) NOT NULL,
    title VARCHAR(180) NOT NULL,
    body TEXT NOT NULL,
    target_url VARCHAR(500),
    email_alert BOOLEAN NOT NULL DEFAULT FALSE,
    emailed_at TIMESTAMPTZ,
    read_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_user_notifications_user_id ON user_notifications (user_id);
CREATE INDEX IF NOT EXISTS idx_user_notifications_read_at ON user_notifications (read_at);
CREATE INDEX IF NOT EXISTS idx_user_notifications_created_at ON user_notifications (created_at);

-- GDPR/self-service privacy settings
CREATE TABLE IF NOT EXISTS user_privacy_settings (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL UNIQUE REFERENCES users (id),
    marketing_emails_consent BOOLEAN NOT NULL DEFAULT FALSE,
    analytics_consent BOOLEAN NOT NULL DEFAULT TRUE,
    profile_visibility_consent BOOLEAN NOT NULL DEFAULT TRUE,
    data_processing_consent BOOLEAN NOT NULL DEFAULT TRUE,
    consent_version VARCHAR(30) NOT NULL DEFAULT 'v1',
    consent_updated_at TIMESTAMPTZ,
    data_retention_days INTEGER NOT NULL DEFAULT 365,
    delete_requested_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_user_privacy_user_id ON user_privacy_settings (user_id);

-- Skills table
CREATE TABLE IF NOT EXISTS skills (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users (id),
    nom VARCHAR(200) NOT NULL,
    type VARCHAR(20) NOT NULL,
    niveau INTEGER NOT NULL,
    description TEXT,
    source VARCHAR(30),
    date_evaluation TIMESTAMP NOT NULL DEFAULT NOW(),
    validee BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS idx_skills_user_id ON skills (user_id);
CREATE INDEX IF NOT EXISTS idx_skills_type ON skills (type);

-- Predictions table
CREATE TABLE IF NOT EXISTS predictions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users (id),
    analyse_text TEXT NOT NULL,
    recommandation_soft TEXT,
    recommandation_tech TEXT,
    score_confiance DOUBLE PRECISION,
    statut VARCHAR(30) NOT NULL DEFAULT 'EN_ANALYSE',
    date_prediction TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_predictions_user_id ON predictions (user_id);

-- Formations table
CREATE TABLE IF NOT EXISTS formations (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users (id),
    prediction_id UUID REFERENCES predictions (id),
    titre VARCHAR(300) NOT NULL,
    description TEXT,
    duree INTEGER,
    fournisseur VARCHAR(200),
    url VARCHAR(500),
    date_proposition TIMESTAMP NOT NULL DEFAULT NOW(),
    date_debut TIMESTAMP,
    date_fin TIMESTAMP,
    progression INTEGER NOT NULL DEFAULT 0,
    review_note TEXT,
    next_action VARCHAR(500),
    reviewed_by VARCHAR(255),
    reviewed_at TIMESTAMP,
    mini_test_score INTEGER,
    mini_test_passed BOOLEAN,
    mini_test_taken_at TIMESTAMP,
    mini_test_notes TEXT,
    certificate_url VARCHAR(600),
    certificate_uploaded_at TIMESTAMP,
    requested_at TIMESTAMP,
    admin_note TEXT,
    type VARCHAR(30) NOT NULL,
    statut VARCHAR(30) NOT NULL DEFAULT 'PROPOSEE'
);

CREATE INDEX IF NOT EXISTS idx_formations_user_id ON formations (user_id);
CREATE INDEX IF NOT EXISTS idx_formations_prediction_id ON formations (prediction_id);
CREATE INDEX IF NOT EXISTS idx_formations_statut ON formations (statut);



-- PCM and Personality Tests
CREATE TABLE IF NOT EXISTS pcm_results (
    id UUID PRIMARY KEY,
    profile_id UUID NOT NULL REFERENCES profiles (id),
    type_pcm VARCHAR(30),
    score_travail INTEGER,
    score_secondaire INTEGER,
    score_reactif INTEGER,
    score_rebelle INTEGER,
    date_evaluation TIMESTAMP NOT NULL DEFAULT NOW(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_pcm_results_profile_id ON pcm_results (profile_id);

CREATE TABLE IF NOT EXISTS tests_personnalite (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users (id),
    date_test TIMESTAMP NOT NULL DEFAULT NOW(),
    type_test VARCHAR(50),
    resultats TEXT,
    analyse_llm TEXT,
    score INTEGER
);

CREATE INDEX IF NOT EXISTS idx_tp_user_id ON tests_personnalite (user_id);

CREATE TABLE IF NOT EXISTS test_reponses (
    test_id UUID NOT NULL REFERENCES tests_personnalite (id),
    question_key VARCHAR(255) NOT NULL,
    reponse_value TEXT,
    PRIMARY KEY (test_id, question_key)
);

-- Audit and Security tokens
CREATE TABLE IF NOT EXISTS audit_logs (
    id UUID PRIMARY KEY,
    user_id UUID REFERENCES users (id),
    event_type VARCHAR(50) NOT NULL,
    email VARCHAR(255),
    ip_address VARCHAR(50),
    user_agent VARCHAR(500),
    device_id VARCHAR(255),
    location VARCHAR(255),
    details TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_audit_logs_user_id ON audit_logs (user_id);
CREATE INDEX IF NOT EXISTS idx_audit_logs_event_type ON audit_logs (event_type);
CREATE INDEX IF NOT EXISTS idx_audit_logs_created_at ON audit_logs (created_at);

CREATE TABLE IF NOT EXISTS password_reset_tokens (
    id UUID PRIMARY KEY,
    token VARCHAR(120) NOT NULL UNIQUE,
    user_id UUID NOT NULL REFERENCES users (id),
    expiry_date TIMESTAMP NOT NULL,
    used BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_prt_user_id ON password_reset_tokens (user_id);

CREATE TABLE IF NOT EXISTS token_blocklist (
    id UUID PRIMARY KEY,
    token_hash VARCHAR(255) NOT NULL UNIQUE,
    expires_at TIMESTAMP NOT NULL,
    reason VARCHAR(100),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_tb_token_hash ON token_blocklist (token_hash);
