package com.haneul.medassist.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "app_state")
public class PersistentStateEntity {
    @Id
    @Column(name = "state_key", nullable = false, length = 220)
    private String stateKey;

    @Column(name = "record_type", nullable = false, length = 80)
    private String recordType;

    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PersistentStateEntity() { }

    public PersistentStateEntity(String stateKey, String recordType, String payload, Instant updatedAt) {
        this.stateKey = stateKey;
        this.recordType = recordType;
        this.payload = payload;
        this.updatedAt = updatedAt;
    }

    public String stateKey() { return stateKey; }
    public String recordType() { return recordType; }
    public String payload() { return payload; }
}
