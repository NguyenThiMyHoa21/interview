package com.intervia.repository;

import com.intervia.model.SessionEventDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SessionEventRepository extends MongoRepository<SessionEventDocument, String> {
    List<SessionEventDocument> findAllBySessionIdOrderByCreatedAtAsc(String sessionId);
    void deleteBySessionId(String sessionId);
}
