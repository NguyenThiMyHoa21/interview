package com.intervia.repository;

import com.intervia.model.SessionReportDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SessionReportRepository extends MongoRepository<SessionReportDocument, String> {
    Optional<SessionReportDocument> findBySessionIdAndUserId(String sessionId, String userId);
    List<SessionReportDocument> findAllByUserIdOrderByCreatedAtDesc(String userId);
    void deleteBySessionId(String sessionId);
}
