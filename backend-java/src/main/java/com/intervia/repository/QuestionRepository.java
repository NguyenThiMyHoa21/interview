package com.intervia.repository;

import com.intervia.model.QuestionDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface QuestionRepository extends MongoRepository<QuestionDocument, String> {
    List<QuestionDocument> findAllBySessionIdOrderBySeqAsc(String sessionId);
    Optional<QuestionDocument> findFirstBySessionIdOrderBySeqDesc(String sessionId);
    void deleteBySessionId(String sessionId);
}
