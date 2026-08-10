CREATE TABLE app_state (
    state_key VARCHAR(220) PRIMARY KEY,
    record_type VARCHAR(80) NOT NULL,
    payload TEXT NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_app_state_record_type ON app_state (record_type);
