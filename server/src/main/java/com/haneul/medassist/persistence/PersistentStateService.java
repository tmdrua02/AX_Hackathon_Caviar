package com.haneul.medassist.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** Small durable JSON record store used by the existing demo-domain API without changing its wire contract. */
@Service
public class PersistentStateService {
    private final PersistentStateRepository repository;
    private final ObjectMapper mapper;

    public PersistentStateService(PersistentStateRepository repository, ObjectMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public <T> Map<String, T> load(String recordType, Class<T> valueType) {
        Map<String, T> values = new LinkedHashMap<>();
        for (PersistentStateEntity entity : repository.findAllByRecordType(recordType)) {
            try {
                String key = entity.stateKey().substring(recordType.length() + 1);
                values.put(key, mapper.readValue(entity.payload(), valueType));
            } catch (Exception error) {
                throw new IllegalStateException("저장된 " + recordType + " 데이터를 읽을 수 없습니다.", error);
            }
        }
        return values;
    }

    @Transactional
    public void put(String recordType, String key, Object value) {
        try {
            repository.save(new PersistentStateEntity(stateKey(recordType, key), recordType,
                    mapper.writeValueAsString(value), Instant.now()));
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("데이터를 안전하게 저장하지 못했습니다.", error);
        }
    }

    @Transactional
    public void delete(String recordType, String key) {
        repository.deleteById(stateKey(recordType, key));
    }

    private String stateKey(String recordType, String key) {
        if (!recordType.matches("[a-z-]{1,80}") || key.isBlank() || key.length() > 120) {
            throw new IllegalArgumentException("invalid persistent state key");
        }
        return recordType + ":" + key;
    }
}
