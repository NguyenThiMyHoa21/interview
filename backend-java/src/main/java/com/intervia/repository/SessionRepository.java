package com.intervia.repository;

import com.intervia.model.SessionDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SessionRepository extends MongoRepository<SessionDocument, String> {
    List<SessionDocument> findAllByUserIdOrderByCreatedAtDesc(String userId);
    List<SessionDocument> findAllByUserIdAndCvId(String userId, String cvId);
    List<SessionDocument> findAllByUserIdAndJdId(String userId, String jdId);
    Optional<SessionDocument> findByIdAndUserId(String id, String userId);
    long countByUserId(String userId);
    long countByUserIdAndStatus(String userId, String status);
}
