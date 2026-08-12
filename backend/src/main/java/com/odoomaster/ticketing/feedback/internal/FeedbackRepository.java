package com.odoomaster.ticketing.feedback.internal;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data JPA repository for the Feedback aggregate.
 */
public interface FeedbackRepository extends JpaRepository<Feedback, Long> {

  @Query("""
      SELECT f FROM Feedback f
      WHERE (:status IS NULL OR f.status = :status)
        AND (:category IS NULL OR f.category = :category)
      ORDER BY f.createdAt DESC
      """)
  Page<Feedback> findAllFiltered(
      @Param("status") FeedbackStatus status,
      @Param("category") FeedbackCategory category,
      Pageable pageable);

  long countByStatus(FeedbackStatus status);
}
