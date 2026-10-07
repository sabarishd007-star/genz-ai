package com.genzai.repository;

import com.genzai.model.Document;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentRepository extends JpaRepository<Document, Long> {
    
    List<Document> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<Document> findByUserIdAndSubjectOrderByCreatedAtDesc(Long userId, String subject);

    List<Document> findByUserIdAndFileNameContainingIgnoreCaseOrderByCreatedAtDesc(Long userId, String fileName);

    Optional<Document> findByIdAndUserId(Long id, Long userId);

    @Query("SELECT d FROM Document d WHERE d.userId = :userId " +
           "AND (:subject IS NULL OR d.subject = :subject) " +
           "AND (:query IS NULL OR LOWER(d.fileName) LIKE :query) " +
           "ORDER BY d.createdAt DESC")
    List<Document> searchUserDocuments(@Param("userId") Long userId, 
                                       @Param("subject") String subject, 
                                       @Param("query") String query);

    long countByUserId(Long userId);
}
