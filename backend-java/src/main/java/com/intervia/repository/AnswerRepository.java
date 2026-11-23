package com.intervia.repository;

import com.intervia.model.AnswerDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AnswerRepository extends MongoRepository<AnswerDocument, String> {
    List<AnswerDocument> findAllBySessionIdOrderBySeqAsc(String sessionId);
    Optional<AnswerDocument> findByIdAndUserId(String id, String userId);
    long countByUserId(String userId);
    void deleteBySessionId(String sessionId);
}
