-- 설계 초안입니다. 현재 backend에는 JPA/Flyway가 연결되어 있지 않아 자동 실행되지 않습니다.
-- PostgreSQL 및 사용자 인증 도입 시 Flyway migration으로 이동합니다.

CREATE TABLE medications (
    id UUID PRIMARY KEY,
    product_code VARCHAR(100) NOT NULL,
    product_name VARCHAR(300) NOT NULL,
    manufacturer VARCHAR(300),
    source_name VARCHAR(300) NOT NULL,
    source_record_id VARCHAR(200) NOT NULL,
    retrieved_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_medication_source UNIQUE (product_code, source_name)
);
CREATE INDEX idx_medications_product_name ON medications (product_name);
CREATE INDEX idx_medications_manufacturer ON medications (manufacturer);

CREATE TABLE medication_ingredients (
    id UUID PRIMARY KEY,
    medication_id UUID NOT NULL REFERENCES medications(id) ON DELETE CASCADE,
    provider_code VARCHAR(100),
    korean_name VARCHAR(300),
    english_name VARCHAR(300),
    normalized_name VARCHAR(300) NOT NULL,
    amount NUMERIC(20, 8),
    unit VARCHAR(30),
    salt_form VARCHAR(200),
    hydrate_form VARCHAR(200),
    source_record_id VARCHAR(200) NOT NULL,
    retrieved_at TIMESTAMPTZ NOT NULL
);
CREATE UNIQUE INDEX uq_medication_ingredient
    ON medication_ingredients (medication_id, provider_code, amount, unit)
    WHERE provider_code IS NOT NULL;
CREATE INDEX idx_medication_ingredient_code ON medication_ingredients (provider_code);
CREATE INDEX idx_medication_ingredient_name ON medication_ingredients (normalized_name);

CREATE TABLE prescription_drafts (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    raw_ocr_text TEXT,
    normalized_text TEXT,
    status VARCHAR(40) NOT NULL,
    selected_product_codes JSONB NOT NULL DEFAULT '[]',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_prescription_drafts_user_created ON prescription_drafts (user_id, created_at);
CREATE INDEX idx_prescription_drafts_status ON prescription_drafts (status);

CREATE TABLE drug_product_cache (
    id UUID PRIMARY KEY,
    cache_key VARCHAR(500) NOT NULL UNIQUE,
    normalized_query VARCHAR(500) NOT NULL,
    response_json JSONB NOT NULL,
    source_name VARCHAR(300) NOT NULL,
    retrieved_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    response_status VARCHAR(40) NOT NULL
);
CREATE INDEX idx_drug_product_cache_expiry ON drug_product_cache (expires_at);

CREATE TABLE ingredient_aliases (
    id UUID PRIMARY KEY,
    provider_code VARCHAR(100) NOT NULL,
    canonical_name VARCHAR(300) NOT NULL,
    alias VARCHAR(300) NOT NULL,
    language VARCHAR(20),
    alias_type VARCHAR(40),
    verified BOOLEAN NOT NULL DEFAULT FALSE,
    verified_by VARCHAR(200),
    verified_at TIMESTAMPTZ,
    CONSTRAINT uq_ingredient_alias UNIQUE (provider_code, alias)
);
CREATE INDEX idx_ingredient_alias_lower ON ingredient_aliases (LOWER(alias));

CREATE TABLE interaction_checks (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    left_medication_id UUID NOT NULL REFERENCES medications(id),
    right_medication_id UUID NOT NULL REFERENCES medications(id),
    status VARCHAR(40) NOT NULL,
    final_severity VARCHAR(40),
    coverage_percent INTEGER,
    error_code VARCHAR(100),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ
);
CREATE INDEX idx_interaction_checks_user_created ON interaction_checks (user_id, created_at);
CREATE INDEX idx_interaction_checks_status ON interaction_checks (status);

CREATE TABLE interaction_results (
    id UUID PRIMARY KEY,
    interaction_check_id UUID NOT NULL REFERENCES interaction_checks(id) ON DELETE CASCADE,
    severity VARCHAR(40) NOT NULL,
    result_type VARCHAR(60) NOT NULL,
    left_ingredient_code VARCHAR(100),
    right_ingredient_code VARCHAR(100),
    summary TEXT NOT NULL,
    source_type VARCHAR(60),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_interaction_result_check ON interaction_results (interaction_check_id);
CREATE INDEX idx_interaction_result_pair ON interaction_results (left_ingredient_code, right_ingredient_code);

CREATE TABLE interaction_evidence (
    id UUID PRIMARY KEY,
    interaction_result_id UUID NOT NULL REFERENCES interaction_results(id) ON DELETE CASCADE,
    source_name VARCHAR(300) NOT NULL,
    source_record_id VARCHAR(200),
    source_authority VARCHAR(200) NOT NULL,
    original_message TEXT,
    normalized_message TEXT,
    retrieved_at TIMESTAMPTZ NOT NULL,
    reviewed BOOLEAN NOT NULL DEFAULT FALSE,
    reviewed_by VARCHAR(200),
    reviewed_at TIMESTAMPTZ
);
CREATE INDEX idx_interaction_evidence_source ON interaction_evidence (source_name, source_record_id);

CREATE TABLE supplement_interaction_rules (
    id UUID PRIMARY KEY,
    drug_ingredient_code VARCHAR(100) NOT NULL,
    supplement_ingredient_code VARCHAR(100) NOT NULL,
    severity VARCHAR(40) NOT NULL,
    action TEXT,
    source_name VARCHAR(300) NOT NULL,
    source_record_id VARCHAR(200) NOT NULL,
    source_text TEXT NOT NULL,
    source_url TEXT,
    reviewed BOOLEAN NOT NULL DEFAULT FALSE,
    reviewed_by VARCHAR(200),
    reviewed_at TIMESTAMPTZ,
    valid_from DATE,
    valid_to DATE,
    CONSTRAINT uq_supplement_rule UNIQUE (
        drug_ingredient_code,
        supplement_ingredient_code,
        source_record_id
    )
);
CREATE INDEX idx_supplement_rule_pair
    ON supplement_interaction_rules (drug_ingredient_code, supplement_ingredient_code);
