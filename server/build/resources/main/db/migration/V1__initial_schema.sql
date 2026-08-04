CREATE TABLE users (
  id UUID PRIMARY KEY,
  display_name VARCHAR(100) NOT NULL,
  timezone VARCHAR(50) NOT NULL DEFAULT 'Asia/Seoul',
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE medications (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL REFERENCES users(id),
  name VARCHAR(255) NOT NULL,
  product_type VARCHAR(40) NOT NULL,
  product_code VARCHAR(100),
  manufacturer VARCHAR(255),
  active BOOLEAN NOT NULL DEFAULT TRUE,
  version BIGINT NOT NULL DEFAULT 0,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE medication_ingredients (
  id UUID PRIMARY KEY,
  medication_id UUID NOT NULL REFERENCES medications(id) ON DELETE CASCADE,
  display_name VARCHAR(255) NOT NULL,
  normalized_name VARCHAR(255) NOT NULL,
  provider_code VARCHAR(100),
  amount DECIMAL(12,3),
  unit VARCHAR(30)
);

CREATE TABLE medication_schedules (
  id UUID PRIMARY KEY,
  medication_id UUID NOT NULL REFERENCES medications(id) ON DELETE CASCADE,
  start_date DATE NOT NULL,
  end_date DATE,
  weekdays VARCHAR(30) NOT NULL,
  dose_time TIME NOT NULL,
  dose VARCHAR(80) NOT NULL,
  timing VARCHAR(40),
  active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE dose_logs (
  id UUID PRIMARY KEY,
  medication_id UUID NOT NULL REFERENCES medications(id),
  user_id UUID NOT NULL REFERENCES users(id),
  scheduled_at TIMESTAMP WITH TIME ZONE NOT NULL,
  status VARCHAR(30) NOT NULL,
  taken_at TIMESTAMP WITH TIME ZONE,
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT uq_dose UNIQUE (medication_id, scheduled_at)
);

CREATE TABLE prescription_drafts (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL REFERENCES users(id),
  status VARCHAR(40) NOT NULL,
  extracted_json TEXT,
  confirmed_medication_id UUID REFERENCES medications(id),
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE prescription_images (
  id UUID PRIMARY KEY,
  draft_id UUID NOT NULL REFERENCES prescription_drafts(id) ON DELETE CASCADE,
  side VARCHAR(10) NOT NULL,
  object_key VARCHAR(500) NOT NULL,
  checksum VARCHAR(64) NOT NULL,
  mime_type VARCHAR(100) NOT NULL
);

CREATE TABLE processing_jobs (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL REFERENCES users(id),
  job_type VARCHAR(50) NOT NULL,
  resource_id UUID NOT NULL,
  status VARCHAR(40) NOT NULL,
  attempt_count INTEGER NOT NULL DEFAULT 0,
  error_code VARCHAR(80),
  next_attempt_at TIMESTAMP WITH TIME ZONE,
  updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE interaction_checks (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL REFERENCES users(id),
  new_medication_id UUID NOT NULL REFERENCES medications(id),
  status VARCHAR(40) NOT NULL,
  saved BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE interaction_results (
  id UUID PRIMARY KEY,
  check_id UUID NOT NULL REFERENCES interaction_checks(id) ON DELETE CASCADE,
  compared_medication_id UUID NOT NULL REFERENCES medications(id),
  severity VARCHAR(40) NOT NULL,
  summary VARCHAR(1000) NOT NULL
);

CREATE TABLE interaction_evidence (
  id UUID PRIMARY KEY,
  result_id UUID NOT NULL REFERENCES interaction_results(id) ON DELETE CASCADE,
  ingredient_a VARCHAR(255),
  ingredient_b VARCHAR(255),
  evidence_type VARCHAR(100),
  source_name VARCHAR(255) NOT NULL,
  source_url VARCHAR(1000) NOT NULL,
  source_record_id VARCHAR(255),
  source_date DATE,
  retrieved_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE consultations (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL REFERENCES users(id),
  title VARCHAR(255) NOT NULL,
  hospital_name VARCHAR(255),
  consulted_at TIMESTAMP WITH TIME ZONE NOT NULL,
  duration_ms BIGINT NOT NULL,
  status VARCHAR(40) NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE audio_objects (
  id UUID PRIMARY KEY,
  consultation_id UUID NOT NULL REFERENCES consultations(id) ON DELETE CASCADE,
  object_key VARCHAR(500) NOT NULL,
  mime_type VARCHAR(100) NOT NULL,
  size_bytes BIGINT NOT NULL,
  checksum VARCHAR(64) NOT NULL
);

CREATE TABLE transcript_segments (
  id UUID PRIMARY KEY,
  consultation_id UUID NOT NULL REFERENCES consultations(id) ON DELETE CASCADE,
  speaker_label VARCHAR(50) NOT NULL,
  start_ms BIGINT NOT NULL,
  end_ms BIGINT NOT NULL,
  text VARCHAR(4000) NOT NULL
);

CREATE TABLE consultation_summaries (
  id UUID PRIMARY KEY,
  consultation_id UUID NOT NULL UNIQUE REFERENCES consultations(id) ON DELETE CASCADE,
  summary_json TEXT NOT NULL,
  status VARCHAR(40) NOT NULL
);

CREATE TABLE reminders (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL REFERENCES users(id),
  medication_id UUID NOT NULL REFERENCES medications(id),
  local_time TIME NOT NULL,
  weekdays VARCHAR(30) NOT NULL,
  enabled BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE chat_sessions (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL REFERENCES users(id),
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE chat_messages (
  id UUID PRIMARY KEY,
  session_id UUID NOT NULL REFERENCES chat_sessions(id) ON DELETE CASCADE,
  role VARCHAR(20) NOT NULL,
  content VARCHAR(8000) NOT NULL,
  token_usage INTEGER,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE external_api_snapshots (
  id UUID PRIMARY KEY,
  provider VARCHAR(80) NOT NULL,
  provider_record_id VARCHAR(255) NOT NULL,
  payload TEXT NOT NULL,
  retrieved_at TIMESTAMP WITH TIME ZONE NOT NULL,
  expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
  CONSTRAINT uq_snapshot UNIQUE (provider, provider_record_id)
);

CREATE INDEX idx_medications_owner ON medications(user_id, active);
CREATE INDEX idx_jobs_resource ON processing_jobs(resource_id, status);
CREATE INDEX idx_consultations_owner ON consultations(user_id, consulted_at);

