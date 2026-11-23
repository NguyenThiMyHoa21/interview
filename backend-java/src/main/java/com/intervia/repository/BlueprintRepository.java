package com.intervia.repository;

import com.intervia.model.BlueprintDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BlueprintRepository extends MongoRepository<BlueprintDocument, String> {
    Optional<BlueprintDocument> findBySessionIdAndUserId(String sessionId, String userId);
    Optional<BlueprintDocument> findFirstBySessionIdOrderByCreatedAtDesc(String sessionId);
    void deleteBySessionId(String sessionId);
}
