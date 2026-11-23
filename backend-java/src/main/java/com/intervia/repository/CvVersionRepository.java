package com.intervia.repository;

import com.intervia.model.CvVersionDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CvVersionRepository extends MongoRepository<CvVersionDocument, String> {
    List<CvVersionDocument> findAllByCvIdAndUserIdOrderByVersionDesc(String cvId, String userId);
    Optional<CvVersionDocument> findByCvIdAndUserIdAndVersion(String cvId, String userId, Integer version);
    Optional<CvVersionDocument> findFirstByCvIdOrderByVersionDesc(String cvId);
}
