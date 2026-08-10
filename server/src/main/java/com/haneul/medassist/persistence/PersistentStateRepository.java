package com.haneul.medassist.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface PersistentStateRepository extends JpaRepository<PersistentStateEntity, String> {
    List<PersistentStateEntity> findAllByRecordType(String recordType);
}
